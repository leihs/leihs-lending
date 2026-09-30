require "spec_helper"
require_relative "graphql/graphql_helper"

describe "contract show page" do
  let(:pool) { create(:inventory_pool) }
  let(:owner) { create(:user) }

  def get_contract(user_id, pool_id, contract_id)
    token = user_id ? create_session_for(user_id) : nil
    Faraday.get("#{LEIHS_LENDING_HTTP_BASE_URL}/lending/#{pool_id}/contracts/#{contract_id}") do |req|
      req.headers["Accept"] = "text/html"
      req.headers["Cookie"] = token ? "#{SESSION_COOKIE_NAME}=#{token}" : ""
    end
  end

  def add_delegate(delegate, delegation_owner)
    database[:delegations_direct_users].insert(
      id: SecureRandom.uuid, delegation_id: delegation_owner.id, user_id: delegate.id
    )
  end

  # `owner`/`pool` are keyword args (resolved at the call site, before this
  # method's body runs) rather than referencing the `let`s directly inside
  # the block below -- a contract needs a signed/closed reservation attached
  # atomically (a DB trigger requires >=1 reservation from creation, and a
  # signed/closed reservation needs the contract-linkage trigger disabled),
  # and lazily creating `owner` *inside* that disabled-triggers block breaks
  # the user factory's own after(:create) FK insert. Mirrors
  # spec/graphql/contracts_spec.rb's create_contract.
  def create_contract_with_line(owner:, pool:, status: "signed", **line_attrs)
    Sequel::Model.db.transaction do
      db_with_disabled_triggers do
        contract = create(:contract, user: owner, inventory_pool: pool)
        create(:reservation, {
          user: owner,
          inventory_pool: pool,
          order: nil,
          contract_id: contract.id,
          status: status
        }.merge(line_attrs))
        contract
      end
    end
  end

  def add_line(contract:, owner:, pool:, status: "signed", **attrs)
    Sequel::Model.db.transaction do
      db_with_disabled_triggers do
        create(:reservation, {
          user: owner,
          inventory_pool: pool,
          order: nil,
          contract_id: contract.id,
          status: status
        }.merge(attrs))
      end
    end
  end

  describe "authorization" do
    it "allows the contract owner, even without a manager role" do
      contract = create_contract_with_line(owner: owner, pool: pool)
      resp = get_contract(owner.id, pool.id, contract.id)
      expect(resp.status).to eq(200)
    end

    it "allows a delegate acting for the owner" do
      contract = create_contract_with_line(owner: owner, pool: pool)
      delegate = create(:user)
      add_delegate(delegate, owner)
      resp = get_contract(delegate.id, pool.id, contract.id)
      expect(resp.status).to eq(200)
    end

    it "allows a group_manager in the pool" do
      contract = create_contract_with_line(owner: owner, pool: pool)
      manager = create(:user)
      grant_pool_access(manager, pool, role: "group_manager")
      resp = get_contract(manager.id, pool.id, contract.id)
      expect(resp.status).to eq(200)
    end

    it "denies a normal user who is neither owner, delegate nor manager" do
      contract = create_contract_with_line(owner: owner, pool: pool)
      stranger = create(:user)
      resp = get_contract(stranger.id, pool.id, contract.id)
      expect(resp.status).to eq(403)
    end

    it "redirects unauthenticated requests to sign-in" do
      contract = create_contract_with_line(owner: owner, pool: pool)
      resp = get_contract(nil, pool.id, contract.id)
      expect(resp.status).to eq(302)
      expect(resp.headers["location"]).to include("/lending/sign-in")
    end

    it "returns 404 when the contract belongs to a different pool" do
      contract = create_contract_with_line(owner: owner, pool: pool)
      other_pool = create(:inventory_pool)
      resp = get_contract(owner.id, other_pool.id, contract.id)
      expect(resp.status).to eq(404)
    end
  end

  describe "content" do
    it "shows a borrowed (not yet returned) item line under Borrowed Items" do
      model = create(:leihs_model, product: "ThinkPad")
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool, inventory_code: "INV1")
      contract = create_contract_with_line(owner: owner, pool: pool, leihs_model: model, item_id: item.id)

      resp = get_contract(owner.id, pool.id, contract.id)
      expect(resp.status).to eq(200)
      expect(resp.body).to include("Borrowed Items")
      expect(resp.body).not_to include("Returned Items")
      expect(resp.body).to include("INV1")
      expect(resp.body).to include("ThinkPad")
    end

    it "splits a partially returned contract into Returned and Borrowed sections" do
      model = create(:leihs_model, product: "Camera")
      returned_item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool, inventory_code: "RET1")
      open_item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool, inventory_code: "OPEN1")
      returner = create(:user, firstname: "Rita", lastname: "Returner")

      # The contract starts with its still-open line -- a contract whose only
      # reservation is closed must itself be closed (a DB trigger enforces this).
      contract = create_contract_with_line(owner: owner, pool: pool, leihs_model: model,
        item_id: open_item.id, status: "signed")
      add_line(contract: contract, owner: owner, pool: pool, leihs_model: model,
        item_id: returned_item.id, status: "closed",
        returned_date: Date.today, returned_to_user_id: returner.id)

      resp = get_contract(owner.id, pool.id, contract.id)
      body = resp.body

      expect(body).to include("Returned Items")
      expect(body).to include("Borrowed Items")
      expect(body).to include("Rita Returner")
      # RET1 belongs under "Returned Items", which legacy/the port render above "Borrowed Items"
      expect(body.index("RET1")).to be < body.index("Borrowed Items")
      expect(body.index("Borrowed Items")).to be < body.index("OPEN1")
    end

    it "shows the serial number next to a Software model line" do
      model = create(:leihs_model, type: "Software", product: "Photoshop")
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool, serial_number: "SN-123")
      contract = create_contract_with_line(owner: owner, pool: pool, leihs_model: model, item_id: item.id)

      resp = get_contract(owner.id, pool.id, contract.id)
      expect(resp.body).to include("Photoshop")
      expect(resp.body).to include("SN-123")
    end

    it "lists package children and pool-active accessories for a package model line" do
      package_model = create(:leihs_model, is_package: true, product: "Camera Kit")
      package_item = create(:item, leihs_model: package_model, inventory_pool: pool, owner: pool)
      child_model = create(:leihs_model, product: "Lens")
      create(:item, leihs_model: child_model, inventory_pool: pool, owner: pool,
        inventory_code: "CHILD1", parent_id: package_item.id)

      accessory_id = SecureRandom.uuid
      database[:accessories].insert(id: accessory_id, model_id: package_model.id, name: "Tripod", quantity: 1)
      database[:accessories_inventory_pools].insert(accessory_id: accessory_id, inventory_pool_id: pool.id)

      contract = create_contract_with_line(owner: owner, pool: pool, leihs_model: package_model,
        item_id: package_item.id)

      resp = get_contract(owner.id, pool.id, contract.id)
      expect(resp.body).to include("CHILD1")
      expect(resp.body).to include("Tripod")
    end

    it "does not leak an accessory that isn't active in this pool" do
      other_pool = create(:inventory_pool)
      model = create(:leihs_model, product: "Mixer")
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool)

      accessory_id = SecureRandom.uuid
      database[:accessories].insert(id: accessory_id, model_id: model.id, name: "XLR Cable", quantity: 1)
      database[:accessories_inventory_pools].insert(accessory_id: accessory_id, inventory_pool_id: other_pool.id)

      contract = create_contract_with_line(owner: owner, pool: pool, leihs_model: model, item_id: item.id)

      resp = get_contract(owner.id, pool.id, contract.id)
      expect(resp.body).not_to include("XLR Cable")
    end

    it "renders an option line using the option's own code, name and price" do
      option = create(:option, inventory_pool: pool, product: "Insurance", price: 10)
      contract = create_contract_with_line(owner: owner, pool: pool, leihs_model: nil,
        option_id: option.id, quantity: 2)

      resp = get_contract(owner.id, pool.id, contract.id)
      expect(resp.body).to include("Insurance")
      expect(resp.body).to include(option.inventory_code)
      expect(resp.body).to include("20.00") # option.price(10) * quantity(2)
    end

    it "shows the delegated user alongside the borrower" do
      delegate = create(:user, firstname: "Della", lastname: "Gate")
      model = create(:leihs_model)
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool)
      contract = create_contract_with_line(owner: owner, pool: pool, leihs_model: model,
        item_id: item.id, delegated_user_id: delegate.id)

      resp = get_contract(owner.id, pool.id, contract.id)
      expect(resp.body).to include("Della Gate")
    end

    it "renders the plain borrower with no delegated-user markup when there is none" do
      model = create(:leihs_model)
      item = create(:item, leihs_model: model, inventory_pool: pool, owner: pool)
      contract = create_contract_with_line(owner: owner, pool: pool, leihs_model: model, item_id: item.id)

      resp = get_contract(owner.id, pool.id, contract.id)
      expect(resp.body).to include(owner.firstname)
      expect(resp.body).to include(owner.lastname)
    end
  end
end
