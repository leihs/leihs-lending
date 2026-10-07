# Edit order feature (skeleton)

Route is as expected: `/lending/POOL-ID/orders/ORDER-ID`

See the screenshot for how it should look and what it should contain. More info here:

- "Bestellung ablehnen" and "Bestellung genehmigen" are wired to the _existing_ "reject/approve order" dialogs
  - The angle on the "approve order" button contains a sub command "approve with comment". Unlike the default "approve" command it should not try to post the mutation directly and open the dialog only in case of error, but always open the dialog.
- The small button with two arrow is for the swapOrderUser command
  --> stub toast, we will implement that later
- The pen button beneath "Zweck Bestellung" is for the updateOrderPurpose command
  --> stub toast
- The form (date, date, search field, "add via catalog" button) are for selecting a model and creating a new reservation
  --> unwired form as for now
- The "Auswahl editieren" button will open the reservation calendar for the selected reservation lines
  --> stub toast
  - Sub commands: "Auswahl ausdrucken", "Auswahl löschen"
    --> stub toast
- List of ReservationLines. Note that so far we used alternate query :reservations and grouped it in frontend. Here we must query :reservationLines because we need availableQuantity.
  - col 1: checkbox to select the line
  - col 2: the numbers (e.g. "1 / 3") are "quantity / availableQuantity"
  - col 3, 4, 5: model image, badge and name: implement like in _inventory_ module (file system: `../inventory`), path /inventory/POOL-ID/list/
  - col 6: button "Eintrag ändern", with sub commands "Zeitleiste", "Modell tauschen", "Löschen"
    --> stub toast

### Agent's summary

Route `/lending/:pool-id/orders/:order-id`.

**Backend** (only what the page needed — `reservationLines` already existed):

- `resources/schema.edn` — `:Model` gained `type`, `isPackage`, `thumbnailUrl`
- `resources/models.clj` — `get-thumbnail-url`: base64 data URL of the cover image's thumbnail, else any thumbnail on the model
- `graphql/queries.clj` — registered `:model-thumbnail-url`

**Frontend** (new `routes/pools/orders/edit/`):

- `page.cljs` — loader data + selection state
- `components/header.cljs` — title + user (suspension icon), purpose, swap/pen stub buttons, "Bestellung ablehnen" / "Bestellung genehmigen" split button
- `components/toolbar.cljs` — the unwired add-reservation form (two date pickers with local state, search input, "Hinzufügen via Katalog") and "Auswahl editieren" with selection count + sub commands
- `components/lines.cljs` — `reservationLines` grouped by start date, then by date range; columns checkbox / `quantity / availableQuantity` / image / badge / name / "Eintrag ändern" split button
- New shared `ModelImage` + `ModelBadge` (inventory-style), vendored shadcn `checkbox.jsx`, `format-weekday-date`, de/en translations

**Reused dialogs:** `ApproveFailedDialog` became `ApproveDialog` — it now also serves the "Mit Kommentar genehmigen" sub command, which always opens it (without `force`); the refusal alert, the "Trotzdem genehmigen" wording and `force` only appear when the approval was actually refused, including when the deliberate one gets a 422. Both dialogs took an `on-success` prop so the edit page returns to the list instead of revalidating.

Decisions worth a look:

- **Wired two entry points** that were stub toasts, since the page is otherwise unreachable: the list row's "Editieren" menu item and the failure dialog's "Bestellung editieren". `spec/features/orders/approve_spec.rb` was updated accordingly (the "not available yet" scenario now asserts navigation; test ids `approve-failed-dialog`→`approve-order-dialog`, `approve-order-force`→`approve-order-submit`).
- `pools/layout.cljs` now derives the tab/breadcrumb from the path segment after the pool id instead of the last one — otherwise the order id broke both.
- Toolbar date fields default to today/today; they're unwired, so there was no non-arbitrary default to pick.
- `checkbox.jsx` is left prettier-unformatted to match the 24 other vendored `ui/*.jsx` files.

Verified: `clojure -M:cljfmt check` clean, GraphQL schema compiles with the new fields, `npm run build` clean (0 warnings), new Tailwind classes generate. Specs were **not** run (`DB_NAME=leihs_test bin/rspec spec/features/orders spec/graphql`). No feature spec for the new page yet.

Noticed but left alone: `orders/get-one` isn't scoped by `pool-id`, so `order(id:)` reads across pools — pre-existing, but this page is a new surface for it.
