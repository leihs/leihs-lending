class Reservation < Sequel::Model
  many_to_one :user
  many_to_one :inventory_pool
  many_to_one :order
  many_to_one :leihs_model, key: :model_id
  many_to_one :delegated_user, class: :User
end

FactoryBot.define do
  factory :reservation do
    user
    inventory_pool
    leihs_model
    order
    status { "submitted" }
    start_date { Date.today.next_occurring(:tuesday).to_s }
    end_date { Date.today.next_occurring(:wednesday).to_s }
    created_at { Time.now }
    updated_at { Time.now }
  end
end
