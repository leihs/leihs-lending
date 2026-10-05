require "features_helper"
require_relative "../shared/common"

feature "Approve order" do
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let!(:item) { create(:item, leihs_model: model, inventory_pool: pool, owner: pool) }
  let(:manager) { FactoryBot.create(:user, language_locale: "en-GB") }
  let(:borrower) { FactoryBot.create(:user, firstname: "Ernst", lastname: "Einmalig") }
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

  def create_order(target_user: borrower)
    order = create(:order, user: target_user, inventory_pool: pool, state: "submitted")
    create(:reservation,
      user: target_user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "submitted",
      start_date: next_monday.to_s,
      end_date: (next_monday + 7).to_s)
    order
  end

  # the pool holds a single item, so a competing reservation for the same
  # period makes the order under test unavailable
  def create_competing_order
    other = FactoryBot.create(:user, firstname: "Konkurrenz", lastname: "Kandidat")
    create_order(target_user: other)
  end

  def approve_button_of(name)
    find("tbody tr", text: name).find("[data-test-id='approve-order']")
  end

  scenario "approves a submitted order without a dialog" do
    order = create_order

    click_on "Orders"
    approve_button_of("Ernst Einmalig").click

    expect(page).to have_content("Order approved")
    expect(find("tbody tr", text: "Ernst Einmalig")).to have_content("Hand over")
    expect(Order[order.id].state).to eq("approved")
    expect(Reservation.where(order_id: order.id).map(&:status)).to all(eq("approved"))
  end

  scenario "opens the failure dialog when the order cannot be approved" do
    order = create_order
    create_competing_order

    click_on "Orders"
    approve_button_of("Ernst Einmalig").click

    dialog = find("[data-test-id='approve-failed-dialog']")
    expect(dialog).to have_content("Approval failed")
    expect(dialog).to have_content("Ernst Einmalig")
    expect(dialog).to have_content(
      "This order is not approvable because some reserved models are not available."
    )
    expect(dialog).to have_content(model.product)
    expect(dialog).to have_content("Edit order")
    expect(dialog).to have_content("Approve anyway")

    expect(Order[order.id].state).to eq("submitted")
  end

  scenario "approves anyway from the failure dialog" do
    order = create_order
    create_competing_order

    click_on "Orders"
    approve_button_of("Ernst Einmalig").click

    find("[data-test-id='approve-order-comment']").set("Pick up at desk B")
    find("[data-test-id='approve-order-force']").click

    expect(page).to have_content("Order approved")
    expect(page).not_to have_css("[data-test-id='approve-failed-dialog']")
    expect(Order[order.id].state).to eq("approved")
  end

  scenario "cancelling the failure dialog leaves the order submitted" do
    order = create_order
    create_competing_order

    click_on "Orders"
    approve_button_of("Ernst Einmalig").click

    within("[data-test-id='approve-failed-dialog']") { click_on "Cancel" }

    expect(page).not_to have_css("[data-test-id='approve-failed-dialog']")
    expect(Order[order.id].state).to eq("submitted")
    expect(find("tbody tr", text: "Ernst Einmalig")).to have_content("Approve")
  end

  scenario "editing the order is not available yet" do
    create_order
    create_competing_order

    click_on "Orders"
    approve_button_of("Ernst Einmalig").click
    find("[data-test-id='approve-order-edit']").click

    expect(page).to have_content("Action not available yet")
  end
end
