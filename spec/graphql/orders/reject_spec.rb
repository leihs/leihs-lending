require "spec_helper"
require_relative "../graphql_helper"

describe "rejectOrder" do
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

  def reject_order(order_id, reason, user_id)
    query(<<~GQL, user_id, pool_id: pool.id)
      mutation {
        rejectOrder(id: "#{order_id}", reason: "#{reason}") {
          id state rejectReason
        }
      }
    GQL
  end

  it "rejects a submitted order" do
    order = create_order
    result = reject_order(order.id, "No stock available", user.id)
    expect_graphql_result(result, {
      rejectOrder: {
        id: order.id.to_s,
        state: "REJECTED",
        rejectReason: "No stock available"
      }
    })
  end

  it "updates reservations to rejected" do
    order = create_order
    reject_order(order.id, "No stock available", user.id)
    statuses = Reservation.where(order_id: order.id).map(&:status)
    expect(statuses).to all(eq("rejected"))
  end

  it "fails when reason is blank" do
    order = create_order
    result = reject_order(order.id, "", user.id)
    expect_graphql_error(result, status: 422)
  end

  it "fails when reason is missing" do
    order = create_order
    result = query(<<~GQL, user.id, pool_id: pool.id)
      mutation { rejectOrder(id: "#{order.id}") { id } }
    GQL
    expect_graphql_error(result)
  end

  it "fails when order is not in submitted state" do
    order = create_order(state: "approved")
    result = reject_order(order.id, "No stock available", user.id)
    expect_graphql_error(result, status: 422)
  end
end
