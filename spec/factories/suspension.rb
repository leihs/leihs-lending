class Suspension < Sequel::Model(:suspensions)
  many_to_one :user
  many_to_one :inventory_pool
end

FactoryBot.define do
  factory :suspension do
    user
    inventory_pool
    suspended_until { Date.today + 10000 }
    suspended_reason { "suspended" }
  end
end
