require "spec_helper"
require_relative "../graphql_helper"

describe "order access across pools" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:other_pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let(:order) do
    create(:order, user: user, inventory_pool: other_pool, state: "submitted").tap do |o|
      create(:reservation,
        user: user,
        inventory_pool: other_pool,
        leihs_model: model,
        order: o,
        status: "submitted")
    end
  end

  before do
    grant_pool_access(user, pool)
    grant_pool_access(user, other_pool)
  end

  def run(gql)
    query(gql, user.id, pool_id: pool.id)
  end

  it "does not return another pool's order" do
    result = run(%({ order(id: "#{order.id}") { id } }))
    expect_graphql_error(result, status: 404)
  end

  it "returns 404 for an unknown order" do
    result = run(%({ order(id: "#{SecureRandom.uuid}") { id } }))
    expect_graphql_error(result, status: 404)
  end

  it "does not list another pool's orders" do
    order
    result = run("{ orders { items { id } totalCount } }")
    expect_graphql_result(result, {orders: {items: [], totalCount: 0}})
  end

  {
    "rejectOrder" => %(reason: "x"),
    "approveOrder" => "force: true",
    "updateOrderPurpose" => %(purpose: "x"),
    "swapOrderUser" => nil
  }.each do |mutation, extra|
    it "#{mutation} fails for another pool's order" do
      extra ||= %(userId: "#{user.id}")
      result = run(%(mutation { #{mutation}(id: "#{order.id}", #{extra}) { id } }))
      expect_graphql_error(result, status: 404)
      expect(order.reload.state).to eq("submitted")
      expect(order.user_id).to eq(user.id)
      expect(order.purpose).not_to eq("x")
    end
  end
end
