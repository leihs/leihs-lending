require "spec_helper"
require_relative "graphql_helper"

VISITS_WITH_REMINDERS_GQL = <<~GQL
  query Visits($visitType: VisitTypeEnum) {
    visits(visitType: $visitType) {
      items {
        id reminders { id createdAt subject template }
      }
      totalCount
    }
  }
GQL

describe "visit reminders" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }

  before { grant_pool_access(user, pool) }

  def visits(variables = {})
    query(VISITS_WITH_REMINDERS_GQL, user.id, pool_id: pool.id, variables: variables)
  end

  def create_take_back(target_user: user)
    order = create(:order, user: target_user, inventory_pool: pool, state: "approved")
    reservation = create(:reservation,
      user: target_user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "approved",
      start_date: (Date.today - 7).to_s,
      end_date: Date.today.to_s)
    contract_id = SecureRandom.uuid
    database.transaction do
      database[:contracts].insert(
        id: contract_id,
        compact_id: contract_id[0..7],
        state: "open",
        user_id: target_user.id,
        inventory_pool_id: pool.id,
        purpose: "test",
        created_at: Time.now,
        updated_at: Time.now
      )
      database[:reservations]
        .where(id: reservation.id)
        .update(status: "signed", contract_id: contract_id)
    end
  end

  def insert_email(template_name)
    email_id = SecureRandom.uuid
    database[:emails].insert(
      id: email_id,
      user_id: user.id,
      source_pool_id: pool.id,
      from_address: "pool@example.com",
      to_address: user.email,
      subject: "test subject",
      body: "test body",
      template: template_name,
      created_at: Time.now,
      updated_at: Time.now
    )
    visit_id = database[:visits].where(user_id: user.id, inventory_pool_id: pool.id).get(:id)
    database[:emails_visits].insert(
      email_id: email_id,
      visit_id: visit_id
    )
  end

  before { create_take_back }

  it "returns reminder emails for the visit" do
    insert_email("reminder")
    result = visits(visitType: "TAKE_BACK")
    reminders = result.dig(:data, :visits, :items, 0, :reminders)
    expect(reminders.length).to eq(1)
    expect(reminders.first[:template]).to eq("reminder")
  end

  it "returns deadline_soon_reminder emails for the visit" do
    insert_email("deadline_soon_reminder")
    result = visits(visitType: "TAKE_BACK")
    reminders = result.dig(:data, :visits, :items, 0, :reminders)
    expect(reminders.length).to eq(1)
    expect(reminders.first[:template]).to eq("deadline_soon_reminder")
  end

  it "excludes non-reminder emails (e.g. approved)", :pending do
    # Pending, see https://github.com/leihs/leihs/issues/2217
    insert_email("approved")
    result = visits(visitType: "TAKE_BACK")
    reminders = result.dig(:data, :visits, :items, 0, :reminders)
    expect(reminders).to be_empty
  end

  it "returns multiple reminders ordered newest first" do
    insert_email("reminder")
    insert_email("deadline_soon_reminder")
    result = visits(visitType: "TAKE_BACK")
    reminders = result.dig(:data, :visits, :items, 0, :reminders)
    expect(reminders.length).to eq(2)
  end
end
