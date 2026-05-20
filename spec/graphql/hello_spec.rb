require "spec_helper"
require_relative "graphql_helper"

describe "hello" do
  let(:user) { create(:user) }

  it "returns 401 without session" do
    result = query("{ hello }")
    expect(result[:status]).to eq(401)
  end

  it "returns hello with valid session" do
    result = query("{ hello }", user.id)
    expect_graphql_result(result, {hello: "Hello from lending!"})
  end
end
