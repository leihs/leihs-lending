class Category < Sequel::Model(:model_groups); end
class ModelGroupLink < Sequel::Model(:model_group_links); end
class ModelLink < Sequel::Model(:model_links); end

FactoryBot.define do
  factory :category do
    type { "Category" }
    name { Faker::Commerce.department }
    created_at { DateTime.now }
    updated_at { DateTime.now }
  end
end
