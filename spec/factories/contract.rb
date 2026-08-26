class Contract < Sequel::Model(:contracts)
  many_to_one :user
  many_to_one :inventory_pool
end

FactoryBot.define do
  factory :contract do
    user
    inventory_pool
    sequence(:compact_id) { |n| "C#{n}" }
    state { "open" }
    purpose { Faker::Lorem.sentence }
    created_at { Time.now }
    updated_at { Time.now }
  end
end
