---
name: business-knowledge
description: What the application under test does as a business - the rules, calculations, permissions and decisions behind its screens - learned from user stories, old tickets and walks on the test environment. Read before analysing a user story, writing test cases, or deciding an expected value. The testing-activity procedure updates it after every story. Not for locators (app-testing) or framework code (automation-framework).
---

# Business knowledge of the app under test

> **Fill this in per project.** Replace this paragraph with two or three sentences: what the product is, who uses it
> (roles), and the main things they do in it.

This skill is the **business** layer: rules, calculations, who can do what, what the stories decided. How a page looks
and its locators are in **app-testing**; the API calls are in **api-catalog**; how to automate is in
**automation-framework**.

## Modules - read the one(s) the story touches

One file per business area, in `modules/`. Start from `modules/_template.md`; add a row here for each.

| Module | File | Covers |
|---|---|---|
| `<area, e.g. Users and registration>` | `modules/<area>.md` | `<what it covers, in one line>` |
| `<area, e.g. Requests and approvals>` | `modules/<area>.md` | |
| `<Settings>` | `modules/settings.md` | the switches that change calculations and which screens show |
| `<Reports>` | `modules/reports.md` | every figure: what it means and how it is calculated |
| Story log | `story-log.md` | one line per story handled: id, module, decision, suite, pull request |

## How to write a rule here

- One bullet per rule, business words first, then its **source** in brackets: `(story 1234 AC)`, `(bug 1301)`,
  `(walk Test 2026-01-15)`, `(decided by <role>, 2026-01-20)`.
- Say when a rule is **decided** (a story, the product owner) vs **observed** (the app does it; it may be a bug). An
  observed rule that contradicts a story is a bug candidate, not a rule.
- Environment differences get their own line (`Stage: ...`).
- Replace a rule that changed, and keep the old one struck through with the story that changed it:
  `~~old~~ → new (story X)`.
- A rule read only from the app's code or an API answer, not walked on a screen, says so
  (`(app code 2026-01-15, not walked)`) until someone confirms it.
- No passwords, tokens or personal data - test users by tag only.
- Keep each module under ~200 lines; split a module that grows past that and add it to the table above.

## When to update

- After reading a story and its tickets (new rules, with the story as source).
- After walking the feature on the test env (observed behaviour; conflicts between story and app).
- After a manual run or an automated run proved or disproved a rule.
- When a bug is fixed and the behaviour changed.

The update rides in the same pull request as the story's automation.
