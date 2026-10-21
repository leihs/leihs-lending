# Review rules

Review-only rules for AI reviewers (`/pre-review`, hosted Code Review). Coding conventions live in `CLAUDE.md` and `README.md` → Backend Guidelines.

## What a bug looks like here

- Violation of a rule in `CLAUDE.md` or `README.md` → Backend Guidelines.
- `database/` or `shared-clj/` changes (incl. submodule bumps) that lending code doesn't account for, e.g. a migration renaming/dropping a column still used here.

## Skip

- `target/`
- `node_modules/`
- `resources/public/lending/assets/`
- `src/leihs/lending/client/components/ui/` except `customized/` (only flag that it was edited)
- `package-lock.json`, `Gemfile.lock`

## Findings

- Tag each: **Important** (bug or rule violation introduced by the diff), **Nit**, **Pre-existing** (not introduced by the diff).
- Max 5 nits. None for pure formatting.
- Cite `file:line` for every finding; verify in the code, don't infer from names.
- Summary starts with the finding count; say explicitly when there is nothing to do.
