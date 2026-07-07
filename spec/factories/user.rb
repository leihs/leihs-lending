require "bcrypt"

class User < Sequel::Model
  attr_accessor :password
end

class Language < Sequel::Model; end

FactoryBot.define do
  factory :user do
    created_at { Date.today }
    updated_at { Date.today }
    login { Faker::Internet.username }
    email { Faker::Internet.email }
    firstname { Faker::Name.first_name }
    lastname { Faker::Name.last_name }
    organization { Faker::Lorem.characters(number: 8) }
    password { "password" }

    after(:create) do |user|
      hash = BCrypt::Password.create(user.password).to_s
      database[:authentication_systems_users].insert(
        user_id: user.id,
        authentication_system_id: "password",
        data: hash
      )
      database[:users].where(id: user.id).update(password_sign_in_enabled: true)
    end
  end
end
