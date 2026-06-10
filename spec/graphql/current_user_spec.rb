require "spec_helper"
require_relative "graphql_helper"

describe "currentUser" do
  let(:user) { create(:user) }
  let(:pool) { create(:inventory_pool) }

  before { grant_pool_access(user, pool) }

  it "returns id and user fields" do
    result = query(<<~GRAPHQL, user.id, pool_id: pool.id)
      { currentUser { id user { id email firstname lastname } } }
    GRAPHQL

    expect_graphql_result(result, {
      currentUser: {
        id: user.id.to_s,
        user: {
          id: user.id.to_s,
          email: user.email,
          firstname: user.firstname,
          lastname: user.lastname
        }
      }
    })
  end
end
