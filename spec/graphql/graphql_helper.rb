require "faraday"
require "digest"

LEIHS_LENDING_HTTP_BASE_URL = ENV["LEIHS_LENDING_HTTP_BASE_URL"].presence || "http://localhost:3270"

SESSION_COOKIE_NAME = "leihs-user-session"
ANTI_CSRF_COOKIE_NAME = "leihs-anti-csrf-token"

def create_session_for(user_id)
  token = SecureRandom.uuid
  token_hash = Digest::SHA256.hexdigest(token)
  database[:user_sessions].insert(
    user_id: user_id,
    token_hash: token_hash,
    authentication_system_id: "password"
  )
  token
end

class GraphqlQuery
  attr_reader :response

  def initialize(query, user_id, variables, pool_id)
    @query = query
    @variables = variables
    @pool_id = pool_id
    @session_token = user_id ? create_session_for(user_id) : nil
    @csrf_token = SecureRandom.uuid
  end

  def perform
    @response = Faraday.post("#{LEIHS_LENDING_HTTP_BASE_URL}/lending/#{@pool_id}/graphql") do |req|
      req.headers["Accept"] = "application/json"
      req.headers["Content-Type"] = "application/json"
      req.headers["x-csrf-token"] = @csrf_token
      cookies = {ANTI_CSRF_COOKIE_NAME.to_s => @csrf_token}
      cookies[SESSION_COOKIE_NAME] = @session_token if @session_token
      req.headers["Cookie"] = cookies.map { |k, v| "#{k}=#{v}" }.join("; ")
      req.body = {query: @query, variables: @variables}.to_json
    end
    log_errors if @response.status == 200
    self
  end

  def result
    JSON.parse @response.body
  end

  def log_errors
    if result["errors"]
      puts "\n=== GRAPHQL ERROR ===\n#{result.slice("errors")}\n===================\n"
    end
  end
end

def grant_pool_access(user, pool, role: "lending_manager")
  database[:direct_access_rights].insert(
    id: SecureRandom.uuid,
    user_id: user.id,
    inventory_pool_id: pool.id,
    role: role
  )
end

RSpec.shared_context "graphql client" do
  def query(q, user_id = nil, pool_id: nil, variables: {})
    gq = GraphqlQuery.new(q, user_id, variables, pool_id).perform
    return {status: gq.response.status} unless gq.response.status == 200
    gq.result.deep_symbolize_keys
  end

  def expect_graphql_result(result, compared)
    expect(result[:errors]).to be_nil
    expect(result[:data]).to eq(compared)
  end

  def expect_graphql_error(result, status: nil)
    expect(result[:errors]).not_to be_empty
    if status
      codes = result[:errors].map { |e| e.dig(:extensions, :code) || e.dig(:extensions, :status) }
      expect(codes).to include(status)
    end
    yield if block_given?
  end
end

RSpec.configure do |config|
  config.include_context "graphql client"
end
