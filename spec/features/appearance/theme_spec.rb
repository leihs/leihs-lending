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

    sign_in(user)
    click_on pool.name
  end

  # Helper methods
  def get_theme_from_storage
    page.evaluate_script("localStorage.getItem('leihs-lending-theme')")
  end

  def has_theme_class?(theme)
    page.evaluate_script("document.documentElement.classList.contains('#{theme}')")
  end

  def open_theme_menu
    # Theme changes remount the header; close any leftover Radix menu first
    # so the next trigger click opens instead of toggling closed (Firefox 153).
    page.send_keys(:escape)
    expect(page).not_to have_css("[data-test-id='user-menu-content']")

    find("[data-test-id='user-menu']").click
    expect(page).to have_css("[data-test-id='user-menu-content']")
    find("[data-test-id='theme-menu']").click
    expect(page).to have_css("[data-test-id='theme-light']")
    expect(page).to have_css("[data-test-id='theme-dark']")
    expect(page).to have_css("[data-test-id='theme-system']")
  end

  def select_theme(theme)
    find("[data-test-id='theme-#{theme}']").click
    # Wait for menus to close after selection before reopening
    expect(page).not_to have_css("[data-test-id='user-menu-content']")
  end

  scenario "toggle between dark and light themes" do
    # Verify page loads
    expect(page).to have_content(pool.name, wait: 10)

    # Open theme menu
    open_theme_menu

    # Select Dark theme
    select_theme("dark")

    # Verify localStorage is set to "dark"
    expect(get_theme_from_storage).to eq("dark")

    # Verify document has "dark" class
    expect(has_theme_class?("dark")).to be true

    # Verify document does NOT have "light" class
    expect(has_theme_class?("light")).to be false

    # Re-open theme menu for light theme
    open_theme_menu

    # Select Light theme
    select_theme("light")

    # Verify localStorage is set to "light"
    expect(get_theme_from_storage).to eq("light")

    # Verify document has "light" class
    expect(has_theme_class?("light")).to be true

    # Verify document does NOT have "dark" class
    expect(has_theme_class?("dark")).to be false
  end

  scenario "system theme preference" do
    expect(page).to have_content(pool.name, wait: 10)

    # Open theme menu
    open_theme_menu

    # Select System theme
    select_theme("system")

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
    expect(page).to have_content(pool.name, wait: 10)

    # Set dark theme
    open_theme_menu
    select_theme("dark")
    expect(get_theme_from_storage).to eq("dark")

    # Reload the page
    visit "/lending/#{pool.id}/"
    expect(page).to have_content(pool.name, wait: 10)

    # Verify theme persisted
    expect(get_theme_from_storage).to eq("dark")
    expect(has_theme_class?("dark")).to be true
  end
end
