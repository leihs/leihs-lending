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

  it "returns delegatorUser for a delegation account" do
    delegator = create(:user)
    delegation = create(:user, delegator_user_id: delegator.id)
    grant_pool_access(delegation, pool)

    result = query(<<~GRAPHQL, delegation.id, pool_id: pool.id)
      { currentUser { user { delegatorUser { id firstname lastname } } } }
    GRAPHQL

    expect_graphql_result(result, {
      currentUser: {
        user: {
          delegatorUser: {
            id: delegator.id.to_s,
            firstname: delegator.firstname,
            lastname: delegator.lastname
          }
        }
      }
    })
  end

  describe "availablePools" do
    let(:pool2) { create(:inventory_pool) }

    let(:available_pools_query) { "{ currentUser { availablePools { id name } } }" }

    context "lending_manager via direct access right" do
      before { grant_pool_access(user, pool2, role: "lending_manager") }

      it "returns the pool" do
        result = query(available_pools_query, user.id)
        expect(result.dig(:data, :currentUser, :availablePools))
          .to include(id: pool2.id.to_s, name: pool2.name)
      end
    end

    context "group_manager via direct access right" do
      before { grant_pool_access(user, pool2, role: "group_manager") }

      it "returns the pool" do
        result = query(available_pools_query, user.id)
        expect(result.dig(:data, :currentUser, :availablePools))
          .to include(id: pool2.id.to_s, name: pool2.name)
      end
    end

    context "inventory_manager via direct access right" do
      before { grant_pool_access(user, pool2, role: "inventory_manager") }

      it "returns the pool" do
        result = query(available_pools_query, user.id)
        expect(result.dig(:data, :currentUser, :availablePools))
          .to include(id: pool2.id.to_s, name: pool2.name)
      end
    end
  end
end
