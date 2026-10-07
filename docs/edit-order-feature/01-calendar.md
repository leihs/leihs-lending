# Edit order - calendar

There are two mocks:

- calendar-one-model.png
  - change the date range and quantity of reservations for one reservation line in the current order (i.e. for one existing model/date-combination). Current quantity and available quantity is shown in the day cells.
- calendar-multi-model.png
  - change the date range for multiple reservation lines. Quantities are neither shown nor are editable. The calendar only shows a boolean "available" for each day (i.e. that the selected reservations can be fulfilled on this date)

You can learn about feature from the old application `../legacy`

### Agent's summary

Rolled our own grid on `date-fns` rather than building on `react-day-picker`: the
design replaces every renderer the library offers (`Day`, `DayButton`, `Nav`,
`Weekday`, `WeekNumber` and all `classNames` slots) and drives selection through
its own handlers, so only the grid math and keyboard handling would have been
left — and the keyboard part we now own outright. The vendored shadcn
`ui/calendar.jsx` stays untouched and keeps serving the date-filter popover.

No backend changes: `Model.availability` and the root `availability(reservationIds:)`
query already cover the two mocks exactly.

**New**

- `lib/calendar.cljs` — `grid-weeks` (whole ISO weeks, Monday first, always six
  of them so the grid keeps its height), `grid-range`, `day-key`, range
  predicates, and locale weekday/month names via `Intl.DateTimeFormat` (the
  source the app already uses for dates)
- `components/booking_calendar/grid.cljs` — `BookingCalendar`: KW column, every
  day labelled with its month, restriction icons, `role="grid"` with roving
  tabindex and ←→ ±1 day / ↑↓ ±1 week / Home / End / PageUp / PageDown, crossing
  the month boundary. A range is picked by clicking its start, hovering for the
  preview and clicking its end; Escape abandons
- `components/booking_calendar/cells.cljs` — `QuantityCell` (available quantity,
  total in a badge, negatives in destructive red) and `AvailabilityCell` (✓/✗);
  the only difference between the two mocks
- `components/booking_calendar/month_nav.cljs` — prev/next plus month and year
  selects, in the dialog toolbar as in the mock
- `routes/pools/orders/edit/components/calendar_dialog.cljs` — one line with a
  model → `Model.availability` with `excludeReservationIds` (the edited
  reservations taken out, as legacy's `withoutLines` did) and an editable
  quantity; anything else → the root query folding all models into one verdict
  per day. Refetches per visible month

**Changed:** `orders/data.cljs` (two availability queries plus their hooks),
`edit/page.cljs` (owns the dialog and the lines it opens with), `toolbar.cljs`
("Auswahl editieren" now wired), `lines.cljs` ("Eintrag ändern" now wired),
de/en translations.

Decisions worth a look:

- **Saving is a stub toast.** There is no mutation for changing a reservation's
  date range or quantity — `mutations.clj` has create/delete/swap-model but no
  update. That is what the feature still needs.
- **The "Ausleihende/r" select is disabled**, holding only the borrower. It maps
  to the API's `userId` vs `entitlementGroupId`, but nothing exposes a model's
  entitlement groups yet (legacy fetched `Partition` + `Group`). Marked TODO.
- **The date fields are read-only displays.** With the grid as the range picker,
  making them editable too means re-implementing legacy's `validateDate` /
  `resetDate` / `validateDateLogic`.
- **The month abbreviation shows in every cell**, not only on days from an
  adjacent month, in a lightened `text-muted-foreground/60`.
- **`NON_WORKDAY` and `HOLIDAY` share one `EyeClosed` icon** (lucide has it, the
  designer's "closed eye"), so a day that is both shows it once with both names
  joined into its `aria-label`; `VISITS_CAPACITY_REACHED` keeps its own `Users`
  icon. The icons stay black on `text-foreground` while the rest of a closed
  cell fades to `text-muted-foreground/40`.
- **Past days carry nothing but their date** — no availability content, no
  restriction icons, no gray fill — and stay unpickable.
- **The day-number chip sits flush in the cell's top-left corner**, rounded only
  at the bottom-right, on `bg-muted-foreground/25`. It keeps its own
  `text-foreground` so the number stays black on closed, adjacent-month and past
  days alike, where everything around it dims. The cell's padding therefore
  lives on the content area below, not on the button.
- **The quantity number has three levels**: full strength inside the picked
  range, `opacity-50` outside it, `opacity-25` on a closed day. Set as opacity
  rather than a lighter colour so overbooked days keep their `text-destructive`
  hue instead of turning gray. The grid passes `:in-range?` and `:closed?` into
  `render-cell` for this, so cell renderers can react to cell state without the
  grid knowing what they draw.
- Days before today cannot be picked and the navigation does not go back past
  the current month, matching legacy's `toggleGoBack`.
- Day buttons carry the formatted date as `aria-label`; the quantities inside
  stay visual only.
- **The grid is always six weeks**, as legacy's was, padded with the following
  month's days. Since the fixed height lives in `grid-range`, the dialog's
  availability query covers the padded week too, so those days carry real data
  and are pickable like any other — they only render as outside days.
- The total-quantity badge is white on `bg-gray-400`. That is about 2.2:1
  contrast, under WCAG AA for text; kept on the designer's call, worth revisiting.
- The dialog toolbar is one non-wrapping row, bottom aligned so the labelled
  quantity field lines up with the unlabelled ones. It needs ≈862px against the
  932px the dialog leaves inside its padding.

Verified: `clojure -M:cljfmt check` clean, `npm run build` clean (0 warnings),
new Tailwind classes generate. The grid math was checked against date-fns over
84 months (2024–2030): always exactly 6 weeks / 42 days, every week 7 days,
Monday→Sunday, every day of the subject month present, no duplicates across DST,
no grid spanning more than three months, and ISO week numbers correct at the
year boundaries (28.12.2026 → KW 53, 04.01.2027 → KW 1). Specs were **not** run
(`DB_NAME=leihs_test bin/rspec spec/features/orders`) and the app was not
started. No feature spec for the calendar yet.
