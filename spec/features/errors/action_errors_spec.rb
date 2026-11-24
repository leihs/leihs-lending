require "spec_helper"
require "config/browser"
require "features_helper"
require_relative "../shared/common"

feature "Action errors" do
  let(:pool) { create(:inventory_pool) }
  let(:manager) { FactoryBot.create(:user, language_locale: "en-GB") }
  let(:borrower) { FactoryBot.create(:user, firstname: "Ernst", lastname: "Einmalig") }
  let(:next_monday) { Date.today.next_occurring(:monday) }
  let(:camera) { lendable_model("Kamera") }
  let(:tripod) { lendable_model("Stativ") }
  let(:order) do
    create(:order, user: borrower, inventory_pool: pool,
      state: "submitted", purpose: "Semester project")
  end
  let(:dialog) { "[role='alertdialog']" }

  before do
    grant_pool_access(manager, pool)
    grant_pool_access(borrower, pool)
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

  def lendable_model(product)
    create(:leihs_model, product: product).tap do |m|
      create(:item, leihs_model: m, inventory_pool: pool, owner: pool)
    end
  end

  def create_reservation(model)
    create(:reservation,
      user: borrower,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "submitted",
      start_date: next_monday.to_s,
      end_date: (next_monday + 4).to_s)
  end

  def reservations_of(model)
    Reservation.where(order_id: order.id, model_id: model.id)
  end

  def visit_edit_page
    visit "/lending/#{pool.id}/orders/#{order.id}"
    expect(page).to have_css("h1", text: /Edit order\s+Ernst Einmalig/)
  end

  def line_of(model)
    find("[data-test-id='reservation-line']", text: model.product)
  end

  def delete_line(model)
    line_of(model).find("[data-test-id='line-actions-menu']").click
    find("[data-test-id='delete-line']").click
  end

  scenario "refused action shows the server message in the details" do
    create_reservation(camera)
    create_reservation(tripod)
    visit_edit_page

    reservations_of(tripod).delete
    delete_line(camera)

    within(dialog) do
      expect(page).to have_content("Action Failed")
      expect(page).to have_content("Status: 422")
      find("summary", text: "Technical details").click
      expect(find("details code")).to have_content("Cannot remove the last reservation")
      click_on "Close"
    end

    expect(page).not_to have_css(dialog)
    expect(line_of(camera)).to be_present
    expect(reservations_of(camera).count).to eq(1)
  end

  scenario "gone data offers to reload the page" do
    create_reservation(camera)
    create_reservation(tripod)
    visit_edit_page

    reservations_of(camera).delete
    delete_line(camera)

    within(dialog) do
      expect(page).to have_content("Not Found")
      click_on "Reload page"
    end

    expect(page).not_to have_css(dialog)
    expect(page).to have_css("[data-test-id='reservation-line']", count: 1)
    expect(line_of(tripod)).to be_present
  end

  scenario "error dialog opens on top of the action's dialog" do
    create_reservation(camera)
    visit "/lending/#{pool.id}/orders"

    find("tbody tr", text: "Ernst Einmalig")
      .find("[data-test-id='order-actions-menu']").click
    # radix renders menu items as divs, so click_on would not find them
    find("[role='menuitem']", text: "Reject").click
    find("[data-test-id='reject-order-reason']").set("Out of stock")

    database[:orders].where(id: order.id).update(state: "approved")
    find("[data-test-id='reject-order-submit']").click

    within(dialog) do
      expect(page).to have_content("Action Failed")
      expect(page).to have_content("Status: 422")
      click_on "Close"
    end

    expect(page).not_to have_css(dialog)
    expect(page).to have_css("[data-test-id='reject-order-dialog']")
    expect(find("[data-test-id='reject-order-reason']").value).to eq("Out of stock")
  end

  context "error types" do
    [
      # kind, title, extra button or link
      ["400 Bad request", "Action Failed", nil],
      ["401 Unauthorized", "Session Expired", "Go to sign in"],
      ["403 Forbidden", "Action Not Allowed", nil],
      ["404 Not found", "Not Found", "Reload page"],
      ["500 Server error", "Server Error", nil],
      ["GraphQL error (code 404)", "Not Found", "Reload page"],
      ["Network error", "No connection", nil],
      ["Exception", "Application Error", nil]
    ].each do |label, title, extra|
      scenario "#{label} shows '#{title}'" do
        visit "/lending/debug"
        click_button label

        within(dialog) do
          expect(page).to have_content(title)
          expect(page).to have_button("Close")

          if extra
            expect(page).to have_selector(:link_or_button, extra)
          else
            expect(page).not_to have_button("Reload page")
            expect(page).not_to have_link("Go to sign in")
          end

          click_on "Close"
        end

        expect(page).not_to have_css(dialog)
        expect(page).not_to have_button("Open error message")
      end
    end
  end
end
