# Decisions about the "Edit order" feature

## Editing reservation lines

### Context

The edit-order calendar changes dates, quantity and models of existing reservation lines.

### Decision

No direct `edit` mutation. The client edits by creating new reservations and deleting the old ones.

### Consequences

- Creates must always come before deletes, because an empty order would be self-deleted
