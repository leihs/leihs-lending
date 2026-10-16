require "spec_helper"
require_relative "../graphql_helper"

describe "Model.borrowableQuantity" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }

  before { grant_pool_access(user, pool) }

  def borrowable_quantity
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { model(id: "#{model.id}") { borrowableQuantity } }
    GQL
    expect(result[:errors]).to be_nil
    result.dig(:data, :model, :borrowableQuantity)
  end

  it "counts borrowable items of the model in the pool" do
    2.times { create(:item, leihs_model: model, inventory_pool: pool) }
    expect(borrowable_quantity).to eq(2)
  end

  it "ignores unborrowable, retired and packaged items, other models and other pools" do
    create(:item, leihs_model: model, inventory_pool: pool)
    create(:item, leihs_model: model, inventory_pool: pool, is_borrowable: false)
    create(:item, leihs_model: model, inventory_pool: pool,
      retired: Date.today, retired_reason: "broken")
    package = create(:item, leihs_model: create(:leihs_model, is_package: true), inventory_pool: pool)
    create(:item, leihs_model: model, inventory_pool: pool, parent_id: package.id)
    create(:item, inventory_pool: pool)
    create(:item, leihs_model: model)

    expect(borrowable_quantity).to eq(1)
  end

  it "ignores reservations" do
    create(:item, leihs_model: model, inventory_pool: pool)
    create(:reservation,
      user: user,
      inventory_pool: pool,
      leihs_model: model,
      order: create(:order, user: user, inventory_pool: pool, state: "submitted"),
      start_date: Date.today.to_s,
      end_date: (Date.today + 2).to_s)

    expect(borrowable_quantity).to eq(1)
  end
end
