require "active_support/all"
require "uuidtools"
require "pry"

PROJECT_DIR = Pathname.new(__dir__).join("..")
require PROJECT_DIR.join("database/spec/config/database")
require "config/factories"

RSpec.configure do |config|
  config.before(:example) do
    srand 1
    db_clean
    db_restore_data seeds_sql
  end
end
