def sign_in(user)
  username = user.login || user.email

  visit "/lending/sign-in"
  fill_in("user", with: username)
  fill_in("password", with: user.password)
  click_button("Sign in")
end
