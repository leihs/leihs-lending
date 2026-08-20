require "spec_helper"
require_relative "../graphql_helper"

describe "swapOrderUser" do
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

  def swap_order_user(order_id, new_user_id, requester_id, delegated_user_id: nil)
    delegated_arg = delegated_user_id ? %(, delegatedUserId: "#{delegated_user_id}") : ""
    query(<<~GQL, requester_id, pool_id: pool.id)
      mutation {
        swapOrderUser(id: "#{order_id}", userId: "#{new_user_id}"#{delegated_arg}) {
          id state
        }
      }
    GQL
  end

  it "swaps the orderer and updates open reservations" do
    order = create_order
    new_user = create(:user)
    grant_pool_access(new_user, pool)

    result = swap_order_user(order.id, new_user.id, user.id)
    expect_graphql_result(result, {
      swapOrderUser: {id: order.id.to_s, state: "SUBMITTED"}
    })
    expect(Order[order.id].user_id).to eq(new_user.id)
    reservation_user_ids = Reservation.where(order_id: order.id).map(&:user_id)
    expect(reservation_user_ids).to all(eq(new_user.id))
  end

  it "sets delegatedUserId on open reservations when given" do
    order = create_order
    new_user = create(:user)
    grant_pool_access(new_user, pool)
    delegate = create(:user)

    swap_order_user(order.id, new_user.id, user.id, delegated_user_id: delegate.id)
    delegated_ids = Reservation.where(order_id: order.id).map(&:delegated_user_id)
    expect(delegated_ids).to all(eq(delegate.id))
  end

  it "updates the customer_order in place when it has no other orders" do
    order = create_order
    customer_order_id = order.customer_order_id
    new_user = create(:user)
    grant_pool_access(new_user, pool)

    swap_order_user(order.id, new_user.id, user.id)
    expect(Order[order.id].customer_order_id).to eq(customer_order_id)
    expect(CustomerOrder[customer_order_id].user_id).to eq(new_user.id)
  end

  it "forks a new customer_order when it is shared with other orders" do
    order = create_order
    shared_customer_order = order.customer_order
    other_order = create(:order, user: user, inventory_pool: pool,
      customer_order: shared_customer_order, state: "submitted")
    new_user = create(:user)
    grant_pool_access(new_user, pool)

    swap_order_user(order.id, new_user.id, user.id)

    expect(Order[order.id].customer_order_id).not_to eq(shared_customer_order.id)
    expect(Order[order.id].user_id).to eq(new_user.id)
    expect(Order[other_order.id].user_id).to eq(user.id)
    expect(CustomerOrder[shared_customer_order.id].user_id).to eq(user.id)
  end

  it "fails when the new user is deactivated" do
    order = create_order
    new_user = create(:user, account_enabled: false)
    grant_pool_access(new_user, pool)

    result = swap_order_user(order.id, new_user.id, user.id)
    expect_graphql_error(result, status: 422)
  end

  it "fails when the new user has no access to the pool" do
    order = create_order
    new_user = create(:user)

    result = swap_order_user(order.id, new_user.id, user.id)
    expect_graphql_error(result, status: 422)
  end

  it "fails when the order is not in submitted state" do
    order = create_order(state: "approved")
    new_user = create(:user)
    grant_pool_access(new_user, pool)

    result = swap_order_user(order.id, new_user.id, user.id)
    expect_graphql_error(result, status: 422)
  end
end
