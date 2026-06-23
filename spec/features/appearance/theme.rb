require "features_helper"
require_relative "../shared/common"

feature "Theme ", type: :feature do
  let(:pool) { FactoryBot.create(:inventory_pool) }
  let(:user) { FactoryBot.create(:user, language_locale: "en-GB") }

  before(:each) do
    FactoryBot.create(:access_right,
      inventory_pool: pool,
      user: user,
      role: :inventory_manager)

    login(user)
    visit "/inventory/#{pool.id}/"
  end

  # Helper methods
  def get_theme_from_storage
    page.evaluate_script("localStorage.getItem('leihs-inventory-theme')")
  end

  def has_theme_class?(theme)
    page.evaluate_script("document.documentElement.classList.contains('#{theme}')")
  end

  def open_theme_menu
    # Click on username to open user dropdown
    click_on "#{user.firstname} #{user.lastname}"
    # Click on "Appearance" to open theme submenu
    click_on "Appearance"
    expect(page).to have_content("Light")
    expect(page).to have_content("Dark")
    expect(page).to have_content("System")
  end

  scenario "toggle between dark and light themes" do
    # Verify page loads
    expect(page).to have_content("Inventory List", wait: 10)

    # Open theme menu
    open_theme_menu

    # Select Dark theme
    click_on "Dark"

    # Verify localStorage is set to "dark"
    expect(get_theme_from_storage).to eq("dark")

    # Verify document has "dark" class
    expect(has_theme_class?("dark")).to be true

    # Verify document does NOT have "light" class
    expect(has_theme_class?("light")).to be false

    # Re-open theme menu for light theme
    open_theme_menu

    # Select Light theme
    click_on "Light"

    # Verify localStorage is set to "light"
    expect(get_theme_from_storage).to eq("light")

    # Verify document has "light" class
    expect(has_theme_class?("light")).to be true

    # Verify document does NOT have "dark" class
    expect(has_theme_class?("dark")).to be false
  end

  scenario "system theme preference" do
    expect(page).to have_content("Inventory List", wait: 10)

    # Open theme menu
    open_theme_menu

    # Select System theme
    click_on "System"

    # Verify localStorage is set to "system"
    expect(get_theme_from_storage).to eq("system")

    # Verify that either "dark" or "light" class is applied
    # (based on system preference, which we can't control in test)
    has_theme_class = page.evaluate_script(
      "document.documentElement.classList.contains('dark') || " \
      "document.documentElement.classList.contains('light')"
    )
    expect(has_theme_class).to be true
  end

  scenario "theme persists after page reload" do
    expect(page).to have_content("Inventory List", wait: 10)

    # Set dark theme
    open_theme_menu
    click_on "Dark"
    expect(get_theme_from_storage).to eq("dark")

    # Reload the page
    visit "/inventory/#{pool.id}/"
    expect(page).to have_content("Inventory List", wait: 10)

    # Verify theme persisted
    expect(get_theme_from_storage).to eq("dark")
    expect(has_theme_class?("dark")).to be true
  end
end
