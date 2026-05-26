class CustomerOrder < Sequel::Model
  many_to_one :user
end

FactoryBot.define do
  factory :customer_order do
    user
    purpose { Faker::Lorem.sentence }
    title { purpose }
  end
end
