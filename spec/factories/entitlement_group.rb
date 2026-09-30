class EntitlementGroup < Sequel::Model(:entitlement_groups)
  many_to_one :inventory_pool
end

FactoryBot.define do
  factory :entitlement_group do
    inventory_pool
    name { Faker::Lorem.word }
    is_verification_required { false }
    created_at { Time.now }
    updated_at { Time.now }
  end
end
