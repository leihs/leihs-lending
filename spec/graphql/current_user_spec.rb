require "spec_helper"
require_relative "graphql_helper"

describe "currentUser" do
  let(:user) { create(:user) }

  it "returns id and user fields" do
    result = query(<<~GRAPHQL, user.id)
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
