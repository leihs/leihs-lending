require "spec_helper"
require_relative "graphql_helper"

VISITS_GQL = <<~GQL
  query Visits($visitType: VisitTypeEnum, $date: Date, $term: String,
               $verification: VerificationEnum, $page: Int, $perPage: Int) {
    visits(visitType: $visitType, date: $date, term: $term,
           verification: $verification, page: $page, perPage: $perPage) {
      items {
        id visitType date isOverdue quantity
        withUserToVerify withUserAndModelToVerify
        projectTitle comment
        user { id firstname lastname }
      }
      totalCount
    }
  }
GQL

describe "visits" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let(:next_monday) { Date.today.next_occurring(:monday) }

  before { grant_pool_access(user, pool) }

  def visits(variables = {})
    query(VISITS_GQL, user.id, pool_id: pool.id, variables: variables)
  end

  def create_hand_over(target_user: user, status: "submitted", start_date: next_monday)
    order = create(:order, user: target_user, inventory_pool: pool)
    create(:reservation,
      user: target_user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: status,
      start_date: start_date.to_s,
      end_date: (next_monday + 7).to_s)
    order
  end

  def create_take_back(target_user: user, end_date: next_monday)
    order = create(:order, user: target_user, inventory_pool: pool, state: "approved")
    reservation = create(:reservation,
      user: target_user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "approved",
      start_date: (Date.today - 7).to_s,
      end_date: end_date.to_s)
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
    order
  end

  it "returns visits for the pool" do
    create_hand_over
    create_take_back
    result = visits
    expect(result[:errors]).to be_nil
    types = result.dig(:data, :visits, :items).map { |v| v[:visitType] }
    expect(types).to contain_exactly("HAND_OVER", "TAKE_BACK")
  end

  it "does not return visits from other pools" do
    other_pool = create(:inventory_pool)
    other_user = create(:user)
    other_order = create(:order, user: other_user, inventory_pool: other_pool)
    create(:reservation,
      user: other_user,
      inventory_pool: other_pool,
      leihs_model: model,
      order: other_order,
      status: "submitted",
      start_date: next_monday.to_s,
      end_date: (next_monday + 7).to_s)
    expect_graphql_result(visits, {visits: {items: [], totalCount: 0}})
  end

  context "visitType filter" do
    before do
      create_hand_over
      create_take_back
    end

    it "HAND_OVER" do
      result = visits(visitType: "HAND_OVER")
      expect(result[:errors]).to be_nil
      expect(result.dig(:data, :visits, :items).map { |v| v[:visitType] }).to all(eq("HAND_OVER"))
    end

    it "TAKE_BACK" do
      result = visits(visitType: "TAKE_BACK")
      expect(result[:errors]).to be_nil
      expect(result.dig(:data, :visits, :items).map { |v| v[:visitType] }).to all(eq("TAKE_BACK"))
    end
  end

  it "filters by date" do
    other_user = create(:user).tap { |u| grant_pool_access(u, pool) }
    create_hand_over(start_date: next_monday)
    create_hand_over(target_user: other_user, start_date: next_monday + 1)
    result = visits(date: next_monday.to_s)
    expect(result[:errors]).to be_nil
    expect(result.dig(:data, :visits, :items).map { |v| v[:date] }).to all(eq(next_monday.to_s))
  end

  it "filters by term" do
    other_user = create(:user, firstname: "Zoltan", lastname: "Uniquesson").tap { |u| grant_pool_access(u, pool) }
    create_hand_over
    create_hand_over(target_user: other_user)
    result = visits(term: "Zoltan")
    expect(result[:errors]).to be_nil
    expect(result.dig(:data, :visits, :items).map { |v| v.dig(:user, :firstname) }).to all(eq("Zoltan"))
  end

  context "isOverdue" do
    it "is true when visit date is in the past" do
      create_hand_over(start_date: Date.today.prev_occurring(:monday))
      result = visits
      expect(result.dig(:data, :visits, :items, 0, :isOverdue)).to be true
    end

    it "is false when visit date is in the future" do
      create_hand_over
      result = visits
      expect(result.dig(:data, :visits, :items, 0, :isOverdue)).to be false
    end
  end

  it "returns projectTitle and comment" do
    order = create_hand_over
    visit = visits.dig(:data, :visits, :items, 0)
    expect(visit[:projectTitle]).to eq(order.customer_order.title)
    expect(visit[:comment]).to eq(order.purpose)
  end

  it "returns user" do
    create_hand_over
    visit = visits.dig(:data, :visits, :items, 0)
    expect(visit.dig(:user, :id)).to eq(user.id.to_s)
    expect(visit.dig(:user, :firstname)).to eq(user.firstname)
  end

  it "paginates" do
    3.times.each_with_index do |_, i|
      u = create(:user).tap { |u| grant_pool_access(u, pool) }
      create_hand_over(target_user: u, start_date: next_monday + i)
    end
    expect(visits(perPage: 2).dig(:data, :visits, :items).size).to eq(2)
    expect(visits(page: 2, perPage: 2).dig(:data, :visits, :items).size).to eq(1)
    expect(visits(perPage: 2).dig(:data, :visits, :totalCount)).to eq(3)
  end

  context "verification filter" do
    def setup_verification_group(with_model: false)
      group_id = SecureRandom.uuid
      database[:entitlement_groups].insert(
        id: group_id,
        inventory_pool_id: pool.id,
        name: "verify-#{group_id[0..7]}",
        is_verification_required: true,
        created_at: Time.now,
        updated_at: Time.now
      )
      database[:entitlement_groups_users].insert(
        entitlement_group_id: group_id,
        user_id: user.id
      )
      if with_model
        database[:entitlements].insert(
          id: SecureRandom.uuid,
          entitlement_group_id: group_id,
          model_id: model.id,
          quantity: 1
        )
      end
    end

    it "NONE_REQUIRED returns visits without verification" do
      create_hand_over
      result = visits(verification: "NONE_REQUIRED")
      expect(result[:errors]).to be_nil
      expect(result.dig(:data, :visits, :items).size).to eq(1)
      expect(result.dig(:data, :visits, :items, 0, :withUserToVerify)).to be false
    end

    it "USER returns visits where user verification required" do
      create_hand_over
      setup_verification_group
      result = visits(verification: "USER")
      expect(result[:errors]).to be_nil
      expect(result.dig(:data, :visits, :items).size).to eq(1)
      expect(result.dig(:data, :visits, :items, 0, :withUserToVerify)).to be true
      expect(result.dig(:data, :visits, :items, 0, :withUserAndModelToVerify)).to be false
    end

    it "USER_AND_MODEL returns visits where user and model verification required" do
      create_hand_over
      setup_verification_group(with_model: true)
      result = visits(verification: "USER_AND_MODEL")
      expect(result[:errors]).to be_nil
      expect(result.dig(:data, :visits, :items).size).to eq(1)
      expect(result.dig(:data, :visits, :items, 0, :withUserAndModelToVerify)).to be true
    end
  end
end
