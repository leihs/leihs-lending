class EntitlementGroupsDirectUser < Sequel::Model(:entitlement_groups_direct_users)
  many_to_one :user
  many_to_one :entitlement_group
end

FactoryBot.define do
  factory :entitlement_groups_direct_user do
    user
    entitlement_group
  end
end
