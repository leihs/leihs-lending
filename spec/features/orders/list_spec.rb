require "features_helper"
require_relative "../shared/common"

feature "Orders list" do
  let(:pool) { create(:inventory_pool) }
  let(:model) { create(:leihs_model) }
  let(:manager) { FactoryBot.create(:user, language_locale: "en-GB") }
  let(:next_monday) { Date.today.next_occurring(:monday) }

  before do
    grant_pool_access(manager, pool)
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

  def create_order(target_user: manager, state: "submitted", start_date: next_monday, end_date: nil, purpose: nil, reject_reason: nil)
    end_date ||= start_date + 7
    order_attrs = {user: target_user, inventory_pool: pool, state: state}
    order_attrs[:purpose] = purpose if purpose
    order_attrs[:reject_reason] = reject_reason if reject_reason
    order = create(:order, **order_attrs)
    create(:reservation,
      user: target_user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: state,
      start_date: start_date.to_s,
      end_date: end_date.to_s)
    order
  end

  scenario "shows table headers" do
    create_order
    click_on "Orders"
    expect(page).to have_content("Name")
    expect(page).to have_content("Date")
    expect(page).to have_content("Items")
    expect(page).to have_content("Duration")
    expect(page).to have_content("Purpose")
    expect(page).to have_content("Status")
  end

  scenario "shows empty state when there are no orders" do
    click_on "Orders"
    expect(page).to have_content("No orders found")
  end

  scenario "shows correct data in all cells of a submitted order row" do
    borrower = FactoryBot.create(:user, firstname: "Ernst", lastname: "Einmalig")
    grant_pool_access(borrower, pool)

    start_date = next_monday
    end_date = next_monday + 7

    create_order(target_user: borrower, state: "submitted",
      start_date: start_date, end_date: end_date, purpose: "Test project")

    click_on "Orders"

    within(find("tbody tr", text: "Ernst Einmalig")) do
      expect(page).to have_content("Ernst Einmalig")
      expect(page).to have_content(Date.today.strftime("%d/%m/%Y")) # createdAt (today)
      expect(page).to have_content("1")                             # items
      expect(page).to have_content("8 days")                        # duration (inclusive)
      expect(page).to have_content("Test project")                  # purpose
      expect(page).to have_content("Approve")                       # action button
    end
  end

  scenario "shows correct data in all cells of an approved order row" do
    borrower = FactoryBot.create(:user, firstname: "Egon", lastname: "Genehmigt")
    grant_pool_access(borrower, pool)

    create_order(target_user: borrower, state: "approved")

    click_on "Orders"

    row = find("tbody tr", text: "Egon Genehmigt")

    within(row) do
      expect(page).to have_content("Egon Genehmigt")
      expect(page).to have_content("Hand over")
    end

    row.find("[data-test-id='order-status-tooltip-trigger']").hover
    expect(page).to have_css("[role='tooltip']", text: "approved")
  end

  scenario "shows correct data in all cells of a rejected order row" do
    borrower = FactoryBot.create(:user, firstname: "Frieda", lastname: "Abgelehnt")
    grant_pool_access(borrower, pool)

    create_order(target_user: borrower, state: "rejected", reject_reason: "Out of stock")

    click_on "Orders"

    row = find("tbody tr", text: "Frieda Abgelehnt")

    within(row) do
      expect(page).to have_content("Frieda Abgelehnt")
    end

    row.find("[data-test-id='order-status-tooltip-trigger']").hover
    expect(page).to have_text "rejected\nReason: Out of stock"
  end

  scenario "shows summed quantity when an order has multiple reservations" do
    borrower = FactoryBot.create(:user, firstname: "Multi", lastname: "Booker")

    order = create(:order, user: borrower, inventory_pool: pool)
    2.times do
      create(:reservation,
        user: borrower,
        inventory_pool: pool,
        leihs_model: model,
        order: order,
        status: "submitted",
        start_date: next_monday.to_s,
        end_date: (next_monday + 7).to_s)
    end

    click_on "Orders"

    within(find("tbody tr", text: "Multi Booker")) do
      expect(find("[data-test-id='order-items-popover-trigger']")).to have_content("2")
    end
  end

  scenario "popovers open on click" do
    borrower = FactoryBot.create(:user, firstname: "Ernst", lastname: "Einmalig")
    grant_pool_access(borrower, pool)

    create_order(target_user: borrower)

    click_on "Orders"

    row = find("tbody tr", text: "Ernst Einmalig")

    # user name popover shows email
    name_trigger = row.find("[data-test-id='order-user-popover-trigger']")
    name_trigger.click
    expect(page).to have_content(borrower.email)
    name_trigger.click  # close

    # items popover (mock content)
    items_trigger = row.find("[data-test-id='order-items-popover-trigger']")
    items_trigger.click
    expect(page).to have_content("...TODO...")
    items_trigger.click  # close
  end

  scenario "shows purpose truncated with expand popover" do
    borrower = FactoryBot.create(:user, firstname: "Lena", lastname: "Langtext")
    grant_pool_access(borrower, pool)

    long_purpose = "A" * 40
    create_order(target_user: borrower, purpose: long_purpose)

    click_on "Orders"

    truncated = "A" * 30 + "…"
    row = find("tbody tr", text: "Lena Langtext")

    expect(row).to have_content(truncated)

    row.find("span.cursor-pointer", text: truncated).click
    expect(page).to have_content(long_purpose)
  end

  scenario "shows suspended user icon when user is suspended" do
    suspended_user = FactoryBot.create(:user, firstname: "Sven", lastname: "Gesperrt")
    grant_pool_access(suspended_user, pool)
    create(:suspension, user: suspended_user, inventory_pool: pool)

    create_order(target_user: suspended_user)

    click_on "Orders"

    name_trigger = find("tbody tr", text: "Sven Gesperrt")
      .find("[data-test-id='order-user-popover-trigger']")
    expect(name_trigger).to have_css("svg")
  end

  scenario "paginates orders" do
    11.times { |i| create_order(start_date: next_monday + i) }

    click_on "Orders"

    expect(find("[data-test-id='pagination-range']")).to have_content("1-11 of 11 rows")

    find("[data-test-id='pagination-size-button']").click
    within("[data-test-id='pagination-size-dropdown']") do
      click_on "10"
    end

    expect(find("[data-test-id='pagination-range']")).to have_content("1-10 of 11 rows")

    find("[aria-label='Go to next page']").click

    expect(find("[data-test-id='pagination-range']")).to have_content("11-11 of 11 rows")
  end

  scenario "navigates between pages with Alt+Shift+arrow keys" do
    11.times { |i| create_order(start_date: next_monday + i) }
    click_on "Orders"

    find("[data-test-id='pagination-size-button']").click
    within("[data-test-id='pagination-size-dropdown']") { click_on "10" }

    expect(find("[data-test-id='pagination-current-page']")).to have_content("1")

    page.driver.browser.action
      .key_down(:shift)
      .key_down(:alt)
      .send_keys(:arrow_right)
      .key_up(:alt)
      .key_up(:shift)
      .perform

    expect(find("[data-test-id='pagination-current-page']")).to have_content("2")

    page.driver.browser.action
      .key_down(:shift)
      .key_down(:alt)
      .send_keys(:arrow_left)
      .key_up(:alt)
      .key_up(:shift)
      .perform

    expect(find("[data-test-id='pagination-current-page']")).to have_content("1")
  end

  scenario "shows skeleton rows and aria-busy during loading" do
    create_order
    click_on "Orders"
    expect(page).to have_selector("tbody tr")
    expect(page).not_to have_selector("[aria-busy='true']")

    # Hold the next fetch for 800 ms so the loading state is observable
    page.execute_script(<<~JS)
      const orig = window.fetch;
      window.fetch = (...args) =>
        new Promise(r => setTimeout(() => r(), 800)).then(() => orig(...args));
    JS

    find("[name='states']").click
    find("[data-test-id='SUBMITTED']").click

    expect(page).to have_selector("[aria-busy='true']")
    expect(page).to have_selector("[data-slot='skeleton']")

    expect(page).not_to have_selector("[aria-busy='true']")
    expect(page).not_to have_selector("[data-slot='skeleton']")
  end
end
