require "spec_helper"
require_relative "../graphql_helper"

describe "deleteReservation" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }

  before { grant_pool_access(user, pool) }

  def create_order(state: "submitted")
    create(:order, user: user, inventory_pool: pool, state: state)
  end

  def create_open_reservation(order: nil, state: "submitted")
    create(:reservation,
      user: user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: state)
  end

  def delete_reservation(reservation_id, requester_id, order_id: nil)
    order_arg = order_id ? %(, orderId: "#{order_id}") : ""
    query(<<~GQL, requester_id, pool_id: pool.id)
      mutation {
        deleteReservation(id: "#{reservation_id}"#{order_arg})
      }
    GQL
  end

  it "deletes an open reservation from a submitted order" do
    order = create_order
    create_open_reservation(order: order)
    doomed = create_open_reservation(order: order)

    result = delete_reservation(doomed.id, user.id, order_id: order.id)
    expect_graphql_result(result, {deleteReservation: doomed.id.to_s})
    expect(Reservation.where(id: doomed.id).first).to be_nil
  end

  it "deletes a reservation without an order" do
    reservation = create_open_reservation(state: "approved")

    result = delete_reservation(reservation.id, user.id)
    expect_graphql_result(result, {deleteReservation: reservation.id.to_s})
    expect(Reservation.where(id: reservation.id).first).to be_nil
  end

  it "fails when it would remove the last open reservation on the order" do
    order = create_order
    only = create_open_reservation(order: order)

    result = delete_reservation(only.id, user.id, order_id: order.id)
    expect_graphql_error(result, status: 422)
    expect(Reservation.where(id: only.id).first).not_to be_nil
  end

  it "fails when the given order is not in submitted state" do
    order = create_order(state: "approved")
    create_open_reservation(order: order, state: "approved")
    doomed = create_open_reservation(order: order, state: "approved")

    result = delete_reservation(doomed.id, user.id, order_id: order.id)
    expect_graphql_error(result, status: 422)
  end
end
