require "spec_helper"
require_relative "graphql_helper"

describe "user" do
  let(:requester) { create(:user) }
  let(:pool) { create(:inventory_pool) }
  let(:user) do
    create(:user,
      phone: "555-1234",
      badge_id: "BADGE42",
      address: "Main St 1",
      zip: "12345",
      city: "Testville")
  end

  let(:user_fields) do
    "id email firstname lastname login " \
    "phone badgeId address zip city img256Url " \
    "isSuspended suspendedReason"
  end

  it "returns user fields" do
    result = query(<<~GQL, requester.id, pool_id: pool.id)
      { user(id: "#{user.id}") { #{user_fields} } }
    GQL
    expect_graphql_result(result, {
      user: {
        id: user.id.to_s,
        email: user.email,
        firstname: user.firstname,
        lastname: user.lastname,
        login: user.login,
        phone: user.phone,
        badgeId: user.badge_id,
        address: user.address,
        zip: user.zip,
        city: user.city,
        img256Url: user.img256_url,
        isSuspended: false,
        suspendedReason: nil
      }
    })
  end

  it "returns isSuspended true with active suspension in pool" do
    create(:suspension, user: user, inventory_pool: pool, suspended_reason: "overdue")
    result = query(<<~GQL, requester.id, pool_id: pool.id)
      { user(id: "#{user.id}") { isSuspended suspendedReason } }
    GQL
    expect_graphql_result(result, {
      user: {isSuspended: true, suspendedReason: "overdue"}
    })
  end

  it "returns isSuspended false when suspended in different pool" do
    other_pool = create(:inventory_pool)
    create(:suspension, user: user, inventory_pool: other_pool)
    result = query(<<~GQL, requester.id, pool_id: pool.id)
      { user(id: "#{user.id}") { isSuspended } }
    GQL
    expect_graphql_result(result, {user: {isSuspended: false}})
  end

  it "returns delegatorUser for delegation accounts" do
    delegator = create(:user)
    delegation = create(:user, delegator_user_id: delegator.id)
    result = query(<<~GQL, requester.id, pool_id: pool.id)
      { user(id: "#{delegation.id}") {
          delegatorUser { id firstname lastname }
        }
      }
    GQL
    expect_graphql_result(result, {
      user: {
        delegatorUser: {
          id: delegator.id.to_s,
          firstname: delegator.firstname,
          lastname: delegator.lastname
        }
      }
    })
  end

  it "returns delegatorUser nil for regular users" do
    result = query(<<~GQL, requester.id, pool_id: pool.id)
      { user(id: "#{user.id}") { delegatorUser { id } } }
    GQL
    expect_graphql_result(result, {user: {delegatorUser: nil}})
  end
end
