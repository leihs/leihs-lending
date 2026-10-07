require "features_helper"
require_relative "../shared/common"

feature "Edit order" do
  let(:pool) { create(:inventory_pool) }
  let(:manager) { FactoryBot.create(:user, language_locale: "en-GB") }
  let(:borrower) { FactoryBot.create(:user, firstname: "Ernst", lastname: "Einmalig") }
  let(:camera) { lendable_model("Kamera") }
  let(:tripod) { lendable_model("Stativ") }
  let(:next_monday) { Date.today.next_occurring(:monday) }
  let(:order) do
    create(:order, user: borrower, inventory_pool: pool,
      state: "submitted", purpose: "Semester project")
  end

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

  def create_reservation(model, start_date: next_monday, end_date: next_monday + 4)
    create(:reservation,
      user: borrower,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "submitted",
      start_date: start_date.to_s,
      end_date: end_date.to_s)
  end

  def visit_edit_page
    visit "/lending/#{pool.id}/orders/#{order.id}"
    expect(page).to have_content("Edit order Ernst Einmalig")
  end

  def line_of(model)
    find("[data-test-id='reservation-line']", text: model.product)
  end

  def date_range_text(start_date, end_date)
    fmt = "%A %d/%m/%Y"
    "#{start_date.strftime(fmt)} – #{end_date.strftime(fmt)}"
  end

  def calendar_dialog
    find("[data-test-id='calendar-dialog']").tap do |dialog|
      expect(dialog).not_to have_css("[aria-busy='true']")
    end
  end

  def pick_range(dialog, start_date, end_date)
    dialog.find("[data-day='#{start_date}']").click
    dialog.find("[data-day='#{end_date}']").click
  end

  def reservations_of(model)
    Reservation.where(order_id: order.id, model_id: model.id).all
  end

  def open_purpose_dialog
    find("[data-test-id='edit-order-purpose']").click
    find("[data-test-id='order-purpose-dialog']")
  end

  scenario "shows the order's lines grouped by date range" do
    2.times { create_reservation(camera) }
    create_reservation(tripod, end_date: next_monday + 1)

    visit_edit_page

    expect(page).to have_content("Semester project")
    expect(page).to have_content(date_range_text(next_monday, next_monday + 4))
    expect(page).to have_content("5 days")
    expect(page).to have_content(date_range_text(next_monday, next_monday + 1))
    expect(page).to have_content("2 days")
    expect(page).to have_css("[data-test-id='reservation-line']", count: 2)
    expect(line_of(camera).find("[data-test-id='line-quantity']")).to have_content(%r{^2 / \d+$})
    expect(line_of(tripod).find("[data-test-id='line-quantity']")).to have_content(%r{^1 / \d+$})
  end

  scenario "changes dates and quantity of a single line" do
    create_reservation(camera)
    create_reservation(tripod)

    visit_edit_page
    line_of(camera).find("[data-test-id='edit-line']").click

    dialog = calendar_dialog
    expect(dialog).to have_content("Edit reservation")
    expect(dialog).to have_content("Ernst Einmalig")
    expect(dialog).to have_css("[data-test-id='calendar-day-quantity']", minimum: 1)
    expect(dialog.find("[data-test-id='calendar-line']")).to have_content(camera.product)

    pick_range(dialog, next_monday + 1, next_monday + 3)
    expect(dialog.find("[data-test-id='calendar-start-date']").value)
      .to eq((next_monday + 1).strftime("%d/%m/%Y"))
    expect(dialog.find("[data-test-id='calendar-end-date']").value)
      .to eq((next_monday + 3).strftime("%d/%m/%Y"))
    dialog.find("[data-test-id='calendar-quantity']").send_keys(:up)
    dialog.find("[data-test-id='calendar-save']").click

    expect(page).not_to have_css("[data-test-id='calendar-dialog']")
    expect(page).to have_content(date_range_text(next_monday + 1, next_monday + 3))
    expect(line_of(camera).find("[data-test-id='line-quantity']")).to have_content(%r{^2 / })

    cameras = reservations_of(camera)
    expect(cameras.size).to eq(2)
    expect(cameras.map(&:start_date)).to all(eq(next_monday + 1))
    expect(cameras.map(&:end_date)).to all(eq(next_monday + 3))
    expect(cameras.map(&:status)).to all(eq("submitted"))

    tripods = reservations_of(tripod)
    expect(tripods.size).to eq(1)
    expect(tripods.first.start_date).to eq(next_monday)
  end

  scenario "changes the dates of the selected lines, keeping their quantities" do
    2.times { create_reservation(camera) }
    create_reservation(tripod)

    visit_edit_page
    find("[data-test-id='select-range']").click
    expect(find("[data-test-id='edit-selection']")).to have_content("2")
    find("[data-test-id='edit-selection']").click

    dialog = calendar_dialog
    expect(dialog).to have_css("[data-test-id='calendar-line']", count: 2)
    expect(dialog).not_to have_css("[data-test-id='calendar-quantity']")
    expect(dialog).to have_css(
      "[data-test-id='calendar-day-available'], [data-test-id='calendar-day-unavailable']",
      minimum: 1
    )

    pick_range(dialog, next_monday + 2, next_monday + 3)
    dialog.find("[data-test-id='calendar-save']").click

    expect(page).not_to have_css("[data-test-id='calendar-dialog']")
    expect(page).to have_content(date_range_text(next_monday + 2, next_monday + 3))
    expect(find("[data-test-id='edit-selection']")).to have_content("0")

    [[camera, 2], [tripod, 1]].each do |model, quantity|
      reservations = reservations_of(model)
      expect(reservations.size).to eq(quantity)
      expect(reservations.map(&:start_date)).to all(eq(next_monday + 2))
      expect(reservations.map(&:end_date)).to all(eq(next_monday + 3))
    end
  end

  scenario "cancelling the dialog leaves the reservations untouched" do
    reservation = create_reservation(camera)

    visit_edit_page
    line_of(camera).find("[data-test-id='edit-line']").click

    dialog = calendar_dialog
    pick_range(dialog, next_monday + 1, next_monday + 3)
    within(dialog) { click_on "Cancel" }

    expect(page).not_to have_css("[data-test-id='calendar-dialog']")
    expect(page).to have_content(date_range_text(next_monday, next_monday + 4))
    expect(reservations_of(camera).map(&:id)).to eq([reservation.id])
    expect(reservations_of(camera).first.start_date).to eq(next_monday)
  end

  scenario "edits the purpose" do
    visit_edit_page
    dialog = open_purpose_dialog
    expect(dialog.find("[data-test-id='order-purpose']").value).to eq("Semester project")

    dialog.find("[data-test-id='order-purpose']").set("Exhibition\nin Basel")
    dialog.find("[data-test-id='order-purpose-submit']").click

    expect(page).not_to have_css("[data-test-id='order-purpose-dialog']")
    expect(page).to have_content("Exhibition\nin Basel")
    expect(Order[order.id].purpose).to eq("Exhibition\nin Basel")
  end

  scenario "requires a purpose" do
    visit_edit_page
    dialog = open_purpose_dialog

    dialog.find("[data-test-id='order-purpose']").set("   ")
    dialog.find("[data-test-id='order-purpose-submit']").click

    expect(dialog).to have_content("Specification of a purpose is required")
    expect(page).to have_css("[data-test-id='order-purpose-dialog']")
    expect(Order[order.id].purpose).to eq("Semester project")
  end

  scenario "cancelling the purpose dialog leaves the purpose untouched" do
    visit_edit_page
    dialog = open_purpose_dialog

    dialog.find("[data-test-id='order-purpose']").set("Something else")
    within(dialog) { click_on "Cancel" }

    expect(page).not_to have_css("[data-test-id='order-purpose-dialog']")
    expect(page).to have_content("Semester project")
    expect(Order[order.id].purpose).to eq("Semester project")
  end
end
