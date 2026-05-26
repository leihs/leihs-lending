class InventoryPool < Sequel::Model(:inventory_pools); end

FactoryBot.define do
  factory :inventory_pool do
    name { Faker::Company.name }
    email { Faker::Internet.email }
    shortname { name.split(" ").map(&:first).join }
  end
end
