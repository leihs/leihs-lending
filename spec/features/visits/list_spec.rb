require "features_helper"
require_relative "../shared/common"

feature "Visits list" do
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

  def create_hand_over(target_user: manager, start_date: next_monday)
    order = create(:order, user: target_user, inventory_pool: pool)
    create(:reservation,
      user: target_user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "submitted",
      start_date: start_date.to_s,
      end_date: (start_date + 7).to_s)
    order
  end

  def create_take_back(target_user: manager, end_date: next_monday)
    order = create(:order, user: target_user, inventory_pool: pool, state: "approved")
    reservation = create(:reservation,
      user: target_user,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "approved",
      start_date: (Date.today - 7).to_s,
      end_date: end_date.to_s)
    contract_id = SecureRandom.uuid
    database.transaction do
      database[:contracts].insert(
        id: contract_id,
        compact_id: contract_id[0..7],
        state: "open",
        user_id: target_user.id,
        inventory_pool_id: pool.id,
        purpose: "test",
        created_at: Time.now,
        updated_at: Time.now
      )
      database[:reservations]
        .where(id: reservation.id)
        .update(status: "signed", contract_id: contract_id)
    end
    order
  end

  scenario "shows table headers" do
    create_hand_over
    click_on "Visits"
    expect(page).to have_content("Name")
    expect(page).to have_content("Date")
    expect(page).to have_content("Items")
    expect(page).to have_content("Duration")
    expect(page).to have_content("Notifications")
  end

  scenario "shows empty state when there are no visits" do
    click_on "Visits"
    expect(page).to have_content("No visits found")
  end

  scenario "shows correct data in all cells of a hand-over row" do
    borrower = FactoryBot.create(:user, firstname: "Ernst", lastname: "Einmalig")
    grant_pool_access(borrower, pool)

    start_date = next_monday
    end_date = next_monday + 7

    order = create(:order, user: borrower, inventory_pool: pool)
    create(:reservation,
      user: borrower,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "submitted",
      start_date: start_date.to_s,
      end_date: end_date.to_s)

    database[:emails].insert(
      id: SecureRandom.uuid,
      user_id: borrower.id,
      subject: "Reminder",
      body: "Please pick up your items.",
      from_address: "noreply@example.com",
      to_address: borrower.email,
      created_at: start_date.to_time,
      updated_at: start_date.to_time
    )

    click_on "Visits"

    within(find("tbody tr", text: "Ernst Einmalig")) do
      expect(page).to have_content("Ernst Einmalig")           # name
      expect(page).to have_content(start_date.strftime("%d/%m/%Y")) # date (en-GB)
      expect(page).to have_content("1")                        # quantity
      expect(page).to have_content("8 days")                   # duration (inclusive: 8 days)
      expect(page).to have_content("1 reminder email")         # notifications
      expect(page).to have_content("Hand over")                # action
    end
  end

  scenario "shows correct data in all cells of a take-back row" do
    borrower = FactoryBot.create(:user, firstname: "Egon", lastname: "Einzigartig")
    grant_pool_access(borrower, pool)

    end_date = next_monday

    create_take_back(target_user: borrower, end_date: end_date)

    click_on "Visits"

    within(find("tbody tr", text: "Egon Einzigartig")) do
      expect(page).to have_content("Egon Einzigartig")         # name
      expect(page).to have_content(end_date.strftime("%d/%m/%Y")) # date (en-GB)
      expect(page).to have_content("1")                        # quantity
      expect(page).to have_content("No reminders")             # notifications
      expect(page).to have_content("Take back")                # action
    end
  end

  scenario "shows summed quantity when a visit has multiple reservations" do
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

    click_on "Visits"

    within(find("tbody tr", text: "Multi Booker")) do
      expect(find("[data-test-id='visit-items-popover-trigger']")).to have_content("2")
    end
  end

  scenario "popovers open on click" do
    borrower = FactoryBot.create(:user, firstname: "Ernst", lastname: "Einmalig")
    grant_pool_access(borrower, pool)

    order = create(:order, user: borrower, inventory_pool: pool)
    create(:reservation,
      user: borrower,
      inventory_pool: pool,
      leihs_model: model,
      order: order,
      status: "submitted",
      start_date: next_monday.to_s,
      end_date: (next_monday + 7).to_s)

    database[:emails].insert(
      id: SecureRandom.uuid,
      user_id: borrower.id,
      subject: "Reminder",
      body: "Please pick up your items.",
      from_address: "noreply@example.com",
      to_address: borrower.email,
      created_at: next_monday.to_time,
      updated_at: next_monday.to_time
    )

    click_on "Visits"

    row = find("tbody tr", text: "Ernst Einmalig")

    # user name popover shows name and email
    name_trigger = row.find("[data-test-id='visit-user-popover-trigger']")
    name_trigger.click
    expect(page).to have_content(borrower.email)
    name_trigger.click  # close

    # quantity popover (mock content)
    qty_trigger = row.find("[data-test-id='visit-items-popover-trigger']")
    qty_trigger.click
    expect(page).to have_content("...TODO...")
    qty_trigger.click  # close

    # reminders popover (mock content)
    row.find("[data-test-id='visit-reminders-popover-trigger']").click
    expect(page).to have_content("...TODO...")
  end

  scenario "filters visits by term" do
    user1 = FactoryBot.create(:user, firstname: "Zoltan", lastname: "Uniquesson")
    create_hand_over(target_user: user1)
    user2 = FactoryBot.create(:user, firstname: "Jürgen", lastname: "Ohnegleichen")
    create_hand_over(target_user: user2)
    click_on "Visits"
    # without search term filter
    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).to have_content("Jürgen Ohnegleichen")
    # WITH search term filter
    find("input[name='term']").set("Zoltan")
    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).not_to have_content("Jürgen Ohnegleichen")
  end

  scenario "filters visits by visit type" do
    create_hand_over
    create_take_back
    click_on "Visits"
    find("[name='visitType']").click
    find("[data-test-id='HAND_OVER']").click
    expect(page).to have_content("Hand over")
    expect(page).not_to have_content("Take back")
  end

  scenario "filters visits by verification requirement" do
    no_verify_user = FactoryBot.create(:user, firstname: "Anna", lastname: "Noverify")
    user_only_user = FactoryBot.create(:user, firstname: "Bruno", lastname: "Useronly")
    user_model_user = FactoryBot.create(:user, firstname: "Clara", lastname: "Usermodel")

    create_hand_over(target_user: no_verify_user)
    create_hand_over(target_user: user_only_user)
    create_hand_over(target_user: user_model_user)

    now = Time.now

    # Group A: user_only_user is a member, no model entitlement
    # → with_user_to_verify=true, with_user_and_model_to_verify=false
    eg_a = SecureRandom.uuid
    database[:entitlement_groups].insert(
      id: eg_a, name: "Verify User Only", inventory_pool_id: pool.id,
      is_verification_required: true, created_at: now, updated_at: now
    )
    database[:entitlement_groups_direct_users].insert(
      id: SecureRandom.uuid, user_id: user_only_user.id, entitlement_group_id: eg_a
    )

    # Group B: user_model_user is a member and the model has an entitlement
    # → with_user_to_verify=true, with_user_and_model_to_verify=true
    eg_b = SecureRandom.uuid
    database[:entitlement_groups].insert(
      id: eg_b, name: "Verify User and Model", inventory_pool_id: pool.id,
      is_verification_required: true, created_at: now, updated_at: now
    )
    database[:entitlement_groups_direct_users].insert(
      id: SecureRandom.uuid, user_id: user_model_user.id, entitlement_group_id: eg_b
    )
    database[:entitlements].insert(
      id: SecureRandom.uuid, model_id: model.id, entitlement_group_id: eg_b, quantity: 1
    )

    click_on "Visits"
    expect(page).to have_content("Anna Noverify")
    expect(page).to have_content("Bruno Useronly")
    expect(page).to have_content("Clara Usermodel")

    find("[name='verification']").click
    find("[data-test-id='USER']").click
    expect(page).not_to have_content("Anna Noverify")
    expect(page).to have_content("Bruno Useronly")
    expect(page).not_to have_content("Clara Usermodel")

    find("[name='verification']").click
    find("[data-test-id='USER_AND_MODEL']").click
    expect(page).not_to have_content("Anna Noverify")
    expect(page).not_to have_content("Bruno Useronly")
    expect(page).to have_content("Clara Usermodel")

    find("[name='verification']").click
    find("[data-test-id='NONE_REQUIRED']").click
    expect(page).to have_content("Anna Noverify")
    expect(page).not_to have_content("Bruno Useronly")
    expect(page).not_to have_content("Clara Usermodel")
  end

  scenario "filters visits by date range" do
    base = Date.today.beginning_of_month

    early_user = FactoryBot.create(:user, firstname: "Alice", lastname: "Tooearly")
    mid_user = FactoryBot.create(:user, firstname: "Bob", lastname: "Inrange")
    late_user = FactoryBot.create(:user, firstname: "Carol", lastname: "Toolate")

    create_hand_over(target_user: early_user, start_date: base + 7)   # 8th
    create_hand_over(target_user: mid_user, start_date: base + 13)  # 14th
    create_hand_over(target_user: late_user, start_date: base + 20)  # 21st

    click_on "Visits"
    expect(page).to have_content("Alice Tooearly")
    expect(page).to have_content("Bob Inrange")
    expect(page).to have_content("Carol Toolate")

    # startDate filter: from 11th — excludes 8th (Alice), keeps 14th and 21st
    find("[data-test-id='startDate-filter-button']").click
    within("[data-test-id='startDate-calendar']") do
      all(:button, (base + 10).day.to_s).last.click  # 11th
    end
    expect(page).not_to have_content("Alice Tooearly")
    expect(page).to have_content("Bob Inrange")
    expect(page).to have_content("Carol Toolate")

    # endDate filter: up to 18th — excludes 21st (Carol), leaves 14th (Bob)
    find("[data-test-id='endDate-filter-button']").click
    within("[data-test-id='endDate-calendar']") do
      all(:button, (base + 17).day.to_s).last.click  # 18th
    end
    expect(page).not_to have_content("Alice Tooearly")
    expect(page).to have_content("Bob Inrange")
    expect(page).not_to have_content("Carol Toolate")
  end

  scenario "resets all filters" do
    user1 = FactoryBot.create(:user, firstname: "Zoltan", lastname: "Uniquesson")
    user2 = FactoryBot.create(:user, firstname: "Juergen", lastname: "Ohnegleichen")

    create_hand_over(target_user: user1)
    create_hand_over(target_user: user2)

    click_on "Visits"

    # apply term filter — narrows to one result
    find("input[name='term']").set("Zoltan")
    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).not_to have_content("Juergen Ohnegleichen")

    # reset clears the filter
    find("[data-test-id='reset-filters-button']").click

    expect(find("input[name='term']").value).to eq("")
    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).to have_content("Juergen Ohnegleichen")
  end

  scenario "focuses term filter input with Alt+Shift+F" do
    click_on "Visits"

    page.driver.browser.action
      .key_down(:shift)
      .key_down(:alt)
      .send_keys("f")
      .key_up(:alt)
      .key_up(:shift)
      .perform

    expect(page.evaluate_script("document.activeElement.name")).to eq("term")
  end

  scenario "navigates between pages with Alt+Shift+arrow keys" do
    11.times { |i| create_hand_over(start_date: next_monday + i) }
    click_on "Visits"

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

  scenario "marks overdue visits with a destructive left border" do
    overdue_user = FactoryBot.create(:user, firstname: "Otto", lastname: "Faellig")
    punctual_user = FactoryBot.create(:user, firstname: "Fridolin", lastname: "Pünktlich")

    # overdue: start_date in the past → v.date < CURRENT_DATE → is_overdue = true
    create_hand_over(target_user: overdue_user, start_date: Date.today - 1)
    create_hand_over(target_user: punctual_user, start_date: next_monday)

    click_on "Visits"

    overdue_row = find("tbody tr", text: "Otto Faellig")
    punctual_row = find("tbody tr", text: "Fridolin Pünktlich")

    expect(overdue_row[:class]).to include("border-l-destructive")
    expect(punctual_row[:class]).to include("border-l-transparent")
  end

  scenario "paginates visits" do
    11.times do |i|
      create_hand_over(start_date: next_monday + i)
    end
    click_on "Visits"

    expect(find("[data-test-id='pagination-range']")).to have_content("1-11 of 11 rows")

    find("[data-test-id='pagination-size-button']").click
    within("[data-test-id='pagination-size-dropdown']") do
      click_on "10"
    end

    expect(find("[data-test-id='pagination-range']")).to have_content("1-10 of 11 rows")

    find("[aria-label='Go to next page']").click

    expect(find("[data-test-id='pagination-range']")).to have_content("11-11 of 11 rows")
  end

  scenario "shows skeleton rows and aria-busy during loading" do
    create_hand_over
    click_on "Visits"
    expect(page).to have_selector("tbody tr")
    expect(page).not_to have_selector("[aria-busy='true']")

    # Hold the next fetch for 800 ms so the loading state is observable
    page.execute_script(<<~JS)
      const orig = window.fetch;
      window.fetch = (...args) =>
        new Promise(r => setTimeout(() => r(), 800)).then(() => orig(...args));
    JS

    find("[name='visitType']").click
    find("[data-test-id='HAND_OVER']").click

    expect(page).to have_selector("[aria-busy='true']")
    expect(page).to have_selector("[data-slot='skeleton']")

    expect(page).not_to have_selector("[aria-busy='true']")
    expect(page).not_to have_selector("[data-slot='skeleton']")
  end
end
