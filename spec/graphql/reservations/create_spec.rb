require "spec_helper"
require_relative "../graphql_helper"

describe "createReservation" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { lendable_model }

  before { grant_pool_access(user, pool) }

  def lendable_model(inventory_pool: pool, **item_attrs)
    create(:leihs_model).tap do |m|
      create(:item, leihs_model: m, inventory_pool: inventory_pool, owner: inventory_pool, **item_attrs)
    end
  end

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

  def create_reservation(model_id, start_date, end_date, requester_id,
    order_id: nil, user_id: nil, option_id: nil, inventory_code: nil)
    args = {
      orderId: order_id,
      userId: user_id || requester_id,
      modelId: model_id,
      optionId: option_id,
      inventoryCode: inventory_code,
      startDate: start_date,
      endDate: end_date
    }.compact.map { |k, v| %(#{k}: "#{v}") }.join("\n")
    query(<<~GQL, requester_id, pool_id: pool.id)
      mutation {
        createReservation(#{args}) {
          id startDate endDate
        }
      }
    GQL
  end

  def create_by_option(option_id, order_id: nil)
    start_date = Date.today.next_occurring(:monday)
    create_reservation(nil, start_date, start_date + 4, user.id,
      order_id: order_id, option_id: option_id)
  end

  def create_by_code(code, order_id: nil)
    start_date = Date.today.next_occurring(:monday)
    create_reservation(nil, start_date, start_date + 4, user.id,
      order_id: order_id, inventory_code: code)
  end

  it "adds a reservation with quantity 1 to a submitted order" do
    order = create_order
    new_model = lendable_model
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

  it "fails when the given order belongs to another pool" do
    other_pool = create(:inventory_pool)
    order = create(:order, user: user, inventory_pool: other_pool, state: "submitted")
    start_date = Date.today.next_occurring(:monday)
    end_date = start_date + 4

    result = create_reservation(model.id, start_date, end_date, user.id, order_id: order.id)
    expect_graphql_error(result, status: 404)
  end

  describe "by option id" do
    it "creates an option line without an order" do
      option = create(:option, inventory_pool: pool)

      result = create_by_option(option.id)
      expect(result[:errors]).to be_nil

      reservation = Reservation.where(id: result.dig(:data, :createReservation, :id)).first
      expect(reservation.option_id).to eq(option.id)
      expect(reservation.type).to eq("OptionLine")
    end

    it "fails when an order is given" do
      option = create(:option, inventory_pool: pool)

      result = create_by_option(option.id, order_id: create_order.id)
      expect_graphql_error(result, status: 422)
    end

    it "fails for another pool's option" do
      option = create(:option, inventory_pool: create(:inventory_pool))

      result = create_by_option(option.id)
      expect_graphql_error(result, status: 422)
    end
  end

  describe "model availability in pool" do
    def create_for(model_id)
      start_date = Date.today.next_occurring(:monday)
      create_reservation(model_id, start_date, start_date + 4, user.id)
    end

    it "accepts a model whose item is owned by another pool" do
      m = create(:leihs_model)
      create(:item, leihs_model: m, inventory_pool: pool, owner: create(:inventory_pool))

      expect(create_for(m.id)[:errors]).to be_nil
    end

    it "fails when the model does not exist" do
      expect_graphql_error(create_for(SecureRandom.uuid), status: 422)
    end

    it "fails when the model has no items in the pool" do
      expect_graphql_error(create_for(create(:leihs_model).id), status: 422)
    end

    it "fails when the model's items are only in another pool" do
      m = lendable_model(inventory_pool: create(:inventory_pool))
      expect_graphql_error(create_for(m.id), status: 422)
    end

    it "fails when the model's items are only owned by the pool" do
      m = create(:leihs_model)
      create(:item, leihs_model: m, inventory_pool: create(:inventory_pool), owner: pool)
      expect_graphql_error(create_for(m.id), status: 422)
    end

    it "fails when the model's items are all retired" do
      m = lendable_model(retired: Date.today, retired_reason: "broken")
      expect_graphql_error(create_for(m.id), status: 422)
    end

    it "fails when the model's items are all inside a package" do
      parent = create(:item, inventory_pool: pool, owner: pool)
      m = lendable_model(parent_id: parent.id)
      expect_graphql_error(create_for(m.id), status: 422)
    end
  end

  it "fails when both modelId and optionId are given" do
    option = create(:option, inventory_pool: pool)
    start_date = Date.today.next_occurring(:monday)

    result = create_reservation(model.id, start_date, start_date + 4, user.id, option_id: option.id)
    expect_graphql_error(result, status: 422)
  end

  describe "by inventory code" do
    let(:other_pool) { create(:inventory_pool) }

    it "adds a reservation for the item's model without assigning the item" do
      order = create_order
      new_model = create(:leihs_model)
      item = create(:item, leihs_model: new_model, inventory_pool: pool, owner: pool)

      result = create_by_code(item.inventory_code.downcase, order_id: order.id)
      expect(result[:errors]).to be_nil

      reservation = Reservation.where(id: result.dig(:data, :createReservation, :id)).first
      expect(reservation.model_id).to eq(new_model.id)
      expect(reservation.item_id).to be_nil
      expect(reservation.order_id).to eq(order.id)
    end

    it "creates an option line without an order" do
      option = create(:option, inventory_pool: pool, inventory_code: "OPT-1")

      result = create_by_code("opt-1")
      expect(result[:errors]).to be_nil

      reservation = Reservation.where(id: result.dig(:data, :createReservation, :id)).first
      expect(reservation.option_id).to eq(option.id)
      expect(reservation.model_id).to be_nil
      expect(reservation.type).to eq("OptionLine")
      expect(reservation.status).to eq("approved")
    end

    it "fails for an option when an order is given" do
      order = create_order
      create(:option, inventory_pool: pool, inventory_code: "OPT-2")

      result = create_by_code("OPT-2", order_id: order.id)
      expect_graphql_error(result, status: 422)
    end

    it "fails when the item is retired" do
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool,
        retired: Date.today, retired_reason: "broken")

      result = create_by_code(item.inventory_code)
      expect_graphql_error(result, status: 422)
    end

    it "fails when the item is inside a package" do
      parent = create(:item, inventory_pool: pool, owner: pool)
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool, parent_id: parent.id)

      result = create_by_code(item.inventory_code)
      expect_graphql_error(result, status: 422)
    end

    it "fails when the pool only owns the item" do
      item = create(:item, leihs_model: model, inventory_pool: other_pool, owner: pool)

      result = create_by_code(item.inventory_code)
      expect_graphql_error(result, status: 422)
    end

    it "fails when the item belongs to another pool" do
      item = create(:item, leihs_model: model, inventory_pool: other_pool, owner: other_pool)

      result = create_by_code(item.inventory_code)
      expect_graphql_error(result, status: 404)
    end

    it "fails when the code is unknown" do
      result = create_by_code("NOPE-#{SecureRandom.hex(4)}")
      expect_graphql_error(result, status: 404)
    end

    it "fails when both modelId and inventoryCode are given" do
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool)
      start_date = Date.today.next_occurring(:monday)

      result = create_reservation(model.id, start_date, start_date + 4, user.id,
        inventory_code: item.inventory_code)
      expect_graphql_error(result, status: 422)
    end

    it "fails when none of modelId, optionId, inventoryCode is given" do
      result = create_reservation(nil, Date.today, Date.today + 1, user.id)
      expect_graphql_error(result, status: 422)
    end
  end
end
