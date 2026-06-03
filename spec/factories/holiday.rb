class Holiday < Sequel::Model
  many_to_one :inventory_pool
end

FactoryBot.define do
  factory :holiday do
    inventory_pool
    name { Faker::Lorem.word }
    start_date { Date.today.next_week(:monday).to_s }
    end_date { (Date.today.next_week(:monday) + 1).to_s }
  end
end
