require "spec_helper"
require_relative "../graphql_helper"

describe "creating reservations" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { lendable_model }
  let(:start_date) { Date.today.next_occurring(:monday) }
  let(:end_date) { start_date + 4 }

  before { grant_pool_access(user, pool) }

  def lendable_model(inventory_pool: pool, **item_attrs)
    create(:leihs_model).tap do |m|
      create(:item, leihs_model: m, inventory_pool: inventory_pool, owner: inventory_pool, **item_attrs)
    end
  end

  def create_order(state: "submitted", inventory_pool: pool)
    order = create(:order, user: user, inventory_pool: inventory_pool, state: state)
    create(:reservation,
      user: user,
      inventory_pool: inventory_pool,
      leihs_model: model,
      order: order,
      status: state)
    order
  end

  def mutate(name, **args)
    args = {userId: user.id, startDate: start_date, endDate: end_date}
      .merge(args)
      .compact
      .map { |k, v| %(#{k}: "#{v}") }
      .join("\n")
    query(<<~GQL, user.id, pool_id: pool.id)
      mutation {
        #{name}(#{args}) {
          id startDate endDate
        }
      }
    GQL
  end

  def created(result, name)
    Reservation.where(id: result.dig(:data, name, :id)).first
  end

  describe "createModelReservation" do
    def create_for(model_id, order_id: nil)
      mutate(:createModelReservation, modelId: model_id, orderId: order_id)
    end

    it "adds a reservation with quantity 1 to a submitted order" do
      order = create_order
      new_model = lendable_model

      result = create_for(new_model.id, order_id: order.id)
      reservation_id = result.dig(:data, :createModelReservation, :id)
      expect_graphql_result(result, {
        createModelReservation: {
          id: reservation_id,
          startDate: start_date.to_s,
          endDate: end_date.to_s
        }
      })

      reservation = created(result, :createModelReservation)
      expect(reservation.order_id).to eq(order.id)
      expect(reservation.model_id).to eq(new_model.id)
      expect(reservation.quantity).to eq(1)
      expect(reservation.status).to eq("submitted")
      expect(reservation.user_id).to eq(user.id)
      expect(reservation.inventory_pool_id).to eq(pool.id)
      expect(reservation.start_date).to eq(start_date)
      expect(reservation.end_date).to eq(end_date)
    end

    it "creates an approved reservation without an order" do
      result = create_for(model.id)
      expect(result[:errors]).to be_nil

      reservation = created(result, :createModelReservation)
      expect(reservation.order_id).to be_nil
      expect(reservation.status).to eq("approved")
    end

    it "fails when the given order is not in submitted state" do
      expect_graphql_error(create_for(model.id, order_id: create_order(state: "approved").id),
        status: 422)
    end

    it "fails when the given order belongs to another pool" do
      order = create(:order, user: user, inventory_pool: create(:inventory_pool), state: "submitted")
      expect_graphql_error(create_for(model.id, order_id: order.id), status: 404)
    end

    describe "model availability in pool" do
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
  end

  describe "createOptionReservation" do
    def create_for(option_id, **args)
      mutate(:createOptionReservation, optionId: option_id, **args)
    end

    it "creates an approved option line" do
      option = create(:option, inventory_pool: pool)

      result = create_for(option.id)
      expect(result[:errors]).to be_nil

      reservation = created(result, :createOptionReservation)
      expect(reservation.option_id).to eq(option.id)
      expect(reservation.model_id).to be_nil
      expect(reservation.type).to eq("OptionLine")
      expect(reservation.status).to eq("approved")
    end

    it "does not accept an orderId (schema)" do
      option = create(:option, inventory_pool: pool)
      expect_graphql_error(create_for(option.id, orderId: create_order.id))
    end

    it "fails for another pool's option" do
      option = create(:option, inventory_pool: create(:inventory_pool))
      expect_graphql_error(create_for(option.id), status: 422)
    end
  end

  describe "createReservationByInventoryCode" do
    let(:other_pool) { create(:inventory_pool) }

    def create_for(code, order_id: nil)
      mutate(:createReservationByInventoryCode, inventoryCode: code, orderId: order_id)
    end

    it "adds a reservation for the item's model without assigning the item" do
      order = create_order
      new_model = create(:leihs_model)
      item = create(:item, leihs_model: new_model, inventory_pool: pool, owner: pool)

      result = create_for(item.inventory_code.downcase, order_id: order.id)
      expect(result[:errors]).to be_nil

      reservation = created(result, :createReservationByInventoryCode)
      expect(reservation.model_id).to eq(new_model.id)
      expect(reservation.item_id).to be_nil
      expect(reservation.order_id).to eq(order.id)
    end

    it "creates an option line without an order" do
      option = create(:option, inventory_pool: pool, inventory_code: "OPT-1")

      result = create_for("opt-1")
      expect(result[:errors]).to be_nil

      reservation = created(result, :createReservationByInventoryCode)
      expect(reservation.option_id).to eq(option.id)
      expect(reservation.type).to eq("OptionLine")
      expect(reservation.status).to eq("approved")
    end

    it "fails for an option when an order is given" do
      create(:option, inventory_pool: pool, inventory_code: "OPT-2")
      expect_graphql_error(create_for("OPT-2", order_id: create_order.id), status: 422)
    end

    it "fails when the given order is not in submitted state" do
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool)
      expect_graphql_error(create_for(item.inventory_code, order_id: create_order(state: "approved").id),
        status: 422)
    end

    it "fails when the item is retired" do
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool,
        retired: Date.today, retired_reason: "broken")
      expect_graphql_error(create_for(item.inventory_code), status: 422)
    end

    it "fails when the item is inside a package" do
      parent = create(:item, inventory_pool: pool, owner: pool)
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool, parent_id: parent.id)
      expect_graphql_error(create_for(item.inventory_code), status: 422)
    end

    it "fails when the pool only owns the item" do
      item = create(:item, leihs_model: model, inventory_pool: other_pool, owner: pool)
      expect_graphql_error(create_for(item.inventory_code), status: 422)
    end

    it "fails when the item belongs to another pool" do
      item = create(:item, leihs_model: model, inventory_pool: other_pool, owner: other_pool)
      expect_graphql_error(create_for(item.inventory_code), status: 404)
    end

    it "fails when the code is unknown" do
      expect_graphql_error(create_for("NOPE-#{SecureRandom.hex(4)}"), status: 404)
    end
  end
end
