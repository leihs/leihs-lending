require "spec_helper"
require_relative "../graphql_helper"

describe "Model.availabilityChanges" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let(:today) { Time.now.utc.to_date }
  let(:start_date) { today + 7 }
  let(:end_date) { today + 11 }

  before { grant_pool_access(user, pool) }

  def create_items(count, **attrs)
    Array.new(count) { create(:item, leihs_model: model, inventory_pool: pool, **attrs) }
  end

  def add_reservation(reservation_user: user, **attrs)
    create(:reservation,
      user: reservation_user,
      inventory_pool: pool,
      leihs_model: model,
      order: create(:order, user: reservation_user, inventory_pool: pool, state: "submitted"),
      start_date: start_date.to_s,
      end_date: end_date.to_s,
      **attrs)
  end

  def add_signed_reservation(item:, **attrs)
    Sequel::Model.db.transaction do
      db_with_disabled_triggers do
        contract = create(:contract, user: user, inventory_pool: pool)
        create(:reservation,
          user: user,
          inventory_pool: pool,
          leihs_model: model,
          item_id: item.id,
          order: nil,
          contract_id: contract.id,
          status: "signed",
          **attrs)
      end
    end
  end

  def availability_changes
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { model(id: "#{model.id}") {
          availabilityChanges { date groups { entitlementGroup { id name } inQuantity reservations { id } } } } }
    GQL
    expect(result[:errors]).to be_nil
    result.dig(:data, :model, :availabilityChanges)
  end

  def general(in_quantity, reservation_ids = [])
    {entitlementGroup: nil, inQuantity: in_quantity, reservations: reservation_ids.map { |id| {id: id.to_s} }}
  end

  it "returns a single change today when there are no reservations" do
    create_items(2)
    expect(availability_changes).to eq([{date: today.to_s, groups: [general(2)]}])
  end

  it "adds changes at a reservation's start and the day after its end" do
    create_items(2)
    reservation = add_reservation

    expect(availability_changes).to eq([
      {date: today.to_s, groups: [general(2)]},
      {date: start_date.to_s, groups: [general(1, [reservation.id])]},
      {date: (end_date + 1).to_s, groups: [general(2)]}
    ])
  end

  it "allocates a reservation to the user's entitlement group, general first" do
    create_items(2)
    group = create(:entitlement_group, inventory_pool: pool)
    create(:entitlement, leihs_model: model, entitlement_group: group, quantity: 1)
    create(:entitlement_groups_direct_user, user: user, entitlement_group: group)
    reservation = add_reservation

    group_row = ->(in_quantity, ids = []) {
      {entitlementGroup: {id: group.id.to_s, name: group.name}, inQuantity: in_quantity,
       reservations: ids.map { |id| {id: id.to_s} }}
    }
    expect(availability_changes).to eq([
      {date: today.to_s, groups: [general(1), group_row.call(1)]},
      {date: start_date.to_s, groups: [general(1), group_row.call(0, [reservation.id])]},
      {date: (end_date + 1).to_s, groups: [general(1), group_row.call(1)]}
    ])
  end

  it "shows negative quantity when overbooked" do
    create_items(1)
    r1 = add_reservation
    r2 = add_reservation

    change = availability_changes.find { |c| c[:date] == start_date.to_s }
    expect(change[:groups].first[:inQuantity]).to eq(-1)
    expect(change[:groups].first[:reservations]).to match_array([{id: r1.id.to_s}, {id: r2.id.to_s}])
  end

  it "blocks a late handed-over item from today for one month" do
    item = create_items(1).first
    reservation = add_signed_reservation(item: item,
      start_date: (today - 10).to_s,
      end_date: (today - 1).to_s)

    expect(availability_changes).to eq([
      {date: today.to_s, groups: [general(0, [reservation.id])]},
      {date: (today >> 1).next_day.to_s, groups: [general(1)]}
    ])
  end

  it "ignores reservations with an unborrowable item assigned" do
    create_items(1)
    unborrowable = create(:item, leihs_model: model, inventory_pool: pool, is_borrowable: false)
    add_signed_reservation(item: unborrowable,
      start_date: today.to_s,
      end_date: (today + 3).to_s)

    expect(availability_changes).to eq([{date: today.to_s, groups: [general(1)]}])
  end
end
