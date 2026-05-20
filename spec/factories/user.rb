require "bcrypt"

class User < Sequel::Model; end
class Language < Sequel::Model; end

def create_user_with_password(password: "password", **attrs)
  user = FactoryBot.create(:user, **attrs)
  hash = BCrypt::Password.create(password).to_s
  database[:authentication_systems_users].insert(
    user_id: user.id,
    authentication_system_id: "password",
    data: hash
  )
  database[:users].where(id: user.id).update(password_sign_in_enabled: true)
  user
end

FactoryBot.define do
  factory :user do
    created_at { Date.today }
    updated_at { Date.today }
    login { Faker::Internet.username }
    email { Faker::Internet.email }
    firstname { Faker::Name.first_name }
    lastname { Faker::Name.last_name }
    organization { Faker::Lorem.characters(number: 8) }
  end
end
