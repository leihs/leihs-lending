require "spec_helper"
require_relative "graphql_helper"

describe "users" do
  let(:requester) { create(:user) }
  let(:pool) { create(:inventory_pool) }

  before { grant_pool_access(requester, pool) }

  def search_users(term, user_id)
    query(<<~GQL, user_id, pool_id: pool.id)
      { users(term: "#{term}") { id firstname lastname login badgeId } }
    GQL
  end

  it "returns users with access to the pool matching the term" do
    match = create(:user, firstname: "Alice", lastname: "Anders")
    grant_pool_access(match, pool)
    result = search_users("Alice", requester.id)
    expect_graphql_result(result, {
      users: [{
        id: match.id.to_s,
        firstname: "Alice",
        lastname: "Anders",
        login: match.login,
        badgeId: match.badge_id
      }]
    })
  end

  it "matches by lastname, login or badgeId" do
    lastname_match = create(:user, lastname: "Zephyr")
    grant_pool_access(lastname_match, pool)
    login_match = create(:user, login: "zephyr-login")
    grant_pool_access(login_match, pool)
    badge_match = create(:user, badge_id: "ZEPHYR-1")
    grant_pool_access(badge_match, pool)

    result = search_users("zephyr", requester.id)
    ids = result.dig(:data, :users).map { |u| u[:id] }
    expect(ids).to contain_exactly(
      lastname_match.id.to_s, login_match.id.to_s, badge_match.id.to_s
    )
  end

  it "excludes users without access to the pool" do
    create(:user, firstname: "NoAccess")
    result = search_users("NoAccess", requester.id)
    expect_graphql_result(result, {users: []})
  end

  it "excludes users with access to a different pool" do
    other_pool = create(:inventory_pool)
    other_pool_user = create(:user, firstname: "Elsewhere")
    grant_pool_access(other_pool_user, other_pool)
    result = search_users("Elsewhere", requester.id)
    expect_graphql_result(result, {users: []})
  end

  it "returns delegatorUser for a delegation account" do
    delegator = create(:user)
    delegation = create(:user, firstname: "Delegation", delegator_user_id: delegator.id)
    grant_pool_access(delegation, pool)

    result = query(<<~GQL, requester.id, pool_id: pool.id)
      { users(term: "Delegation") { id delegatorUser { id firstname lastname } } }
    GQL
    expect_graphql_result(result, {
      users: [{
        id: delegation.id.to_s,
        delegatorUser: {
          id: delegator.id.to_s,
          firstname: delegator.firstname,
          lastname: delegator.lastname
        }
      }]
    })
  end

  it "returns all pool-accessible users when term is omitted" do
    grant_pool_access(create(:user), pool)
    result = query(<<~GQL, requester.id, pool_id: pool.id)
      { users { id } }
    GQL
    ids = result.dig(:data, :users).map { |u| u[:id] }
    expect(ids).to include(requester.id.to_s)
  end
end
