class Holiday < Sequel::Model
  many_to_one :inventory_pool
end

FactoryBot.define do
  factory :holiday do
    inventory_pool
    name { Faker::Lorem.word }
    start_date { Date.today.next_occurring(:tuesday).to_s }
    end_date { Date.today.next_occurring(:wednesday).to_s }
  end
end
