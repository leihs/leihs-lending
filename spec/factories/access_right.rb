class DirectAccessRight < Sequel::Model
  many_to_one(:user)
  many_to_one(:inventory_pool)
end

FactoryBot.define do
  factory :access_right, class: DirectAccessRight do
    inventory_pool
    user
    role { :inventory_manager }
  end
end
