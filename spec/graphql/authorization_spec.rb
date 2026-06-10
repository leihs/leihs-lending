require "spec_helper"
require_relative "graphql_helper"

describe "pool-scoped authorization" do
  let(:pool) { create(:inventory_pool) }
  let(:other_pool) { create(:inventory_pool) }
  let(:user) { create(:user) }

  def grant(user, pool, role)
    database[:direct_access_rights].insert(
      id: SecureRandom.uuid,
      user_id: user.id,
      inventory_pool_id: pool.id,
      role: role
    )
  end

  def graphql_request(user_id, pool_id)
    token = user_id ? create_session_for(user_id) : nil
    csrf = SecureRandom.uuid
    Faraday.post("#{LEIHS_LENDING_HTTP_BASE_URL}/lending/#{pool_id}/graphql") do |req|
      req.headers["Content-Type"] = "application/json"
      req.headers["x-csrf-token"] = csrf
      cookies = {ANTI_CSRF_COOKIE_NAME => csrf}
      cookies[SESSION_COOKIE_NAME] = token if token
      req.headers["Cookie"] = cookies.map { |k, v| "#{k}=#{v}" }.join("; ")
      req.body = {query: "{ currentUser { id } }"}.to_json
    end
  end

  def graphiql_request(user_id, pool_id)
    token = user_id ? create_session_for(user_id) : nil
    csrf = SecureRandom.uuid
    Faraday.get("#{LEIHS_LENDING_HTTP_BASE_URL}/lending/#{pool_id}/graphiql") do |req|
      req.headers["x-csrf-token"] = csrf
      cookies = {ANTI_CSRF_COOKIE_NAME => csrf}
      cookies[SESSION_COOKIE_NAME] = token if token
      req.headers["Cookie"] = cookies.map { |k, v| "#{k}=#{v}" }.join("; ")
    end
  end

  context "/graphql route" do
    it "returns 401 when unauthenticated" do
      resp = graphql_request(nil, pool.id)
      expect(resp.status).to eq(401)
    end

    it "returns 403 with GraphQL error body when unauthorized" do
      resp = graphql_request(user.id, pool.id)
      expect(resp.status).to eq(403)
      body = JSON.parse(resp.body)
      expect(body.dig("errors", 0, "extensions", "code")).to eq("FORBIDDEN")
    end

    it "allows lending_manager" do
      grant(user, pool, "lending_manager")
      resp = graphql_request(user.id, pool.id)
      expect(resp.status).to eq(200)
    end

    it "allows inventory_manager" do
      grant(user, pool, "inventory_manager")
      resp = graphql_request(user.id, pool.id)
      expect(resp.status).to eq(200)
    end

    it "returns 403 when customer role" do
      grant(user, pool, "customer")
      resp = graphql_request(user.id, pool.id)
      expect(resp.status).to eq(403)
    end

    it "returns 403 when manager in different pool" do
      grant(user, other_pool, "lending_manager")
      resp = graphql_request(user.id, pool.id)
      expect(resp.status).to eq(403)
    end
  end

  context "/graphiql route" do
    it "returns 403 with plain text body when unauthorized" do
      resp = graphiql_request(user.id, pool.id)
      expect(resp.status).to eq(403)
      expect(resp.headers["content-type"]).to include("text/plain")
      expect(resp.body).to eq("Forbidden")
    end

    it "allows lending_manager" do
      grant(user, pool, "lending_manager")
      resp = graphiql_request(user.id, pool.id)
      expect(resp.status).to eq(200)
    end
  end
end
