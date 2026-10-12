# Known scenarios

The index the **create-test-case** agent and the **testing-activity** procedure read **first**: if a scenario is here
with its steps and expected results, it is **known** - use it. If it is not, walk it in the app logged in
(**login-mapping** → `web/webOpenLoggedIn` → `tools/browser.js` in the same session) and add a row here **before**
writing test cases. A later run then finds it known.

How to add a row:
- **Scenario** in the actor's words; **Actors** (each role that acts or checks the result); **Steps** in business
  words (no XPaths, no class names); **Expected** - the real results seen on the test env (texts as the app shows
  them, statuses, figures); **Rules / source** with their source `(walk <env> <yyyy-MM-dd>)`, `(story <id>)`;
  **API calls seen** - the calls each step made (the set-up / clean-up candidates, details in **api-catalog**);
  **Module** of this skill; **Suite** that runs it, or `-`.
- One row per scenario; variants (negative, language, setting on / off, another role) go in the Rules cell, or as
  their own rows when the steps differ.
- No passwords, tokens or personal data - users by tag or role only.
- Replace the example row below with the project's first real scenario.

| Scenario | Actors | Steps | Expected | Rules / source | API calls seen | Module | Suite |
|---|---|---|---|---|---|---|---|
| _Example (replace): a doctor creates a patient, uploads a file and shares it with another doctor, who sees it_ | doctor A, doctor B | A: Patients → Add → name + birth date → Save → open the patient → Files → Upload a PDF → Share → pick doctor B. B: Shared with me → open the file | A sees the toast "Patient saved" and the file in the patient's Files list; B sees the file under "Shared with me" with A as the owner | only the owner can share; B can view, not delete `(walk Test <yyyy-MM-dd>)` | `POST /patients`, `POST /patients/{id}/files`, `POST /files/{id}/shares`, `GET /shared-files`; clean-up `DELETE /patients/{id}` (takes the files and shares with it) | `<patients>` | `-` |
