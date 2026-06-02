class Building < Sequel::Model; end

class Room < Sequel::Model
  many_to_one :building
end

FactoryBot.define do
  factory :building do
    name { Faker::Address.street_address }
  end

  factory :room do
    name { Faker::Lorem.characters(number: 8) }
    building
    general { false }
  end
end
