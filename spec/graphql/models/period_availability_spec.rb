require "spec_helper"
require_relative "../graphql_helper"

describe "Model.periodAvailability" do
  let(:user) { create(:user) }
  let(:customer) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let(:today) { Time.now.utc.to_date }
  let(:start_date) { today + 7 }
  let(:end_date) { today + 9 }

  before { grant_pool_access(user, pool) }

  def create_items(count)
    Array.new(count) { create(:item, leihs_model: model, inventory_pool: pool) }
  end

  def add_reservation(from:, to:)
    other_user = create(:user)
    create(:reservation,
      user: other_user,
      inventory_pool: pool,
      leihs_model: model,
      order: create(:order, user: other_user, inventory_pool: pool, state: "submitted"),
      start_date: from.to_s,
      end_date: to.to_s)
  end

  def period_availability_result(from: start_date, to: end_date)
    query(<<~GQL, user.id, pool_id: pool.id)
      { model(id: "#{model.id}") {
          periodAvailability(startDate: "#{from}", endDate: "#{to}", userId: "#{customer.id}") {
            availableQuantity totalAvailableQuantity } } }
    GQL
  end

  def period_availability(**opts)
    result = period_availability_result(**opts)
    expect(result[:errors]).to be_nil
    result.dig(:data, :model, :periodAvailability)
  end

  it "returns the lowest quantities over the period" do
    create_items(2)
    add_reservation(from: start_date + 1, to: start_date + 1)

    expect(period_availability).to eq(availableQuantity: 1, totalAvailableQuantity: 1)
  end

  it "shows negative quantities when overbooked" do
    create_items(1)
    2.times { add_reservation(from: start_date, to: end_date) }

    expect(period_availability).to eq(availableQuantity: -1, totalAvailableQuantity: -1)
  end

  it "total includes groups the user is not member of" do
    create_items(2)
    group = create(:entitlement_group, inventory_pool: pool)
    create(:entitlement, leihs_model: model, entitlement_group: group, quantity: 1)

    expect(period_availability).to eq(availableQuantity: 1, totalAvailableQuantity: 2)
  end

  it "fails when endDate is before startDate" do
    result = period_availability_result(from: end_date, to: start_date)
    expect(result[:errors].first[:message]).to eq("endDate must not be before startDate")
  end
end
