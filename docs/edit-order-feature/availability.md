## Question

In the second column the second number is the availableQuantity in both applications, but in the new application the number differs for some lines
(it is greater than in the legacy application)

## Answer

The two apps compute the second number differently. Legacy calculates availability with the line's own reservations still booked, then gives back only part of their quantity. Lending recalculates as if those reservations didn't exist. That gap can only push lending's number up, and there's one more effect besides.

**Legacy** (`effectiveAvailableForUser` in `legacy/app/assets/javascripts/manage/modules/line_problems.coffee`, with `withoutLines` in `models/availability.coffee`):

1. It takes the model's availability with all reservations booked, the line's own included.
2. For each of the line's reservations, it adds the quantity back only if the reservation was booked against the order user's own entitlement groups or the general group.
3. If a reservation had borrowed from a group the user isn't in (legacy calls this "soft-overbooked"), it adds nothing back and then subtracts the line's quantity again.
4. Negative results are shown as they are.

**Lending** (`get-lines` in `reservations.clj`, which calls `av/maximum-available-in-pool-and-period-summed-for-groups` with the line's reservation ids to exclude):

- It recalculates the whole availability without those reservations and floors the result at 0 with `(max 0 ...)`.

### Causes, most likely first

1. **Floor at 0.** When a line is overbooked, legacy shows a negative number such as `2 / -1` and lending shows `2 / 0`. This is the most common case where lending's number is higher.
2. **Soft overbooking.** Reservations are booked in creation order, trying the user's groups first, then the general group, then other groups. If a reservation only fits into a group the user isn't in, it borrows that group's quota. Legacy then shows "available for user minus quantity", while lending acts as if the line was never booked. Example: the user's group allows 1, general allows 0, another group allows 2, and the line needs 2:
   - Legacy shows `2 / -1`.
   - Lending shows `2 / 1`.
3. **Recalculating instead of adding back.** Legacy keeps how all other reservations were assigned to groups while the line was still there. Lending's recalculation can move later reservations into different groups. This can push the number either way, so it is a less likely cause of "always higher".

These are not the cause: the user's group membership (both read the same `entitlement_groups_users` data) and the shared availability calculation, which you aligned with legacy in `3651e00`.

### Fix

Calculate the availability once per model with all reservations included, then copy legacy's `withoutLines` and soft-overbooking logic on top, without the floor. That needs the calculation to record which group each reservation was booked against, and it already does (the `running-reservations` list per group). It would probably be a new function next to `maximum-available-in-pool-and-period-summed-for-groups`. The booking calendar (`data.cljs:141` and `calendar_dialog.cljs`) uses the same number, so it would need to switch too.

Do you want me to implement this? And should the new function go into the shared `leihs.core.availability` code or stay local to lending?
