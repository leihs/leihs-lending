require "spec_helper"
require_relative "../graphql_helper"

describe "deleteReservations" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }

  before { grant_pool_access(user, pool) }

  def create_order(state: "submitted")
    create(:order, user: user, inventory_pool: pool, state: state)
  end

  def create_open_reservation(order: nil, state: "submitted", inventory_pool: pool)
    create(:reservation,
      user: user,
      inventory_pool: inventory_pool,
      leihs_model: model,
      order: order,
      status: state)
  end

  def delete_reservations(ids)
    ids_arg = ids.map { |id| %("#{id}") }.join(", ")
    query(<<~GQL, user.id, pool_id: pool.id)
      mutation {
        deleteReservations(ids: [#{ids_arg}])
      }
    GQL
  end

  def exists?(reservation)
    !Reservation.where(id: reservation.id).first.nil?
  end

  it "deletes open reservations from a submitted order" do
    order = create_order
    create_open_reservation(order: order)
    doomed = [create_open_reservation(order: order), create_open_reservation(order: order)]

    result = delete_reservations(doomed.map(&:id))
    expect(result[:errors]).to be_nil
    expect(result.dig(:data, :deleteReservations)).to match_array(doomed.map { |r| r.id.to_s })
    expect(doomed.none? { |r| exists?(r) }).to be true
  end

  it "deletes a single reservation without an order" do
    reservation = create_open_reservation(state: "approved")

    result = delete_reservations([reservation.id])
    expect_graphql_result(result, {deleteReservations: [reservation.id.to_s]})
    expect(exists?(reservation)).to be false
  end

  it "deletes the last approved reservation of an order (hand over)" do
    order = create_order(state: "approved")
    only = create_open_reservation(order: order, state: "approved")

    result = delete_reservations([only.id])
    expect_graphql_result(result, {deleteReservations: [only.id.to_s]})
    expect(exists?(only)).to be false
  end

  it "fails when it would remove all open reservations of a submitted order" do
    order = create_order
    all = [create_open_reservation(order: order), create_open_reservation(order: order)]

    result = delete_reservations(all.map(&:id))
    expect_graphql_error(result, status: 422)
    expect(all.all? { |r| exists?(r) }).to be true
  end

  it "fails when a reservation is rejected" do
    order = create_order(state: "rejected")
    create_open_reservation(order: order, state: "rejected")
    doomed = create_open_reservation(order: order, state: "rejected")

    result = delete_reservations([doomed.id])
    expect_graphql_error(result, status: 422)
    expect(exists?(doomed)).to be true
  end

  it "fails when a reservation belongs to another pool" do
    own = create_open_reservation(state: "approved")
    foreign = create_open_reservation(state: "approved", inventory_pool: create(:inventory_pool))

    result = delete_reservations([own.id, foreign.id])
    expect_graphql_error(result, status: 404)
    expect(exists?(own)).to be true
    expect(exists?(foreign)).to be true
  end
end
