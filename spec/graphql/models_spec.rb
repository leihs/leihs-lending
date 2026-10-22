require "spec_helper"
require_relative "graphql_helper"

describe "models" do
  let(:requester) { create(:user) }
  let(:pool) { create(:inventory_pool) }

  before { grant_pool_access(requester, pool) }

  def search_models(term, category_id: nil)
    category_arg = category_id ? %(, categoryId: "#{category_id}") : ""
    query(<<~GQL, requester.id, pool_id: pool.id)
      { models(term: "#{term}"#{category_arg}) { id name manufacturer } }
    GQL
  end

  def model_with_item(product)
    create(:leihs_model, product: product).tap do |model|
      create(:item, leihs_model: model, inventory_pool: pool)
    end
  end

  def link(category, model)
    ModelLink.create(model_group_id: category.id, model_id: model.id)
  end

  def nest(parent, child)
    ModelGroupLink.create(parent_id: parent.id, child_id: child.id)
  end

  def model_ids(result)
    result.dig(:data, :models).map { |m| m[:id] }
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

  it "orders by name" do
    b = model_with_item("Bravo")
    a = model_with_item("Alpha")

    expect(model_ids(search_models(""))).to eq([a.id, b.id].map(&:to_s))
  end

  describe "categoryId" do
    let(:root) { create(:category) }
    let(:child) { create(:category) }
    let(:grandchild) { create(:category) }

    before do
      nest(root, child)
      nest(child, grandchild)
    end

    it "includes models of the category itself" do
      model = model_with_item("Direct")
      link(root, model)

      expect(model_ids(search_models("", category_id: root.id))).to eq([model.id.to_s])
    end

    it "includes models of descendant categories" do
      model = model_with_item("Deep")
      link(grandchild, model)

      expect(model_ids(search_models("", category_id: root.id))).to eq([model.id.to_s])
    end

    it "excludes models of ancestor and unrelated categories" do
      link(root, model_with_item("Ancestor"))
      link(create(:category), model_with_item("Unrelated"))

      expect(model_ids(search_models("", category_id: child.id))).to eq([])
    end

    it "returns a model linked to several matching categories once" do
      model = model_with_item("Twice")
      link(root, model)
      link(grandchild, model)

      expect(model_ids(search_models("", category_id: root.id))).to eq([model.id.to_s])
    end

    it "terminates on cyclic links" do
      nest(grandchild, root)
      model = model_with_item("Cycle")
      link(child, model)

      expect(model_ids(search_models("", category_id: root.id))).to eq([model.id.to_s])
    end

    it "combines with term" do
      match = model_with_item("Alpha Cam")
      link(root, match)
      link(root, model_with_item("Bravo Cam"))
      model_with_item("Alpha Outside")

      expect(model_ids(search_models("Alpha", category_id: root.id))).to eq([match.id.to_s])
    end

    it "returns nothing for an unknown category" do
      model_with_item("Any")

      expect(model_ids(search_models("", category_id: SecureRandom.uuid))).to eq([])
    end
  end

  describe "model(id)" do
    def fetch_model(id)
      query(<<~GQL, requester.id, pool_id: pool.id)
        { model(id: "#{id}") { id name manufacturer } }
      GQL
    end

    it "returns the model regardless of pool" do
      model = create(:leihs_model, product: "Lone Lens", manufacturer: "Zeiss")

      expect_graphql_result(fetch_model(model.id),
        {model: {id: model.id.to_s, name: "Lone Lens", manufacturer: "Zeiss"}})
    end

    it "fails with 404 for an unknown id" do
      expect_graphql_error(fetch_model(SecureRandom.uuid), status: 404)
    end
  end
end
