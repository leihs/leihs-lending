require "spec_helper"
require_relative "../graphql_helper"

describe "createReservation" do
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

  def create_reservation(model_id, start_date, end_date, requester_id, order_id: nil, user_id: nil)
    order_arg = order_id ? %(orderId: "#{order_id}") : ""
    query(<<~GQL, requester_id, pool_id: pool.id)
      mutation {
        createReservation(
          #{order_arg}
          userId: "#{user_id || requester_id}"
          modelId: "#{model_id}"
          startDate: "#{start_date}"
          endDate: "#{end_date}"
        ) {
          id startDate endDate
        }
      }
    GQL
  end

  it "adds a reservation with quantity 1 to a submitted order" do
    order = create_order
    new_model = create(:leihs_model)
    start_date = Date.today.next_occurring(:monday)
    end_date = start_date + 4

    result = create_reservation(new_model.id, start_date, end_date, user.id, order_id: order.id)
    reservation_id = result.dig(:data, :createReservation, :id)
    expect_graphql_result(result, {
      createReservation: {
        id: reservation_id,
        startDate: start_date.to_s,
        endDate: end_date.to_s
      }
    })

    reservation = Reservation.where(order_id: order.id, model_id: new_model.id).first
    expect(reservation).not_to be_nil
    expect(reservation.id.to_s).to eq(reservation_id)
    expect(reservation.quantity).to eq(1)
    expect(reservation.status).to eq("submitted")
    expect(reservation.user_id).to eq(user.id)
    expect(reservation.inventory_pool_id).to eq(pool.id)
    expect(reservation.start_date).to eq(start_date)
    expect(reservation.end_date).to eq(end_date)
  end

  it "creates a reservation without an order" do
    start_date = Date.today.next_occurring(:monday)
    end_date = start_date + 4

    result = create_reservation(model.id, start_date, end_date, user.id)
    expect(result[:errors]).to be_nil

    reservation_id = result.dig(:data, :createReservation, :id)
    reservation = Reservation.where(id: reservation_id).first
    expect(reservation.order_id).to be_nil
    expect(reservation.inventory_pool_id).to eq(pool.id)
    expect(reservation.user_id).to eq(user.id)
  end

  it "fails when the given order is not in submitted state" do
    order = create_order(state: "approved")
    start_date = Date.today.next_occurring(:monday)
    end_date = start_date + 4

    result = create_reservation(model.id, start_date, end_date, user.id, order_id: order.id)
    expect_graphql_error(result, status: 422)
  end
end
