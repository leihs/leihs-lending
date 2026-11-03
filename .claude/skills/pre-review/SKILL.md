---
name: pre-review
description: Machine review of the current branch before requesting human PR review. Runs repo path rules, /code-review (fresh-context fork, REVIEW.md rules applied) and /security-review and prints a local report. Optional arg - review effort level (low|medium|high|xhigh|max), default medium.
disable-model-invocation: true
allowed-tools: Bash(git fetch:*), Bash(git rev-parse:*), Bash(git diff:*), Bash(git merge-base:*), Bash(git status:*), Bash(git log:*)
---

# Pre-review

Review the change set of the current branch and print a report. Judge only by the code and the docs below, not by intent stated earlier in the conversation. Do not edit files, commit, or post anything.

Effort level for `/code-review`: `$ARGUMENTS` — if empty, use `medium` (never omit it; `/code-review` would reuse the user's last level).

## 1. Scope

1. `git fetch origin master`
2. `git rev-parse --verify -q origin/HEAD` (needed by `security-review`). If it fails, stop and tell the user to run `git remote set-head origin -a`.
3. `BASE=$(git merge-base origin/master HEAD)`
4. Change set = commits `BASE..HEAD` plus uncommitted changes: `git diff --name-status $BASE` and `git status --porcelain`.
5. Read `REVIEW.md` and the "Backend Guidelines" section of `README.md`.
6. Drop paths listed under "Skip" in `REVIEW.md`. If nothing is left, report "0 findings — nothing to review" and stop.

## 2. Path rules

Check the changed paths:

- Changes under `src/leihs/lending/client/components/ui/` outside `customized/` → **Important** (vendored shadcn components).
- Added, removed or renamed examples in `spec/features/**/*_spec.rb` → **Important** unless `cider-ci/generators/feature-tasks.yml` changed accordingly. Fix: `cider-ci/generators/bin/feature-tasks-create`.
- `database` or `shared-clj` submodule pointer changed → check the submodule diff (`git diff $BASE -- database shared-clj --submodule=log`, then migrations/schema changes) against lending code; unhandled renames/drops → **Important**.

## 3. Code review

Invoke the bundled `code-review` skill via the Skill tool. `args`: the effort level above followed by `origin/master...HEAD`. A range excludes uncommitted changes; if there are any, list them under "Not covered". It runs in the background: wait for its completion notification before writing the report; never report without it.

Apply the `REVIEW.md` rules to its findings: what counts as a bug, skip paths, nit cap, file:line evidence, Important / Nit / Pre-existing tags.

## 4. Security review

Invoke the bundled `security-review` skill via the Skill tool. It diffs the branch against origin's default branch; if `git status --porcelain` is non-empty, add a warning to the report that uncommitted changes were not security-reviewed.

## 5. Fail loudly

If `code-review` or `security-review` cannot be invoked, stop and report exactly which one is unavailable and the error. Do not substitute your own review.

## 6. Report

Print only this, nothing else:

```
## Pre-review: <N> findings
<"0 findings — ready for human review." when N = 0>

### Path rules
### Important
### Nit (max 5)
### Pre-existing
### Security

- `path/file.clj:123` — one-line reason

### Not covered
<changed files code-review skipped (e.g. Markdown, specs) and uncommitted files security-review skipped>
```

Omit empty sections. N counts all findings except Pre-existing.
