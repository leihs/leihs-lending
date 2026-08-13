require "features_helper"
require_relative "../shared/common"

feature "Language ", type: :feature do
  scenario "init en works" do
    pool = FactoryBot.create(:inventory_pool)
    user = FactoryBot.create(:user, language_locale: "en-GB")

    FactoryBot.create(:access_right,
      inventory_pool: pool,
      user: user,
      role: :inventory_manager)

    sign_in(user)
    expect(page).to have_content("Leihs Lending")
  end

  scenario "init de works" do
    pool = FactoryBot.create(:inventory_pool)
    user = FactoryBot.create(:user, language_locale: "de-CH")

    FactoryBot.create(:access_right,
      inventory_pool: pool,
      user: user,
      role: :inventory_manager)

    sign_in(user)
    expect(page).to have_content("Leihs Verleih")
  end

  scenario "switching works" do
    pool = FactoryBot.create(:inventory_pool)
    user = FactoryBot.create(:user, language_locale: "en-GB")

    FactoryBot.create(:access_right,
      inventory_pool: pool,
      user: user,
      role: :inventory_manager)

    sign_in(user)
    expect(page).to have_content("Leihs Lending")
    click_on user.firstname
    click_on "Language"
    click_on "Deutsch"
    expect(page).to have_content("Leihs Verleih")
  end
end
