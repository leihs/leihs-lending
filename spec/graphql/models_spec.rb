require "spec_helper"
require_relative "graphql_helper"

describe "models" do
  let(:requester) { create(:user) }
  let(:pool) { create(:inventory_pool) }

  before { grant_pool_access(requester, pool) }

  def search_models(term)
    query(<<~GQL, requester.id, pool_id: pool.id)
      { models(term: "#{term}") { id name manufacturer } }
    GQL
  end

  it "returns models with an item in the pool matching the term" do
    match = create(:leihs_model, product: "Alpha Camera", manufacturer: "Canon")
    create(:item, leihs_model: match, inventory_pool: pool)

    result = search_models("Alpha")
    ids = result.dig(:data, :models).map { |m| m[:id] }
    expect(ids).to eq([match.id.to_s])
  end

  it "matches across manufacturer and product together" do
    match = create(:leihs_model, product: "Camera", manufacturer: "Canon")
    create(:item, leihs_model: match, inventory_pool: pool)

    result = search_models("Canon Camera")
    ids = result.dig(:data, :models).map { |m| m[:id] }
    expect(ids).to eq([match.id.to_s])
  end

  it "excludes models without an item in the pool" do
    create(:leihs_model, product: "Orphan Lens")

    result = search_models("Orphan")
    expect_graphql_result(result, {models: []})
  end

  it "excludes models with an item in a different pool" do
    other_pool = create(:inventory_pool)
    model = create(:leihs_model, product: "Elsewhere Tripod")
    create(:item, leihs_model: model, inventory_pool: other_pool)

    result = search_models("Elsewhere")
    expect_graphql_result(result, {models: []})
  end

  it "excludes retired items" do
    model = create(:leihs_model, product: "Retired Mic")
    create(:item, leihs_model: model, inventory_pool: pool, retired: Date.today)

    result = search_models("Retired")
    expect_graphql_result(result, {models: []})
  end

  it "excludes package child items" do
    parent = create(:item, inventory_pool: pool)
    model = create(:leihs_model, product: "Package Child")
    create(:item, leihs_model: model, inventory_pool: pool, parent_id: parent.id)

    result = search_models("Package")
    expect_graphql_result(result, {models: []})
  end

  it "returns all pool-accessible models when term is omitted" do
    model = create(:leihs_model)
    create(:item, leihs_model: model, inventory_pool: pool)

    result = query(<<~GQL, requester.id, pool_id: pool.id)
      { models { id } }
    GQL
    ids = result.dig(:data, :models).map { |m| m[:id] }
    expect(ids).to include(model.id.to_s)
  end
end
