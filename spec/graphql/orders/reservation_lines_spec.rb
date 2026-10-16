require "spec_helper"
require_relative "../graphql_helper"

describe "reservationLines" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }

  before { grant_pool_access(user, pool) }

  def create_order
    create(:order, user: user, inventory_pool: pool, state: "submitted")
  end

  def add_reservation(order, leihs_model: model, **attrs)
    create(:reservation,
      user: user,
      inventory_pool: pool,
      leihs_model: leihs_model,
      order: order,
      status: "submitted",
      **attrs)
  end

  def reservation_lines_for(order)
    query(<<~GQL, user.id, pool_id: pool.id)
      { order(id: "#{order.id}") { reservationLines { quantity availableQuantity totalAvailableQuantity reservationIds } } }
    GQL
  end

  it "groups reservations with the same model/pool/date-range into one line and sums quantity" do
    order = create_order
    3.times { add_reservation(order) }
    result = reservation_lines_for(order)
    expect(result[:errors]).to be_nil
    lines = result.dig(:data, :order, :reservationLines)
    expect(lines.size).to eq(1)
    expect(lines.first[:quantity]).to eq(3)
    expect(lines.first[:reservationIds]).to match_array(
      Reservation.where(order_id: order.id).map { |r| r.id.to_s }
    )
  end

  it "splits reservations into separate lines by model" do
    other_model = create(:leihs_model)
    order = create_order
    add_reservation(order, leihs_model: model)
    add_reservation(order, leihs_model: other_model)
    result = reservation_lines_for(order)
    lines = result.dig(:data, :order, :reservationLines)
    expect(lines.size).to eq(2)
    expect(lines.map { |l| l[:quantity] }).to eq([1, 1])
  end

  it "splits reservations into separate lines by date range" do
    order = create_order
    add_reservation(order, start_date: "2026-01-01", end_date: "2026-01-05")
    add_reservation(order, start_date: "2026-02-01", end_date: "2026-02-05")
    result = reservation_lines_for(order)
    lines = result.dig(:data, :order, :reservationLines)
    expect(lines.size).to eq(2)
  end

  it "computes available quantity from the model's stock, excluding the line's own reservations" do
    create(:item, leihs_model: model, inventory_pool: pool, owner: pool)
    order = create_order
    add_reservation(order)
    result = reservation_lines_for(order)
    line = result.dig(:data, :order, :reservationLines, 0)
    expect(line[:quantity]).to eq(1)
    expect(line[:availableQuantity]).to eq(1)
    expect(line[:totalAvailableQuantity]).to eq(1)
  end

  it "shows negative available quantity when overbooked" do
    start_date = (Date.today + 7).to_s
    end_date = (Date.today + 9).to_s
    create(:item, leihs_model: model, inventory_pool: pool, owner: pool)
    other_user = create(:user)
    other_order = create(:order, user: other_user, inventory_pool: pool, state: "submitted")
    2.times do
      create(:reservation, user: other_user, inventory_pool: pool, leihs_model: model,
        order: other_order, status: "submitted", start_date: start_date, end_date: end_date)
    end
    order = create_order
    add_reservation(order, start_date: start_date, end_date: end_date)

    line = reservation_lines_for(order).dig(:data, :order, :reservationLines, 0)
    expect(line[:availableQuantity]).to eq(-1)
    expect(line[:totalAvailableQuantity]).to eq(-1)
  end

  it "total available quantity includes groups the user is not member of" do
    start_date = (Date.today + 7).to_s
    end_date = (Date.today + 9).to_s
    2.times { create(:item, leihs_model: model, inventory_pool: pool, owner: pool) }
    group = create(:entitlement_group, inventory_pool: pool)
    create(:entitlement, leihs_model: model, entitlement_group: group, quantity: 2)
    order = create_order
    add_reservation(order, start_date: start_date, end_date: end_date)

    line = reservation_lines_for(order).dig(:data, :order, :reservationLines, 0)
    expect(line[:availableQuantity]).to eq(0)
    expect(line[:totalAvailableQuantity]).to eq(2)
  end
end
