require "spec_helper"
require_relative "../graphql_helper"

describe "swapModel" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let(:new_model) { create(:leihs_model) }

  before { grant_pool_access(user, pool) }

  def create_order(state: "submitted", inventory_pool: pool)
    create(:order, user: user, inventory_pool: inventory_pool, state: state)
  end

  def create_reservation(order: nil, status: "submitted", inventory_pool: pool, item: nil)
    create(:reservation,
      user: user,
      inventory_pool: inventory_pool,
      leihs_model: model,
      order: order,
      item_id: item&.id,
      status: status)
  end

  def swap_model(ids, model_id)
    ids_arg = ids.map { |id| %("#{id}") }.join(", ")
    query(<<~GQL, user.id, pool_id: pool.id)
      mutation {
        swapModel(ids: [#{ids_arg}], modelId: "#{model_id}") {
          id model { id }
        }
      }
    GQL
  end

  it "swaps the model of reservations on a submitted order" do
    order = create_order
    r1 = create_reservation(order: order)
    r2 = create_reservation(order: order)

    result = swap_model([r1.id, r2.id], new_model.id)
    expect(result[:errors]).to be_nil
    expect(result.dig(:data, :swapModel).map { |r| r[:id] })
      .to match_array([r1.id, r2.id].map(&:to_s))
    expect(Reservation.where(id: [r1.id, r2.id]).map(&:model_id).uniq)
      .to eq([new_model.id])
  end

  it "unassigns the item" do
    item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool)
    reservation = create_reservation(status: "approved", item: item)

    result = swap_model([reservation.id], new_model.id)
    expect(result[:errors]).to be_nil
    reservation.reload
    expect(reservation.model_id).to eq(new_model.id)
    expect(reservation.item_id).to be_nil
  end

  it "fails when the model does not exist" do
    reservation = create_reservation(status: "approved")

    result = swap_model([reservation.id], SecureRandom.uuid)
    expect_graphql_error(result, status: 422)
  end

  it "fails when a reservation is rejected" do
    order = create_order(state: "rejected")
    reservation = create_reservation(order: order, status: "rejected")

    result = swap_model([reservation.id], new_model.id)
    expect_graphql_error(result, status: 422)
    expect(reservation.reload.model_id).to eq(model.id)
  end

  it "fails when a reservation is signed (handed over)" do
    reservation = Sequel::Model.db.transaction do
      db_with_disabled_triggers do
        contract = create(:contract, user: user, inventory_pool: pool)
        create(:reservation,
          user: user,
          inventory_pool: pool,
          leihs_model: model,
          order: nil,
          contract_id: contract.id,
          status: "signed")
      end
    end

    result = swap_model([reservation.id], new_model.id)
    expect_graphql_error(result, status: 422)
    expect(reservation.reload.model_id).to eq(model.id)
  end

  it "fails when a reservation does not exist" do
    result = swap_model([SecureRandom.uuid], new_model.id)
    expect_graphql_error(result, status: 404)
  end

  it "fails when a reservation belongs to another pool" do
    other_pool = create(:inventory_pool)
    own = create_reservation(status: "approved")
    foreign = create_reservation(status: "approved", inventory_pool: other_pool)

    result = swap_model([own.id, foreign.id], new_model.id)
    expect_graphql_error(result, status: 404)
    expect(own.reload.model_id).to eq(model.id)
    expect(foreign.reload.model_id).to eq(model.id)
  end
end
