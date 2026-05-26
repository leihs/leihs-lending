require "spec_helper"
require_relative "graphql_helper"

describe "orders" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }

  def create_order(state: "submitted", **attrs)
    order = create(:order, user: user, inventory_pool: pool, state: state, **attrs)
    create(:reservation,
      user: user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: state)
    order
  end

  it "returns orders for pool" do
    order = create_order
    result = query(<<~GQL, user.id)
      { orders(poolId: "#{pool.id}") { id state purpose } }
    GQL
    expect_graphql_result(result, {
      orders: [{id: order.id.to_s, state: "SUBMITTED", purpose: order.purpose}]
    })
  end

  it "filters by state" do
    create_order(state: "submitted")
    approved = create_order(state: "approved")
    result = query(<<~GQL, user.id)
      { orders(poolId: "#{pool.id}", states: [APPROVED]) { id } }
    GQL
    expect_graphql_result(result, {orders: [{id: approved.id.to_s}]})
  end

  it "paginates" do
    3.times { create_order }
    result = query(<<~GQL, user.id)
      { orders(poolId: "#{pool.id}", page: 1, perPage: 2) { id } }
    GQL
    expect(result[:errors]).to be_nil
    expect(result.dig(:data, :orders).size).to eq(2)
  end

  it "returns reservations with model" do
    order = create_order
    reservation = Reservation.where(order_id: order.id).first
    result = query(<<~GQL, user.id)
      { orders(poolId: "#{pool.id}") { reservations { id model { id name } } } }
    GQL
    expect_graphql_result(result, {
      orders: [{
        reservations: [{
          id: reservation.id.to_s,
          model: {id: model.id.to_s, name: model.product}
        }]
      }]
    })
  end

  it "returns user on order" do
    create_order
    result = query(<<~GQL, user.id)
      { orders(poolId: "#{pool.id}") { user { id email } } }
    GQL
    expect_graphql_result(result, {
      orders: [{user: {id: user.id.to_s, email: user.email}}]
    })
  end
end
