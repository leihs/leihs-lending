require "spec_helper"
require_relative "graphql_helper"

describe "contracts" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }

  before { grant_pool_access(user, pool) }

  def create_contract(state: "open", user: self.user, **attrs)
    Sequel::Model.db.transaction do
      db_with_disabled_triggers do
        contract = create(:contract, user: user, inventory_pool: pool, state: state, **attrs)
        create(:reservation,
          user: user,
          inventory_pool: pool,
          leihs_model: model,
          order: nil,
          contract_id: contract.id,
          status: (state == "closed") ? "closed" : "signed")
        contract
      end
    end
  end

  it "returns contracts for pool" do
    contract = create_contract
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { contracts { items { id state purpose } totalCount } }
    GQL
    expect_graphql_result(result, {
      contracts: {
        items: [{id: contract.id.to_s, state: "OPEN", purpose: contract.purpose}],
        totalCount: 1
      }
    })
  end

  it "filters by state" do
    create_contract(state: "open")
    closed = create_contract(state: "closed")
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { contracts(state: CLOSED) { items { id } totalCount } }
    GQL
    expect_graphql_result(result, {contracts: {items: [{id: closed.id.to_s}], totalCount: 1}})
  end

  it "filters by term" do
    create_contract(purpose: "special research grant")
    create_contract(purpose: "unrelated")
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { contracts(term: "research") { items { purpose } totalCount } }
    GQL
    expect_graphql_result(result, {contracts: {items: [{purpose: "special research grant"}], totalCount: 1}})
  end

  it "filters by startDate and endDate" do
    create_contract(created_at: Date.today - 30)
    recent = create_contract(created_at: Date.today - 1)
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { contracts(startDate: "#{Date.today - 5}", endDate: "#{Date.today}") { items { id } totalCount } }
    GQL
    expect_graphql_result(result, {contracts: {items: [{id: recent.id.to_s}], totalCount: 1}})
  end

  it "paginates" do
    3.times { create_contract }
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { contracts(page: 1, perPage: 2) { items { id } totalCount } }
    GQL
    expect(result[:errors]).to be_nil
    expect(result.dig(:data, :contracts, :items).size).to eq(2)
    expect(result.dig(:data, :contracts, :totalCount)).to eq(3)
  end

  it "orders by createdAt descending" do
    older = create_contract(created_at: Date.today - 2)
    newer = create_contract(created_at: Date.today - 1)
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { contracts { items { id } totalCount } }
    GQL
    expect_graphql_result(result, {
      contracts: {items: [{id: newer.id.to_s}, {id: older.id.to_s}], totalCount: 2}
    })
  end

  it "returns reservations on contract" do
    contract = create_contract
    reservation = Reservation.where(contract_id: contract.id).first
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { contracts { items { reservations { id model { id name } } } totalCount } }
    GQL
    expect_graphql_result(result, {
      contracts: {
        items: [{
          reservations: [{
            id: reservation.id.to_s,
            model: {id: model.id.to_s, name: model.product}
          }]
        }],
        totalCount: 1
      }
    })
  end

  it "filters by toBeVerified" do
    verify_user = create(:user)
    grant_pool_access(verify_user, pool)
    to_verify = create_contract(user: verify_user)
    create_contract

    eg = create(:entitlement_group, inventory_pool: pool, is_verification_required: true)
    create(:entitlement_groups_direct_user, user: verify_user, entitlement_group: eg)
    create(:entitlement, leihs_model: model, entitlement_group: eg)

    result = query(<<~GQL, user.id, pool_id: pool.id)
      { contracts(toBeVerified: true) { items { id toBeVerified } totalCount } }
    GQL
    expect_graphql_result(result, {
      contracts: {items: [{id: to_verify.id.to_s, toBeVerified: true}], totalCount: 1}
    })
  end

  it "returns user on contract" do
    create_contract
    result = query(<<~GQL, user.id, pool_id: pool.id)
      { contracts { items { user { id email } } totalCount } }
    GQL
    expect_graphql_result(result, {
      contracts: {items: [{user: {id: user.id.to_s, email: user.email}}], totalCount: 1}
    })
  end
end
