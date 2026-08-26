class Entitlement < Sequel::Model(:entitlements)
  many_to_one :leihs_model, key: :model_id
  many_to_one :entitlement_group
end

FactoryBot.define do
  factory :entitlement do
    leihs_model
    entitlement_group
    quantity { 1 }
  end
end
