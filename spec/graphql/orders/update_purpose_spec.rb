require "spec_helper"
require_relative "../graphql_helper"

describe "updateOrderPurpose" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }

  before { grant_pool_access(user, pool) }

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

  def update_order_purpose(order_id, purpose, user_id)
    query(<<~GQL, user_id, pool_id: pool.id)
      mutation {
        updateOrderPurpose(id: "#{order_id}", purpose: "#{purpose}") {
          id purpose state
        }
      }
    GQL
  end

  it "updates the purpose of a submitted order" do
    order = create_order
    result = update_order_purpose(order.id, "Field trip", user.id)
    expect_graphql_result(result, {
      updateOrderPurpose: {
        id: order.id.to_s,
        purpose: "Field trip",
        state: "SUBMITTED"
      }
    })
  end

  it "persists the updated purpose" do
    order = create_order
    update_order_purpose(order.id, "Field trip", user.id)
    expect(Order.where(id: order.id).first.purpose).to eq("Field trip")
  end

  it "fails when purpose is blank" do
    order = create_order
    result = update_order_purpose(order.id, "", user.id)
    expect_graphql_error(result, status: 422)
  end

  it "fails when purpose is missing" do
    order = create_order
    result = query(<<~GQL, user.id, pool_id: pool.id)
      mutation { updateOrderPurpose(id: "#{order.id}") { id } }
    GQL
    expect_graphql_error(result)
  end

  it "fails when order is not in submitted state" do
    order = create_order(state: "approved")
    result = update_order_purpose(order.id, "Field trip", user.id)
    expect_graphql_error(result, status: 422)
  end
end
