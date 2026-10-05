require "spec_helper"
require_relative "../graphql_helper"

describe "Model.availability" do
  let(:user) { create(:user) }
  let(:customer) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let(:today) { Time.now.utc.to_date }
  let(:start_date) { today + 7 }
  let(:end_date) { today + 9 }

  before do
    grant_pool_access(user, pool)
    open_all_days
  end

  def open_all_days(**attrs)
    days = %i[monday tuesday wednesday thursday friday saturday sunday]
    database[:workdays]
      .where(inventory_pool_id: pool.id)
      .update(days.to_h { |d| [d, true] }.merge(days.to_h { |d| [:"#{d}_orders_processing", true] }).merge(attrs))
  end

  def create_items(count)
    Array.new(count) { create(:item, leihs_model: model, inventory_pool: pool) }
  end

  def add_reservation(reservation_user: customer, from: start_date, to: end_date)
    create(:reservation,
      user: reservation_user,
      inventory_pool: pool,
      leihs_model: model,
      order: create(:order, user: reservation_user, inventory_pool: pool, state: "submitted"),
      start_date: from.to_s,
      end_date: to.to_s)
  end

  def create_group_with(quantity, member: nil)
    group = create(:entitlement_group, inventory_pool: pool)
    create(:entitlement, leihs_model: model, entitlement_group: group, quantity: quantity)
    create(:entitlement_groups_direct_user, user: member, entitlement_group: group) if member
    group
  end

  def availability_result(from: start_date, to: end_date, args: "userId: \"#{customer.id}\"")
    query(<<~GQL, user.id, pool_id: pool.id)
      { model(id: "#{model.id}") {
          availability(startDate: "#{from}", endDate: "#{to}", #{args}) {
            date quantity totalQuantity startDateRestrictions endDateRestrictions } } }
    GQL
  end

  def availability(**opts)
    result = availability_result(**opts)
    expect(result[:errors]).to be_nil
    result.dig(:data, :model, :availability)
  end

  def quantities(days)
    days.map { |d| [d[:date], d[:quantity], d[:totalQuantity]] }
  end

  it "returns quantities per date, reduced by reservations" do
    create_items(2)
    add_reservation(from: start_date + 1, to: start_date + 1)

    expect(quantities(availability)).to eq([
      [start_date.to_s, 2, 2],
      [(start_date + 1).to_s, 1, 1],
      [end_date.to_s, 2, 2]
    ])
  end

  it "shows negative quantity when overbooked" do
    create_items(1)
    2.times { add_reservation }

    expect(availability.map { |d| d[:quantity] }).to all(eq(-1))
  end

  it "returns 0 for past dates" do
    create_items(1)

    expect(quantities(availability(from: today - 2, to: today))).to eq([
      [(today - 2).to_s, 0, 0],
      [(today - 1).to_s, 0, 0],
      [today.to_s, 1, 1]
    ])
  end

  it "excludes the given reservations" do
    create_items(1)
    reservation = add_reservation

    days = availability(args: "userId: \"#{customer.id}\", excludeReservationIds: [\"#{reservation.id}\"]")
    expect(days.map { |d| d[:quantity] }).to all(eq(1))
  end

  context "with entitlement groups" do
    before { create_items(3) }

    it "sums general and the user's groups, total over all groups" do
      create_group_with(1, member: customer)
      create_group_with(1)

      expect(availability.map { |d| [d[:quantity], d[:totalQuantity]] }).to all(eq([2, 3]))
    end

    it "sums general and the given group" do
      group = create_group_with(1)
      create_group_with(1)

      days = availability(args: "entitlementGroupId: \"#{group.id}\"")
      expect(days.map { |d| [d[:quantity], d[:totalQuantity]] }).to all(eq([2, 3]))
    end

    it "returns 404 for a group of another pool" do
      group = create(:entitlement_group, inventory_pool: create(:inventory_pool))

      result = availability_result(args: "entitlementGroupId: \"#{group.id}\"")
      expect(result[:errors].first[:message]).to eq("Entitlement group not found")
    end
  end

  it "requires exactly one of userId or entitlementGroupId" do
    group = create(:entitlement_group, inventory_pool: pool)

    [
      "",
      "userId: \"#{customer.id}\", entitlementGroupId: \"#{group.id}\""
    ].each do |args|
      result = availability_result(args: args)
      expect(result[:errors].first[:message]).to eq("Exactly one of userId or entitlementGroupId is required")
    end
  end

  describe "restrictions" do
    before { create_items(1) }

    def restrictions_on(date)
      day = availability(from: date, to: date).first
      expect(day[:startDateRestrictions]).to eq(day[:endDateRestrictions])
      day[:startDateRestrictions]
    end

    it "is empty on an open day" do
      expect(restrictions_on(start_date)).to eq([])
    end

    it "flags a non-workday" do
      day = start_date.strftime("%A").downcase
      open_all_days(day.to_sym => false, :"#{day}_orders_processing" => false)

      expect(restrictions_on(start_date)).to eq(["NON_WORKDAY"])
    end

    it "flags a holiday" do
      create(:holiday, inventory_pool: pool, start_date: start_date.to_s, end_date: start_date.to_s)

      expect(restrictions_on(start_date)).to eq(["HOLIDAY"])
    end

    it "flags reached visits capacity" do
      database[:workdays]
        .where(inventory_pool_id: pool.id)
        .update(max_visits: Sequel.pg_jsonb({start_date.wday.to_s => "1"}))

      expect(restrictions_on(start_date)).to eq([])
      add_reservation
      expect(restrictions_on(start_date)).to eq(["VISITS_CAPACITY_REACHED"])
    end
  end
end
