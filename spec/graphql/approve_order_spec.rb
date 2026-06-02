require "spec_helper"
require_relative "graphql_helper"

describe "approveOrder" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let!(:item) { create(:item, leihs_model: model, inventory_pool: pool, owner: pool) }

  def create_order(state: "submitted")
    order = create(:order, user: user, inventory_pool: pool, state: state)
    create(:reservation,
      user: user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: state)
    order
  end

  def approve_order(order_id, user_id, force: nil)
    force_arg = force.nil? ? "" : ", force: #{force}"
    query(<<~GQL, user_id)
      mutation {
        approveOrder(id: "#{order_id}"#{force_arg}) {
          id state
        }
      }
    GQL
  end

  it "approves a submitted order" do
    order = create_order
    result = approve_order(order.id, user.id)
    expect_graphql_result(result, {
      approveOrder: {
        id: order.id.to_s,
        state: "APPROVED"
      }
    })
  end

  it "updates reservations to approved" do
    order = create_order
    approve_order(order.id, user.id)
    statuses = Reservation.where(order_id: order.id).map(&:status)
    expect(statuses).to all(eq("approved"))
  end

  it "fails when order is not in submitted state" do
    order = create_order(state: "approved")
    result = approve_order(order.id, user.id)
    expect_graphql_error(result, status: 422)
  end

  context "when all reservations are in the past" do
    def create_expired_order
      order = create(:order, user: user, inventory_pool: pool, state: "submitted")
      create(:reservation,
        user: user,
        inventory_pool: pool,
        leihs_model: model,
        order: order,
        status: "submitted",
        start_date: (Date.today - 3).to_s,
        end_date: (Date.today - 1).to_s)
      order
    end

    it "fails even with force: true" do
      order = create_expired_order
      result = approve_order(order.id, user.id, force: true)
      expect_graphql_error(result, status: 422)
    end
  end

  context "with a suspended user" do
    before { create(:suspension, user: user, inventory_pool: pool) }

    it "fails when order user is suspended" do
      order = create_order
      result = approve_order(order.id, user.id)
      expect_graphql_error(result, status: 422)
    end

    it "approves when force: true" do
      order = create_order
      result = approve_order(order.id, user.id, force: true)
      expect_graphql_result(result, {approveOrder: {id: order.id.to_s, state: "APPROVED"}})
    end
  end

  context "with unavailable reservations" do
    let(:other_user) { create(:user) }

    before do
      other_order = create(:order, user: other_user, inventory_pool: pool, state: "submitted")
      create(:reservation,
        user: other_user,
        inventory_pool: pool,
        leihs_model: model,
        order: other_order,
        status: "submitted")
    end

    it "fails when reservations are not available" do
      order = create_order
      result = approve_order(order.id, user.id)
      expect_graphql_error(result, status: 422)
    end

    it "approves when force: true" do
      order = create_order
      result = approve_order(order.id, user.id, force: true)
      expect_graphql_result(result, {approveOrder: {id: order.id.to_s, state: "APPROVED"}})
    end
  end

  context "when pool is closed on reservation dates" do
    before { create(:holiday, inventory_pool: pool) }

    it "fails when pool is closed on reservation dates" do
      order = create_order
      result = approve_order(order.id, user.id)
      expect_graphql_error(result, status: 422)
    end

    it "approves when force: true" do
      order = create_order
      result = approve_order(order.id, user.id, force: true)
      expect_graphql_result(result, {approveOrder: {id: order.id.to_s, state: "APPROVED"}})
    end
  end

  context "email notification" do
    def copy_approved_template_to_pool(pool_id)
      tmpl = MailTemplate.where(name: "approved", language_locale: "en-GB", inventory_pool_id: nil).first
      database[:mail_templates].insert(
        id: SecureRandom.uuid,
        inventory_pool_id: pool_id,
        name: tmpl[:name],
        format: tmpl[:format],
        language_locale: tmpl[:language_locale],
        subject: tmpl[:subject],
        body: tmpl[:body],
        is_template_template: false,
        type: tmpl[:type],
        created_at: Time.now,
        updated_at: Time.now
      )
    end

    before { copy_approved_template_to_pool(pool.id) }

    it "inserts an email row on approval" do
      order = create_order
      approve_order(order.id, user.id)
      email = Email.where(user_id: user.id).first
      expect(email).not_to be_nil
      expect(email[:subject]).to eq("[leihs] Reservation Confirmation")
    end

    it "includes the comment in the email body when provided" do
      order = create_order
      query(<<~GQL, user.id)
        mutation { approveOrder(id: "#{order.id}", comment: "Pick up at desk B") { id } }
      GQL
      email = Email.where(user_id: user.id).first
      expect(email[:body]).to include("Pick up at desk B")
    end

    it "still approves if email fails (no template)" do
      MailTemplate.where(inventory_pool_id: pool.id).delete
      order = create_order
      result = approve_order(order.id, user.id)
      expect_graphql_result(result, {approveOrder: {id: order.id.to_s, state: "APPROVED"}})
      expect(Email.where(user_id: user.id).count).to eq(0)
    end
  end

  context "with a suspended delegated user" do
    let(:delegated_user) { create(:user) }
    before { create(:suspension, user: delegated_user, inventory_pool: pool) }

    def create_order_with_delegated_user
      order = create(:order, user: user, inventory_pool: pool, state: "submitted")
      create(:reservation,
        user: user,
        inventory_pool: pool,
        leihs_model: model,
        order: order,
        status: "submitted",
        delegated_user: delegated_user)
      order
    end

    it "fails when delegated user is suspended" do
      order = create_order_with_delegated_user
      result = approve_order(order.id, user.id)
      expect_graphql_error(result, status: 422)
    end

    it "approves when force: true" do
      order = create_order_with_delegated_user
      result = approve_order(order.id, user.id, force: true)
      expect_graphql_result(result, {approveOrder: {id: order.id.to_s, state: "APPROVED"}})
    end
  end
end
