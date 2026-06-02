class Holiday < Sequel::Model
  many_to_one :inventory_pool
end

FactoryBot.define do
  factory :holiday do
    inventory_pool
    name { Faker::Lorem.word }
    start_date { Date.tomorrow.to_s }
    end_date { (Date.tomorrow + 1).to_s }
  end
end
