require "spec_helper"
require_relative "graphql_helper"

describe "orders" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }

  before { grant_pool_access(user, pool) }

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
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { orders(poolId: "#{pool.id}") { items { id state purpose } totalCount } }
    GQL
    expect_graphql_result(result, {
      orders: {
        items: [{id: order.id.to_s, state: "SUBMITTED", purpose: order.purpose}],
        totalCount: 1
      }
    })
  end

  it "filters by state" do
    create_order(state: "submitted")
    approved = create_order(state: "approved")
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { orders(poolId: "#{pool.id}", states: [APPROVED]) { items { id } totalCount } }
    GQL
    expect_graphql_result(result, {orders: {items: [{id: approved.id.to_s}], totalCount: 1}})
  end

  it "paginates" do
    3.times { create_order }
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { orders(poolId: "#{pool.id}", page: 1, perPage: 2) { items { id } totalCount } }
    GQL
    expect(result[:errors]).to be_nil
    expect(result.dig(:data, :orders, :items).size).to eq(2)
    expect(result.dig(:data, :orders, :totalCount)).to eq(3)
  end

  it "returns reservations with model and quantity" do
    order = create_order
    reservation = Reservation.where(order_id: order.id).first
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { orders(poolId: "#{pool.id}") { items { reservations { id quantity model { id name } } } totalCount } }
    GQL
    expect_graphql_result(result, {
      orders: {
        items: [{
          reservations: [{
            id: reservation.id.to_s,
            quantity: reservation.quantity,
            model: {id: model.id.to_s, name: model.product}
          }]
        }],
        totalCount: 1
      }
    })
  end

  it "returns reservations with option" do
    option = create(:option, inventory_pool: pool)
    order = create(:order, user: user, inventory_pool: pool, state: "submitted")
    create(:reservation,
      user: user,
      inventory_pool: pool,
      leihs_model: nil,
      option_id: option.id,
      order: order,
      status: "submitted")
    reservation = Reservation.where(order_id: order.id).first
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { orders(poolId: "#{pool.id}") { items { reservations { id option { id name } } } totalCount } }
    GQL
    expect_graphql_result(result, {
      orders: {
        items: [{
          reservations: [{
            id: reservation.id.to_s,
            option: {id: option.id.to_s, name: option.product}
          }]
        }],
        totalCount: 1
      }
    })
  end

  it "returns user on order" do
    create_order
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { orders(poolId: "#{pool.id}") { items { user { id email } } totalCount } }
    GQL
    expect_graphql_result(result, {
      orders: {items: [{user: {id: user.id.to_s, email: user.email}}], totalCount: 1}
    })
  end
end
