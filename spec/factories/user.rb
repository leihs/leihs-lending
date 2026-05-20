class User < Sequel::Model; end
class Language < Sequel::Model; end

FactoryBot.define do
  factory :user do
    created_at { Date.today }
    updated_at { Date.today }
    email { Faker::Internet.email }
    firstname { Faker::Name.first_name }
    lastname { Faker::Name.last_name }
    organization { Faker::Lorem.characters(number: 8) }
  end
end
