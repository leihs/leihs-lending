require "features_helper"

feature "Sign-in / Sign-out" do
  before :each do
    @user = create_user_with_password
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

    click_button "Sign out"

    expect(current_path).to eq "/"

    visit "/lending/"
    expect(page).to have_current_path("/lending/sign-in")
  end
end
