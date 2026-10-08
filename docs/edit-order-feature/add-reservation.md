# Add reservation line — missing backend pieces

The model search field of the order edit page (`edit/components/model_search_field.cljs`) uses the existing `models(term:)` query. Everything else is stubbed client-side in `edit/data.cljs` (`stub-availability`, `stub-templates`).

## 1. Availability for the chosen period

Each model row shows `X(Y)/Z`, as in legacy:

- **X**: minimum over the period, for the order user's entitlement groups plus the general group. Rendered red when X = 0.
- **Y**: minimum over the period, summed over all groups.
- **Z**: number of borrowable items in the pool (`inventory_pool_id` = pool, not retired, borrowable). This one doesn't depend on the period.

Suggestion: a field on `Model` that takes `startDate`, `endDate` and `userId` and returns these three numbers. Rationale:

- `Model.availability` already computes X and Y per day, but calling it for 20 models on every keystroke is too heavy.
- Z doesn't exist yet.

Legacy computes all three from `GET /manage/:pool_id/availabilities?model_ids[]=…&user_id=…` (`app/controllers/manage/availability_controller.rb`, `app/assets/javascripts/models/availability.coffee`).

## 2. Templates

- A `Template {id name}` type.
- A `templates(term:)` query: every search word must appear in the name, sorted by name, at most 5 results.
- A `createTemplateReservations(orderId, userId, templateId, startDate, endDate)` mutation. For each model in the template that the pool has, it creates `link.quantity` reservations, each with quantity 1. Legacy: `POST /manage/:pool/reservations/for_template`.

## 3. Sorting

`models/get-multiple` has no `ORDER BY`. Legacy sorts the results by name.

## Already available

- **Search scope:** `models(term:)` matches each search word against manufacturer, product and version and returns at most 20 models with lendable items. That's the same scope as legacy.
- **Adding a model:** `createModelReservation`.
- **Enter with an inventory code:** in legacy this adds the item's model. `createReservationByInventoryCode` covers it.
- **Options:** legacy doesn't offer options on the order edit page either.

## Frontend follow-ups once the backend exists

- Pass the dates and the order's user to the search field and include them in the query variables.
- Remove the stubs in `edit/data.cljs`.
- On selecting an entry, call the mutation instead of the stub toast.
- Pressing Enter with no entry highlighted should resolve the text as an inventory code.
