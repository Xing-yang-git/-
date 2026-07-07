---
name: git-commit-standard
description: Use when committing code, creating branches, or writing commit messages — enforces Conventional Commits format, scope rules for multi-module projects, and the split-vs-combine decision framework. Use when the user says "保存版本", "git保存", "commit", "提交", "打个点", "git save", or when git operations involve writing commit messages.
---

# Git Commit Standard

## Overview

Systematic git commit discipline for multi-module projects. Enforces [Conventional Commits](https://www.conventionalcommits.org/) format with a decision framework for when to split commits by module versus combine them into one.

This skill is **project-agnostic** — it defines the methodology. The calling agent or project context provides the specific module names, scope prefixes, and branch conventions.

## Core Principle

**One commit = one complete, independently reviewable, independently revertible change.**

A commit should tell a coherent story. If you need to explain "part 1 of 3" in the message, split it wrong. If reverting the commit would leave the codebase in a broken state, the commit is incomplete.

## Conventional Commits Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type (required)

| Type | When to use |
|------|------------|
| `feat` | New feature or functionality |
| `fix` | Bug fix |
| `refactor` | Code restructuring (no behavior change) |
| `style` | Formatting, CSS, whitespace (no logic change) |
| `docs` | Documentation only |
| `test` | Adding or updating tests |
| `chore` | Build, dependencies, tooling |
| `perf` | Performance improvement |
| `ci` | CI/CD configuration |
| `revert` | Reverting a previous commit |

### Scope (optional but recommended for multi-module projects)

Scope identifies which module(s) the commit affects. Rules:

1. **Single module changed** → single scope: `feat(server): add rating endpoint`
2. **Multiple modules, same feature** → comma-separated: `feat(admin,server): add rating audit page`
3. **All modules or cross-cutting** → no scope: `feat: launch rating system`
4. **Documentation/config** → no scope or the file area: `docs: update deploy guide`

The calling agent defines the valid scope values based on project structure.

### Subject (required)

- Maximum 72 characters
- Imperative mood ("add" not "added", "fix" not "fixed")
- No period at end
- Describe WHAT and WHERE, not HOW
- Use the module name if scope doesn't capture it

### Body (optional, recommended for non-trivial changes)

- Blank line between subject and body
- Wrap at 72 characters
- Explain WHY this change exists, WHAT the approach is, and any non-obvious consequences
- Reference issues, PRs, or design docs

### Footer (optional)

- `BREAKING CHANGE:` prefix for breaking API changes
- `Closes #123` or `Refs #456` for issue tracking

## Split vs Combine Decision Framework

This is the core decision for multi-module projects. Use this ordered checklist:

### Step 1: How many modules are changed?

```bash
git diff --name-only --cached    # staged
git diff --name-only             # unstaged
```

Map changed files to modules (calling agent provides the mapping).

**One module only** → no decision needed, commit with that module's scope.

**Multiple modules** → proceed to Step 2.

### Step 2: Are the changes causally related?

Ask: "Does module A's change MAKE SENSE without module B's change?"

```
Example 1 — related (combine):
  server: add GET /api/ratings endpoint
  admin: add RatingView.vue that calls GET /api/ratings

  → The admin page is useless without the endpoint.
  → COMBINE: feat(admin,server): add rating management page

Example 2 — unrelated (split):
  server: fix NPE in AuthService
  miniprogram: fix button border-radius

  → The NPE fix and the UI fix have nothing to do with each other.
  → SPLIT: fix(server): guard null user in AuthService
            fix(miniprogram): correct button border-radius
```

### Step 3: Would splitting leave the codebase in a broken state?

Ask: "If I revert commit A but keep commit B, does anything break?"

```
Example — must combine:
  server: rename column "status" to "item_status" in schema.sql
  server: update all references from status to item_status
  admin: update API field from "status" to "item_status"

  → Splitting means the schema change is in one commit and the code
    updates are in another. Reverting the schema commit alone breaks
    everything. They MUST be one atomic commit.
  → COMBINE: refactor(admin,server): rename status to item_status
```

### Step 4: Default rule when uncertain

```
When in doubt:
  Prefer SMALL commits. A commit that's too large is harder to review
  than two commits that are slightly related but independently coherent.

  But never split if splitting creates a broken intermediate state.
```

## Branch Naming Convention

```
<type>/<short-description>

Examples:
  feat/rating-system
  fix/login-npe
  refactor/dto-separation
  chore/spring-boot-upgrade
```

- Lowercase
- Hyphen-separated words
- Start with type prefix matching the eventual commit type
- Keep under 50 characters
- No issue numbers in branch names (put those in commit footers)

## Pre-Commit Checklist

Before every commit, verify:

```
[ ] No hardcoded passwords, tokens, or API keys
    → grep -nRiE "(password|secret|token|api[_-]?key)\s*[:=]\s*['\"][^'\"]{4,}" $(git diff --cached --name-only)

[ ] No commented-out code (use git history, not comments)
    → grep -n "^[[:space:]]*//.*;\|^[[:space:]]*/\*.*;\|^[[:space:]]*\*.*;" $(git diff --cached --name-only)

[ ] No console.log / System.out.print /调试日志
    → grep -n "console\.\(log\|debug\)\|System\.out\.print" $(git diff --cached --name-only)

[ ] Files are staged, not just modified
    → git diff --name-only returns empty (everything is staged)

[ ] Commit message matches Conventional Commits format
    → type(scope): subject (imperative, ≤72 chars)

[ ] If multi-module: split-vs-combine decision is documented in body
```

## Examples

### Good

```
feat(server): add item rating endpoint

POST /api/ratings — create or update rating (1-5 stars + comment)
GET /api/ratings/user/{id} — get all ratings for a user
Rating entity linked to borrow_request_id for audit trail

Refs #42
```

```
fix(miniprogram): prevent double-submit on publish form

Added formState lock that prevents the submit button from being
tapped twice before the first request completes. Also added a
300ms debounce on the submit handler as defense-in-depth.
```

```
feat(admin,server): add batch audit approval

Admin can now select multiple pending items and approve/reject
them in one action. Backend uses a single transaction — if any
individual approval fails, the entire batch is rolled back.

BREAKING CHANGE: POST /api/admin/audit now accepts array
instead of single object.
```

```
style(miniprogram): unify button border-radius to 25rpx

Prototype specifies 25rpx for all primary action buttons.
Replaced 12 hardcoded border-radius values with var(--radius-pill).
```

### Bad

```
fix: fix bug                    ← Too vague. What bug? Where?
feat: update                    ← Update what?
fix(server): fixed login error  ← Past tense. "fix" not "fixed".
added feature x                 ← No type prefix at all.
wip                            ← Not informative. Squash before pushing.
```

## Commit Granularity Guidance

| Files changed | Action |
|---------------|--------|
| 1 file | Usually fine as one commit |
| 2-5 files, same module | One commit unless they touch unrelated concerns |
| 2-5 files, multiple modules | Evaluate split-vs-combine (Step 2+3) |
| 6-15 files, same module | Re-read diff — can this be logically split? |
| 6-15 files, multiple modules | Almost certainly should be split unless it's one coherent feature |
| 15+ files | Strongly consider splitting into multiple commits by concern |

This is guidance, not a hard rule. A 20-file rename refactor is one coherent commit. Two 3-file changes in unrelated modules should be two commits.

## Common Mistakes

| Mistake | Why it happens | Fix |
|---------|---------------|-----|
| `git add .` + massive commit | Laziness, "save everything" reflex | `git add -p` to stage hunks interactively |
| Vague subject line | Didn't re-read before committing | Ask: "Would someone reading `git log --oneline` understand this?" |
| Mixing refactor + feature in one commit | "While I was in there..." | Stash or stage selectively — refactor first, then feature |
| Past tense subject | Following prose conventions | Read the subject as "This commit will ___" |
| No body on complex change | Rushing | If the diff needs explanation, so does the message |
| Committing dead code | "Might need it later" | Delete it. Git history keeps it if needed. |
| Forgetting to stage new files | `git commit -a` habit | `git add` explicitly, then `git status` before committing |
| Committing generated files | Forgot to add to .gitignore | Check `git status` for `target/`, `dist/`, `node_modules/` |

## The Bottom Line

**Commit messages are for the developer six months from now who's debugging a production incident at 3am.** That developer might be you. Give them clear history, atomic reverts, and enough context to understand WHY without reading the full diff.
