class Option < Sequel::Model(:options)
  many_to_one :inventory_pool
end

FactoryBot.define do
  factory :option do
    inventory_pool
    product { Faker::Commerce.product_name }
  end
end
