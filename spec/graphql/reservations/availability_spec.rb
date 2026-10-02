require "spec_helper"
require_relative "../graphql_helper"

describe "availability for reservations" do
  let(:user) { create(:user) }
  let(:customer) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model_a) { create(:leihs_model) }
  let(:model_b) { create(:leihs_model) }
  let(:today) { Time.now.utc.to_date }
  let(:start_date) { today + 7 }
  let(:end_date) { today + 9 }
  let(:order) { create(:order, user: customer, inventory_pool: pool, state: "submitted") }

  before do
    grant_pool_access(user, pool)
    days = %i[monday tuesday wednesday thursday friday saturday sunday]
    database[:workdays]
      .where(inventory_pool_id: pool.id)
      .update(days.to_h { |d| [d, true] }.merge(days.to_h { |d| [:"#{d}_orders_processing", true] }))
  end

  def create_items(model, count)
    Array.new(count) { create(:item, leihs_model: model, inventory_pool: pool) }
  end

  def add_reservation(model, reservation_user: customer, reservation_order: order, from: start_date, to: end_date)
    create(:reservation,
      user: reservation_user,
      inventory_pool: pool,
      leihs_model: model,
      order: reservation_order,
      start_date: from.to_s,
      end_date: to.to_s)
  end

  def availability_result(reservations)
    ids = reservations.map { |r| "\"#{r.id}\"" }.join(", ")
    query(<<~GQL, user.id, pool_id: pool.id)
      { availability(reservationIds: [#{ids}], startDate: "#{start_date}", endDate: "#{end_date}") {
          date available quantity totalQuantity startDateRestrictions endDateRestrictions } }
    GQL
  end

  def availability(reservations)
    result = availability_result(reservations)
    expect(result[:errors]).to be_nil
    result.dig(:data, :availability)
  end

  def available_by_date(reservations)
    availability(reservations).map { |d| [d[:date], d[:available]] }
  end

  def dates
    (start_date..end_date).map(&:to_s)
  end

  it "is available on all days when every model is" do
    create_items(model_a, 1)
    create_items(model_b, 1)
    rs = [add_reservation(model_a), add_reservation(model_b)]

    days = availability(rs)
    expect(days.map { |d| d[:date] }).to eq(dates)
    expect(days.map { |d| d.slice(:available, :quantity, :totalQuantity) })
      .to all(eq(available: true, quantity: nil, totalQuantity: nil))
  end

  it "is unavailable on days one of the models is short" do
    create_items(model_a, 1)
    create_items(model_b, 1)
    rs = [add_reservation(model_a), add_reservation(model_b)]
    other_order = create(:order, user: user, inventory_pool: pool, state: "submitted")
    add_reservation(model_b, reservation_user: user, reservation_order: other_order,
      from: start_date + 1, to: start_date + 1)

    expect(available_by_date(rs)).to eq([
      [dates[0], true],
      [dates[1], false],
      [dates[2], true]
    ])
  end

  it "requires the summed quantity per model" do
    create_items(model_a, 1)
    rs = [add_reservation(model_a), add_reservation(model_a)]

    expect(available_by_date(rs).map(&:last)).to all(be(false))

    create_items(model_a, 1)
    expect(available_by_date(rs).map(&:last)).to all(be(true))
  end

  it "is available on all days for option lines only" do
    option = create(:option, inventory_pool: pool)
    r = create(:reservation,
      user: customer,
      inventory_pool: pool,
      leihs_model: nil,
      option_id: option.id,
      type: "OptionLine",
      order: nil,
      status: "approved",
      start_date: start_date.to_s,
      end_date: end_date.to_s)

    expect(available_by_date([r]).map(&:last)).to all(be(true))
  end

  it "returns restrictions" do
    create_items(model_a, 1)
    create(:holiday, inventory_pool: pool, start_date: start_date.to_s, end_date: start_date.to_s)

    days = availability([add_reservation(model_a)])
    expect(days.first[:startDateRestrictions]).to eq(["HOLIDAY"])
    expect(days.first[:endDateRestrictions]).to eq(["HOLIDAY"])
    expect(days.last[:startDateRestrictions]).to eq([])
  end

  it "returns 404 for a reservation of another pool" do
    other_pool = create(:inventory_pool)
    r = create(:reservation, user: customer, inventory_pool: other_pool, leihs_model: model_a,
      order: create(:order, user: customer, inventory_pool: other_pool, state: "submitted"))

    result = availability_result([r])
    expect(result[:errors].first[:message]).to eq("Reservation not found")
  end

  it "returns 422 for reservations of different users" do
    create_items(model_a, 2)
    other_order = create(:order, user: user, inventory_pool: pool, state: "submitted")
    rs = [add_reservation(model_a),
      add_reservation(model_a, reservation_user: user, reservation_order: other_order)]

    result = availability_result(rs)
    expect(result[:errors].first[:message]).to eq("Reservations must belong to a single user")
  end
end
