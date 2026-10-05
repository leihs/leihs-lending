require "features_helper"
require_relative "../shared/common"

feature "Reject order" do
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let(:manager) { FactoryBot.create(:user, language_locale: "en-GB") }
  let(:borrower) { FactoryBot.create(:user, firstname: "Frieda", lastname: "Fragend") }
  let(:next_monday) { Date.today.next_occurring(:monday) }

  before do
    grant_pool_access(manager, pool)
    grant_pool_access(borrower, pool)
    sign_in(manager)
    click_on pool.name
  end

  def grant_pool_access(user, inventory_pool, role: "lending_manager")
    database[:direct_access_rights].insert(
      id: SecureRandom.uuid,
      user_id: user.id,
      inventory_pool_id: inventory_pool.id,
      role: role
    )
  end

  def create_order(purpose: "Semester project")
    order = create(:order, user: borrower, inventory_pool: pool,
      state: "submitted", purpose: purpose)
    create(:reservation,
      user: borrower,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "submitted",
      start_date: next_monday.to_s,
      end_date: (next_monday + 7).to_s)
    order
  end

  def open_reject_dialog
    find("tbody tr", text: "Frieda Fragend")
      .find("[data-test-id='order-actions-menu']").click
    # radix renders menu items as divs, so click_on would not find them
    find("[role='menuitem']", text: "Reject").click
    find("[data-test-id='reject-order-dialog']")
  end

  scenario "shows user, purpose and items in the dialog" do
    create_order

    click_on "Orders"
    dialog = open_reject_dialog

    expect(dialog).to have_content("Reject order")
    expect(dialog).to have_content("Frieda Fragend")
    expect(dialog).to have_content("Semester project")
    expect(dialog).to have_content(model.product)
    expect(dialog).to have_content(
      "#{next_monday.strftime("%d/%m/%Y")} - #{(next_monday + 7).strftime("%d/%m/%Y")} (8 days)"
    )
  end

  scenario "rejects a submitted order with a reason" do
    order = create_order

    click_on "Orders"
    open_reject_dialog

    find("[data-test-id='reject-order-reason']").set("Out of stock")
    find("[data-test-id='reject-order-submit']").click

    expect(page).not_to have_css("[data-test-id='reject-order-dialog']")
    expect(Order[order.id].state).to eq("rejected")
    expect(Order[order.id].reject_reason).to eq("Out of stock")
    expect(Reservation.where(order_id: order.id).map(&:status)).to all(eq("rejected"))

    row = find("tbody tr", text: "Frieda Fragend")
    row.find("[data-test-id='order-status-tooltip-trigger']").hover
    expect(page).to have_text "rejected\nReason: Out of stock"
  end

  scenario "requires a comment" do
    order = create_order

    click_on "Orders"
    open_reject_dialog

    find("[data-test-id='reject-order-submit']").click

    expect(page).to have_content("Specification of a comment is required")
    expect(page).to have_css("[data-test-id='reject-order-dialog']")
    expect(Order[order.id].state).to eq("submitted")
  end

  scenario "cancelling leaves the order submitted" do
    order = create_order

    click_on "Orders"
    dialog = open_reject_dialog
    within(dialog) { click_on "Cancel" }

    expect(page).not_to have_css("[data-test-id='reject-order-dialog']")
    expect(Order[order.id].state).to eq("submitted")
    expect(find("tbody tr", text: "Frieda Fragend")).to have_content("Approve")
  end
end
