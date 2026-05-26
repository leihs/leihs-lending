class Order < Sequel::Model(:orders)
  many_to_one :user
  many_to_one :inventory_pool
  many_to_one :customer_order
end

FactoryBot.define do
  factory :order do
    user
    inventory_pool
    customer_order { create(:customer_order, user: user) }
    state { "submitted" }
    purpose { Faker::Lorem.sentence }
  end
end
