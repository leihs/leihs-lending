require "spec_helper"
require_relative "../graphql_helper"

# The edit-order calendar saves changed lines by creating their new
# reservations and deleting the old ones in a single mutation request.
describe "replacing reservations in one request" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { lendable_model }
  let(:old_start) { Date.today.next_occurring(:monday) }
  let(:new_start) { old_start + 7 }
  let(:new_end) { new_start + 2 }
  let(:order) { create(:order, user: user, inventory_pool: pool, state: "submitted") }

  before { grant_pool_access(user, pool) }

  def lendable_model
    create(:leihs_model).tap do |m|
      create(:item, leihs_model: m, inventory_pool: pool, owner: pool)
    end
  end

  def create_reservation(leihs_model: model)
    create(:reservation,
      user: user,
      inventory_pool: pool,
      leihs_model: leihs_model,
      order: order,
      status: "submitted",
      start_date: old_start.to_s,
      end_date: (old_start + 4).to_s)
  end

  def create_field(alias_name, model_var)
    <<~GQL
      #{alias_name}: createModelReservation(orderId: $orderId, userId: $userId,
                                            modelId: $#{model_var},
                                            startDate: $startDate, endDate: $endDate) { id }
    GQL
  end

  def delete_field
    "deleteReservations(ids: $ids)\n"
  end

  # `model_ids` holds one model id per reservation to create
  def replace(old_ids, model_ids, delete_first: false)
    distinct = model_ids.uniq
    var_of = distinct.each_with_index.to_h { |id, i| [id, "modelId#{i}"] }
    creates = model_ids.each_with_index.map { |id, i| create_field("create#{i}", var_of[id]) }
    fields = delete_first ? [delete_field, *creates] : [*creates, delete_field]
    model_var_decls = var_of.values.map { |v| ", $#{v}: UUID!" }.join

    mutation = <<~GQL
      mutation ($orderId: UUID!, $userId: UUID!, $startDate: Date!,
                $endDate: Date!, $ids: [UUID!]!#{model_var_decls}) {
        #{fields.join}
      }
    GQL
    variables = {
      orderId: order.id,
      userId: user.id,
      startDate: new_start.to_s,
      endDate: new_end.to_s,
      ids: old_ids
    }.merge(var_of.to_h { |id, v| [v, id] })

    query(mutation, user.id, pool_id: pool.id, variables: variables)
  end

  def reservations_of_order
    Reservation.where(order_id: order.id).all
  end

  it "replaces the only reservation of a submitted order" do
    old = create_reservation

    result = replace([old.id], [model.id])
    expect(result[:errors]).to be_nil
    expect(result.dig(:data, :deleteReservations)).to eq([old.id.to_s])

    reservations = reservations_of_order
    expect(reservations.map(&:id)).to eq([result.dig(:data, :create0, :id)])
    expect(reservations.first.model_id).to eq(model.id)
    expect(reservations.first.start_date).to eq(new_start)
    expect(reservations.first.end_date).to eq(new_end)
    expect(reservations.first.status).to eq("submitted")
  end

  it "raises the quantity by creating one reservation per unit" do
    old = create_reservation

    result = replace([old.id], [model.id] * 3)
    expect(result[:errors]).to be_nil

    reservations = reservations_of_order
    expect(reservations.size).to eq(3)
    expect(reservations.map(&:model_id)).to all(eq(model.id))
    expect(reservations.map(&:quantity)).to all(eq(1))
    expect(reservations.map(&:start_date)).to all(eq(new_start))
    expect(reservations.map(&:end_date)).to all(eq(new_end))
  end

  it "lowers the quantity" do
    old = [create_reservation, create_reservation, create_reservation]

    result = replace(old.map(&:id), [model.id])
    expect(result[:errors]).to be_nil
    expect(reservations_of_order.size).to eq(1)
  end

  it "replaces lines of several models at once, keeping each model" do
    other_model = lendable_model
    old = [create_reservation, create_reservation(leihs_model: other_model)]

    result = replace(old.map(&:id), [model.id, other_model.id])
    expect(result[:errors]).to be_nil

    reservations = reservations_of_order
    expect(reservations.map(&:model_id)).to match_array([model.id, other_model.id])
    expect(reservations.map(&:start_date)).to all(eq(new_start))
    expect(reservations.map(&:end_date)).to all(eq(new_end))
    expect(reservations.map(&:id)).not_to include(*old.map(&:id))
  end

  it "fails when deleting comes first, as the order would run out of reservations" do
    old = create_reservation

    result = replace([old.id], [model.id], delete_first: true)
    expect_graphql_error(result, status: 422)
    expect(reservations_of_order.map(&:id)).to eq([old.id])
  end

  it "rolls back the created reservations when deleting fails" do
    old = create_reservation
    foreign = create(:reservation,
      user: user,
      inventory_pool: create(:inventory_pool),
      leihs_model: model,
      order: nil,
      status: "approved")

    result = replace([old.id, foreign.id], [model.id, model.id])
    expect_graphql_error(result, status: 404)

    reservations = reservations_of_order
    expect(reservations.map(&:id)).to eq([old.id])
    expect(reservations.first.start_date).to eq(old_start)
  end

  it "rolls back the deletion when a create fails" do
    old = create_reservation
    unlendable = create(:leihs_model)

    result = replace([old.id], [model.id, unlendable.id])
    expect(result[:errors]).not_to be_empty
    expect(reservations_of_order.map(&:id)).to eq([old.id])
  end
end
