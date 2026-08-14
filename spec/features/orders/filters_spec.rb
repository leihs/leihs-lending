require "features_helper"
require_relative "../shared/common"

feature "Orders filters" do
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

  def create_order(target_user: manager, state: "submitted", start_date: next_monday, end_date: nil, created_at: nil)
    end_date ||= start_date + 7
    order = create(:order, user: target_user, inventory_pool: pool, state: state)
    database[:orders].where(id: order.id).update(created_at: created_at) if created_at
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

  scenario "filters orders by term" do
    user1 = FactoryBot.create(:user, firstname: "Zoltan", lastname: "Uniquesson")
    user2 = FactoryBot.create(:user, firstname: "Jürgen", lastname: "Ohnegleichen")
    create_order(target_user: user1)
    create_order(target_user: user2)

    click_on "Orders"
    # without filter both are visible
    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).to have_content("Jürgen Ohnegleichen")

    # with term filter only matching user remains
    find("input[name='term']").set("Zoltan")
    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).not_to have_content("Jürgen Ohnegleichen")
  end

  scenario "filters orders by state" do
    submitted_user = FactoryBot.create(:user, firstname: "Anna", lastname: "Offen")
    approved_user = FactoryBot.create(:user, firstname: "Bruno", lastname: "Genehmigt")
    rejected_user = FactoryBot.create(:user, firstname: "Clara", lastname: "Abgelehnt")
    create_order(target_user: submitted_user, state: "submitted")
    create_order(target_user: approved_user, state: "approved")
    create_order(target_user: rejected_user, state: "rejected")

    click_on "Orders"
    expect(page).to have_content("Anna Offen")
    expect(page).to have_content("Bruno Genehmigt")
    expect(page).to have_content("Clara Abgelehnt")

    find("[name='states']").click
    find("[data-test-id='SUBMITTED']").click
    expect(page).to have_content("Anna Offen")
    expect(page).not_to have_content("Bruno Genehmigt")
    expect(page).not_to have_content("Clara Abgelehnt")

    find("[name='states']").click
    find("[data-test-id='APPROVED']").click
    expect(page).not_to have_content("Anna Offen")
    expect(page).to have_content("Bruno Genehmigt")
    expect(page).not_to have_content("Clara Abgelehnt")

    find("[name='states']").click
    find("[data-test-id='REJECTED']").click
    expect(page).not_to have_content("Anna Offen")
    expect(page).not_to have_content("Bruno Genehmigt")
    expect(page).to have_content("Clara Abgelehnt")
  end

  scenario "filters orders by verification requirement" do
    no_verify_user = FactoryBot.create(:user, firstname: "Anna", lastname: "Noverify")
    verify_user = FactoryBot.create(:user, firstname: "Bruno", lastname: "Toverify")
    create_order(target_user: no_verify_user)
    create_order(target_user: verify_user)

    now = Time.now
    eg = SecureRandom.uuid
    database[:entitlement_groups].insert(
      id: eg, name: "Verify Group", inventory_pool_id: pool.id,
      is_verification_required: true, created_at: now, updated_at: now
    )
    database[:entitlement_groups_direct_users].insert(
      id: SecureRandom.uuid, user_id: verify_user.id, entitlement_group_id: eg
    )
    database[:entitlements].insert(
      id: SecureRandom.uuid, model_id: model.id, entitlement_group_id: eg, quantity: 1
    )

    click_on "Orders"
    expect(page).to have_content("Anna Noverify")
    expect(page).to have_content("Bruno Toverify")

    find("[name='toBeVerified']").click
    find("[data-test-id='true']").click
    expect(page).not_to have_content("Anna Noverify")
    expect(page).to have_content("Bruno Toverify")

    find("[name='toBeVerified']").click
    find("[data-test-id='false']").click
    expect(page).to have_content("Anna Noverify")
    expect(page).not_to have_content("Bruno Toverify")
  end

  scenario "filters orders by date range" do
    # The startDate/endDate filter params apply to orders.created_at
    base = Date.today.beginning_of_month

    early_user = FactoryBot.create(:user, firstname: "Alice", lastname: "Tooearly")
    mid_user = FactoryBot.create(:user, firstname: "Bob", lastname: "Inrange")
    late_user = FactoryBot.create(:user, firstname: "Carol", lastname: "Toolate")

    create_order(target_user: early_user, created_at: (base + 7).to_time)   # 8th
    create_order(target_user: mid_user, created_at: (base + 13).to_time)    # 14th
    create_order(target_user: late_user, created_at: (base + 20).to_time)   # 21st

    click_on "Orders"
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
    create_order(target_user: user1)
    create_order(target_user: user2)

    click_on "Orders"

    # apply term filter — narrows to one result
    find("input[name='term']").set("Zoltan")
    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).not_to have_content("Juergen Ohnegleichen")

    # reset clears the filter
    find("[data-test-id='reset-filters-button']").click

    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).to have_content("Juergen Ohnegleichen")
    expect(find("input[name='term']").value).to eq("")
  end

  scenario "focuses term filter input with Alt+Shift+F" do
    click_on "Orders"

    page.driver.browser.action
      .key_down(:shift)
      .key_down(:alt)
      .send_keys("f")
      .key_up(:alt)
      .key_up(:shift)
      .perform

    expect(page.evaluate_script("document.activeElement.name")).to eq("term")
  end

  scenario "resets filters with Alt+Shift+R" do
    user1 = FactoryBot.create(:user, firstname: "Zoltan", lastname: "Uniquesson")
    user2 = FactoryBot.create(:user, firstname: "Juergen", lastname: "Ohnegleichen")
    create_order(target_user: user1)
    create_order(target_user: user2)

    click_on "Orders"

    find("input[name='term']").set("Zoltan")
    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).not_to have_content("Juergen Ohnegleichen")

    page.driver.browser.action
      .key_down(:shift)
      .key_down(:alt)
      .send_keys("r")
      .key_up(:alt)
      .key_up(:shift)
      .perform

    expect(page).to have_content("Zoltan Uniquesson")
    expect(page).to have_content("Juergen Ohnegleichen")
    expect(find("input[name='term']").value).to eq("")
  end
end
