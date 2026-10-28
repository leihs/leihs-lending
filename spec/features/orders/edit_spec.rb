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
    expect(page).to have_css("h1", text: /Edit order\s+Ernst Einmalig/)
  end

  def line_of(model)
    find("[data-test-id='reservation-line']", text: model.product)
  end

  def date_range_text(start_date, end_date)
    fmt = "%A %d/%m/%Y"
    return start_date.strftime(fmt) if start_date == end_date
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

  def search_model(term)
    find("[data-test-id='new-reservation-term']").set(term)
  end

  def model_search_item(product)
    find("[data-test-id='model-search-item']", text: /\AM\s+#{product}\n/)
  end

  def add_form_dates
    [find("[data-test-id='new-reservation-start-date']").text,
      find("[data-test-id='new-reservation-end-date']").text]
  end

  def form_date(date)
    date.strftime("%d/%m/%Y")
  end

  def day_cell(dialog, date)
    dialog.find("[data-day='#{date}']")
  end

  def delete_line(model)
    line_of(model).find("[data-test-id='line-actions-menu']").click
    find("[data-test-id='delete-line']").click
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
    expect(line_of(camera).find("[data-test-id='line-quantity']")).to have_content(%r{^2$})
    expect(line_of(tripod).find("[data-test-id='line-quantity']")).to have_content(%r{^1$})
  end

  scenario "shows a single date for a one-day range" do
    create_reservation(camera, start_date: next_monday + 2, end_date: next_monday + 2)

    visit_edit_page

    expect(page).to have_content(date_range_text(next_monday + 2, next_monday + 2))
    expect(page).not_to have_content("–")
    expect(page).to have_content("1 day")
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
    expect(line_of(camera).find("[data-test-id='line-quantity']")).to have_content(%r{^2$})

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

  scenario "a single click picks a one-day range" do
    create_reservation(camera)

    visit_edit_page
    line_of(camera).find("[data-test-id='edit-line']").click

    dialog = calendar_dialog
    day_cell(dialog, next_monday + 1).click
    dialog.find("[data-test-id='calendar-save']").click

    expect(page).not_to have_css("[data-test-id='calendar-dialog']")
    expect(page).to have_content(date_range_text(next_monday + 1, next_monday + 1))
    cameras = reservations_of(camera)
    expect(cameras.map(&:start_date)).to eq([next_monday + 1])
    expect(cameras.map(&:end_date)).to eq([next_monday + 1])
  end

  scenario "the quantity field can be cleared, keeping the previous quantity" do
    create_reservation(camera)

    visit_edit_page
    line_of(camera).find("[data-test-id='edit-line']").click

    dialog = calendar_dialog
    quantity = dialog.find("[data-test-id='calendar-quantity']")
    quantity.send_keys(:backspace, :delete)
    expect(quantity.value).to eq("")
    quantity.send_keys(:tab)
    expect(quantity.value).to eq("1")

    quantity.send_keys(:backspace, :delete)
    expect(quantity.value).to eq("")
    dialog.find("[data-test-id='calendar-save']").click

    expect(page).not_to have_css("[data-test-id='calendar-dialog']")
    expect(line_of(camera).find("[data-test-id='line-quantity']")).to have_content(%r{^1$})
    expect(reservations_of(camera).size).to eq(1)
  end

  scenario "explains restrictions of the calendar days in tooltips" do
    create(:holiday, inventory_pool: pool, name: "Knabenschiessen",
      start_date: (next_monday + 2).to_s, end_date: (next_monday + 2).to_s)
    create_reservation(camera)

    visit_edit_page
    line_of(camera).find("[data-test-id='edit-line']").click

    dialog = calendar_dialog
    day_cell(dialog, next_monday + 2).find("[data-test-id='calendar-restriction']").hover
    expect(page).to have_css("[data-slot='tooltip-content']", text: "Knabenschiessen")

    day_cell(dialog, next_monday + 5).find("[data-test-id='calendar-restriction']").hover
    expect(page).to have_css("[data-slot='tooltip-content']", text: "Closed on Saturday")
  end

  scenario "marks a start date on a closed day as not available" do
    create_reservation(camera)

    visit_edit_page
    line_of(camera).find("[data-test-id='edit-line']").click

    dialog = calendar_dialog
    pick_range(dialog, next_monday + 1, next_monday + 2)
    expect(day_cell(dialog, next_monday + 1)[:class]).to include("bg-lime-200")

    pick_range(dialog, next_monday + 5, next_monday + 7)
    expect(day_cell(dialog, next_monday + 5)[:class]).to include("bg-red-100")
    expect(day_cell(dialog, next_monday + 6)[:class]).to include("bg-lime-200")
    expect(day_cell(dialog, next_monday + 7)[:class]).to include("bg-lime-200")
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

  scenario "adds a model found by the search field" do
    create_reservation(tripod)
    camera

    visit_edit_page
    search_model("Kamera")
    model_search_item("Kamera").click

    expect(page).to have_content("Kamera added")
    expect(page).to have_content(date_range_text(next_monday, next_monday + 4))
    expect(line_of(camera).find("[data-test-id='line-quantity']")).to have_content(%r{^1$})
    expect(find("[data-test-id='new-reservation-term']").value).to eq("")

    cameras = reservations_of(camera)
    expect(cameras.size).to eq(1)
    expect(cameras.first.start_date).to eq(next_monday)
    expect(cameras.first.end_date).to eq(next_monday + 4)
    expect(cameras.first.status).to eq("submitted")
    expect(cameras.first.user_id).to eq(borrower.id)
  end

  scenario "adding a model of an existing line raises its quantity" do
    create_reservation(camera, start_date: Date.today, end_date: Date.today)

    visit_edit_page
    search_model("Kamera")
    model_search_item("Kamera").click

    expect(line_of(camera).find("[data-test-id='line-quantity']")).to have_content(%r{^2$})
    expect(page).to have_css("[data-test-id='reservation-line']", count: 1)
    expect(reservations_of(camera).size).to eq(2)
  end

  scenario "the add form preselects the first date range of the order" do
    create_reservation(tripod, start_date: next_monday + 7, end_date: next_monday + 8)
    create_reservation(camera)

    visit_edit_page

    expect(add_form_dates).to eq([form_date(next_monday), form_date(next_monday + 4)])
  end

  scenario "the add form follows the first date range when it changes" do
    create_reservation(camera)

    visit_edit_page
    expect(add_form_dates).to eq([form_date(next_monday), form_date(next_monday + 4)])

    line_of(camera).find("[data-test-id='edit-line']").click
    dialog = calendar_dialog
    pick_range(dialog, next_monday + 1, next_monday + 3)
    dialog.find("[data-test-id='calendar-save']").click

    expect(page).not_to have_css("[data-test-id='calendar-dialog']")
    expect(add_form_dates).to eq([form_date(next_monday + 1), form_date(next_monday + 3)])
  end

  scenario "applies the date range of a group to the add form" do
    create_reservation(camera)
    create_reservation(tripod, start_date: next_monday + 7, end_date: next_monday + 8)
    microphone = lendable_model("Mikrofon")

    visit_edit_page
    first_range, second_range = all("[data-test-id='apply-range']", count: 2)
    expect(first_range).to be_disabled
    expect(second_range).not_to be_disabled

    second_range.click
    expect(add_form_dates).to eq([form_date(next_monday + 7), form_date(next_monday + 8)])
    expect(second_range).to be_disabled
    expect(first_range).not_to be_disabled

    search_model("Mikrofon")
    model_search_item("Mikrofon").click
    expect(page).to have_content("Mikrofon added")

    microphones = reservations_of(microphone)
    expect(microphones.map(&:start_date)).to eq([next_monday + 7])
    expect(microphones.map(&:end_date)).to eq([next_monday + 8])
  end

  scenario "offers no date range to apply when the order has a single one" do
    create_reservation(camera)
    create_reservation(tripod)

    visit_edit_page

    expect(page).to have_css("[data-test-id='reservation-line']", count: 2)
    expect(page).not_to have_css("[data-test-id='apply-range']")
  end

  scenario "marks models without available items in the search results" do
    create_reservation(camera)
    tripod

    visit_edit_page
    search_model("Kamera")
    expect(model_search_item("Kamera")).to have_css("[data-test-id='model-search-item-unavailable']")

    search_model("Stativ")
    expect(model_search_item("Stativ")).not_to have_css("[data-test-id='model-search-item-unavailable']")
  end

  scenario "deletes a single line" do
    2.times { create_reservation(camera) }
    create_reservation(tripod)

    visit_edit_page
    delete_line(camera)

    expect(page).to have_css("[data-test-id='reservation-line']", count: 1)
    expect(page).not_to have_css("[data-test-id='reservation-line']", text: camera.product)
    expect(reservations_of(camera)).to be_empty
    expect(reservations_of(tripod).size).to eq(1)
  end

  scenario "deletes the selected lines" do
    create_reservation(camera)
    create_reservation(tripod)
    other = lendable_model("Mikrofon")
    create_reservation(other, end_date: next_monday + 1)

    visit_edit_page
    line_of(camera).find("[data-test-id='select-line']").click
    line_of(tripod).find("[data-test-id='select-line']").click
    expect(find("[data-test-id='edit-selection']")).to have_content("2")
    find("[data-test-id='selection-actions-menu']").click
    find("[data-test-id='delete-selection']").click

    expect(page).to have_css("[data-test-id='reservation-line']", count: 1)
    expect(line_of(other)).to be_present
    expect(find("[data-test-id='edit-selection']")).to have_content("0")
    expect(reservations_of(camera)).to be_empty
    expect(reservations_of(tripod)).to be_empty
    expect(reservations_of(other).size).to eq(1)
  end

  scenario "refuses to delete all lines of the order" do
    create_reservation(camera)
    create_reservation(tripod)

    visit_edit_page
    find("[data-test-id='select-range']").click
    find("[data-test-id='selection-actions-menu']").click
    find("[data-test-id='delete-selection']").click

    expect(page).to have_content("You cannot delete all reservations of an order")
    expect(page).to have_css("[data-test-id='reservation-line']", count: 2)
    expect(reservations_of(camera).size).to eq(1)
    expect(reservations_of(tripod).size).to eq(1)
  end

  scenario "refuses to delete the only line of the order" do
    create_reservation(camera)

    visit_edit_page
    delete_line(camera)

    expect(page).to have_content("You cannot delete all reservations of an order")
    expect(line_of(camera)).to be_present
    expect(reservations_of(camera).size).to eq(1)
  end
end
