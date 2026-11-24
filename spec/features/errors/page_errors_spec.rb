require "spec_helper"
require "config/browser"
require "features_helper"
require_relative "../shared/common"

feature "Page errors" do
  let(:pool) { create(:inventory_pool) }
  let(:manager) { FactoryBot.create(:user, language_locale: "en-GB") }
  let(:dialog) { "[role='alertdialog']" }

  before do
    grant_pool_access(manager, pool)
    sign_in(manager)
  end

  def grant_pool_access(user, inventory_pool, role: "lending_manager")
    database[:direct_access_rights].insert(
      id: SecureRandom.uuid,
      user_id: user.id,
      inventory_pool_id: inventory_pool.id,
      role: role
    )
  end

  def expect_not_found_dialog
    within(dialog) do
      expect(page).to have_content("Page Not Found")
      expect(page).to have_content("Status: 404")
    end
  end

  scenario "unknown route shows a 404 below the header and leads back home" do
    visit "/lending/foo/bar"

    expect_not_found_dialog
    expect(page).to have_css("[data-test-id='app-logo']")

    within(dialog) { click_on "Go to Home" }

    expect(page).to have_current_path("/lending/")
    expect(page).not_to have_css(dialog)
    expect(page).to have_link(pool.name)
  end

  scenario "unknown pool shows a 404" do
    visit "/lending/#{SecureRandom.uuid}/daily"

    expect_not_found_dialog
  end

  scenario "pool without access shows a 404" do
    other_pool = create(:inventory_pool)

    visit "/lending/#{other_pool.id}/daily"

    expect_not_found_dialog
  end

  scenario "unknown order shows a 404 within the pool layout" do
    visit "/lending/#{pool.id}/orders/#{SecureRandom.uuid}"

    expect_not_found_dialog
    expect(page).to have_css("nav[aria-label='breadcrumb']", text: pool.name)
    expect(page).to have_link("Visits")
  end

  scenario "closed dialog can be reopened" do
    visit "/lending/foo/bar"

    within(dialog) { click_on "Close" }
    expect(page).not_to have_css(dialog)

    click_on "Open error message"
    expect_not_found_dialog
  end

  scenario "expired session shows the unauthorized dialog on navigation" do
    visit "/lending/#{pool.id}/orders"
    expect(page).to have_link("Visits")

    database[:user_sessions].where(user_id: manager.id).delete
    click_on "Visits"

    within(dialog) do
      expect(page).to have_content("Unauthorized")
      click_on "Go to sign in"
    end

    expect(page).to have_current_path("/lending/sign-in")
  end

  scenario "details show the server message" do
    visit "/lending/debug/error/graphql-404"

    within(dialog) do
      find("summary", text: "Technical details").click
      expect(find("details code")).to have_content("Not found")
    end
  end

  context "error types" do
    [
      # kind, title, status, details?, retry button?
      ["400", "Bad Request", 400, false, false],
      ["401", "Unauthorized", 401, false, false],
      ["403", "Access Forbidden", 403, false, false],
      ["404", "Page Not Found", 404, false, false],
      ["500", "Server Error", 500, false, true],
      ["graphql-404", "Page Not Found", 404, true, false],
      ["network", "No connection", nil, true, true],
      ["exception", "Application Error", nil, true, true]
    ].each do |kind, title, status, details, retryable|
      scenario "#{kind} shows '#{title}'" do
        visit "/lending/debug/error/#{kind}"

        within(dialog) do
          expect(page).to have_content(title)

          if status
            expect(page).to have_content("Status: #{status}")
          else
            expect(page).not_to have_content("Status:")
          end

          if details
            expect(page).to have_content("Technical details")
          else
            expect(page).not_to have_content("Technical details")
          end

          if retryable
            expect(page).to have_button("Retry")
          else
            expect(page).not_to have_button("Retry")
          end
        end
      end
    end
  end
end
