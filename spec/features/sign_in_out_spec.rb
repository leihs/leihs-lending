require "features_helper"

feature "Sign-in / Sign-out" do
  before :each do
    @user = FactoryBot.create(:user)
  end

  scenario "redirect to sign-in when unauthenticated" do
    visit "/lending/"
    expect(page).to have_current_path("/lending/sign-in")
  end

  scenario "sign-in with invalid credentials shows error" do
    visit "/lending/sign-in"
    fill_in "user", with: @user.login
    fill_in "password", with: "wrong"
    click_button "Sign in"

    expect(page).to have_content("Invalid credentials")
    expect(current_path).to eq "/lending/sign-in"
  end

  scenario "sign-in with valid credentials lands on home page" do
    visit "/lending/sign-in"
    fill_in "user", with: @user.login
    fill_in "password", with: "password"
    click_button "Sign in"

    expect(current_path).to eq "/lending/"
    expect(page).to have_content(@user.login)
  end

  scenario "sign-out clears session and redirects to root" do
    visit "/lending/sign-in"
    fill_in "user", with: @user.login
    fill_in "password", with: "password"
    click_button "Sign in"

    expect(current_path).to eq "/lending/"

    # Wait for successful sign-in - header should appear
    expect(page).not_to have_selector("form.ui-form-signin")
    expect(page).to have_selector("header")

    # Open user menu by clicking button containing user's name
    within("header") do
      # Find and click the user menu button (contains firstname and lastname)
      user_name = "#{@user.firstname} #{@user.lastname}"
      user_menu_button = find("button", text: user_name)
      user_menu_button.click
    end

    # Click logout button in the dropdown menu
    click_button "Logout"

    expect(current_path).to eq "/sign-out"
  end
end
