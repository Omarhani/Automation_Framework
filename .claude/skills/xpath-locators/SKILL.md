---
name: xpath-locators
description: Write and verify XPath / CSS locators for this project's Selenium and Appium page objects - unique, readable, and stable across rebuilds and UI languages (including bilingual / RTL apps and Angular Material or other component libraries). Use whenever a locator, XPath, By field or page-object method is needed, or when a locator is flaky, matches the wrong element, or breaks after switching language.
---

# Locators (unique, readable, language-proof)

Goal for every locator: **exactly one match, in every UI language the app ships, still matching after a rebuild.**
Deliver it as a Java `By` field in the project's style (see **automation-framework**), not a bare string.

## 1. Anchor priority (pick the highest one that works)

| # | Anchor | Example |
|---|---|---|
| 1 | Test / app-authored attribute | `//*[@data-testid='save']` · `//input[@name='email']` · `//input[@formcontrolname='firstName']` · `//app-icon[@iconname='delete']` |
| 2 | Component / wrapper tag with a label attribute | `//app-filter-item[@label='Search']//input` · `//*[@aria-label='Close']` |
| 3 | Unique child asset (icon / image file name) | `//button[img[contains(@src,'add-user.svg')]]` |
| 4 | Semantic class token (exact, see §5) | `//button[contains(concat(' ',normalize-space(@class),' '),' btn-confirm ')]` |
| 5 | Visible text - **last resort**; must cover every UI language (§3) | `//button[normalize-space()='Save' or normalize-space()='Enregistrer']` |

CSS is fine (and shorter) when the anchor is a plain attribute: `By.cssSelector("input[name='email']")`. Use XPath
when text, hierarchy, "has a descendant" or a following label is involved.

**Never anchor on:**
- Generated ids: `mat-select-4`, `cdk-overlay-7`, `ng-tns-c31-4`, `react-select-3-input`, `:r5:`. They shift with render order.
- Absolute or near-absolute paths: `/html/body/div[3]/div/div[2]/...`.
- Bare position: `(//button)[7]`. Position is acceptable only *inside* an already-unique scope: `(//div[@role='dialog']//li[contains(@class,'tab')])[1]`.
- Styling-only classes (`col-md-6`, `mb-3`, `text-end`) or anything that looks like a build hash (`css-1x2y3z`).
- `text()=' Save '` with padded spaces - breaks on any whitespace change.

## 2. Scope before you match

An unscoped `//input[@name='userName']` matches the add dialog *and* the edit dialog if both are in the DOM. Prefix with the container:

```
//div[@role='dialog']//input[@name='userName']
//div[contains(@class,'swal2-popup') and not(contains(@class,'swal2-hide'))]//button[...]
//table/tbody/tr[td[3][normalize-space()='user1@example.com']]//button[@aria-label='Edit']
```

Row rules:
- Match the key cell **exactly** (`normalize-space()='X'`), never `contains(text(),'X')` - `user1@` also matches `user12@`.
- If the row mixes `th` and `td`, `td[n]` and `*[n]` are off by one. Use `*[n]` when counting every cell, `td[n]` only after subtracting the leading `th`. Say which one you used in a comment.
- Overlay content (dropdown options, toasts, confirm dialogs, menus) is usually appended to `<body>`, outside the dialog that opened it - do **not** scope those to the dialog.

## 3. Multilingual / RTL text

When text is the only anchor:
- One locator covering every language the tests run in, default language first:
  ```
  //button[normalize-space()='Confirm request' or normalize-space()='<same label in the second language>']
  ```
- Always `normalize-space()`, never `text()`. `text()` takes only the first text node and dies on padded whitespace or a nested `<span>`.
- `normalize-space()` on an element concatenates **all** descendant text. Good for `<button><span>Save</span></button>`; wrong when the element also wraps other text - then target the innermost text-bearing node.
- Partial match when the label carries dynamic data:
  ```
  //h1[contains(.,'Review request') or contains(.,'<second language>')]
  ```
  Pick a substring with no digits, names or counts in it, so it survives data changes.
- Copy non-Latin text straight from the DOM, then strip invisible bidi marks (U+200F, U+200E, U+202B ...). They are invisible in a diff and make the match fail silently - check this first when a copied RTL string won't match.
- Java source must be UTF-8. Write the XPath as a double-quoted Java string with single quotes inside.
- Don't hardcode a translation that may be re-worded. If an icon, a form-control name or a wrapper `@label` identifies the same element, use that and skip the text entirely.
- RTL layouts: wide tables scroll sideways and cells can sit off-screen (negative x); scroll the element into view before clicking or reading (the helpers do; see `getTextUsingHorizontalScroll`).

## 4. Verify uniqueness before handing it over

In the browser (Claude in Chrome / the built-in browser, or the user's devtools console):

```js
x => document.evaluate(x, document, null, 7, null).snapshotLength   // must be 1
```

Check it **in every UI language** and in the state the test will be in (dialog open, row present).
- `0` -> wrong anchor, or the element is not rendered yet.
- `>1` -> add a scope (§2), not an index.

Report the count. Never present an unverified locator as verified - mark it `(not verified)` when there was no way to run it.

Mobile (Appium): take the tree with `adb shell uiautomator dump /sdcard/ui.xml` + `adb exec-out cat /sdcard/ui.xml`
(or Appium Inspector) and anchor on `resource-id`, then `content-desc` (`AppiumBy.accessibilityId`), then text.
Flutter apps expose their semantics labels / keys as `content-desc` / `resource-id`; only the on-screen part of a list
is in the tree.

## 5. XPath idioms worth memorising

| Need | Write |
|---|---|
| Exact class token | `contains(concat(' ',normalize-space(@class),' '),' btn-primary ')` |
| Has a descendant | `//button[.//*[name()='svg' and @data-icon='trash']]` |
| Has a direct child | `//button[img[contains(@src,'export.svg')]]` |
| Field next to a label | `//label[normalize-space()='Country']/following::input[1]` |
| Sibling error message | `//input[@name='userName']/following-sibling::div[contains(@class,'error')]` |
| Row from a cell value | `//table/tbody/tr[td[3][normalize-space()='VALUE']]` |
| Not in a hidden state | `//div[contains(@class,'modal') and not(contains(@class,'hide'))]` |
| Case-insensitive (Latin only) | `translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='save'` |
| SVG element | `//*[name()='svg']` (plain `//svg` does not match in HTML documents) |

## 6. Deliverable format

Project style: `src/main/java/pages/*Page.java`, `extends utils.MethodHandlesWeb` (mobile: `screens/*Screen.java`,
`extends utils.MethodHandlesMobile`, `AppiumBy...`).

```java
// scoped to the dialog; verified 1 match in both UI languages
private final By saveButton =
        By.xpath("//div[@role='dialog']//button[normalize-space()='Save' or normalize-space()='<second language>']");

public void clickSave() {
    click(saveButton, 10);
}
```

- `private final By`, camelCase, named for the element and its role (`addUserButton`, `firstNameInput`, `statusFilterSelect`) - not `btn1`, and not the translated label.
- One short action method per locator, matching the existing naming (`insertFirstName`, `clickSave`, `deleteButtonUsingEmail(email)`), default timeout `10`.
- Parameterise row locators with a method, keeping the exact-match brackets:
  ```java
  private By rowByUserName(String userName) {
      return By.xpath("//table/tbody/tr[td[3][normalize-space()='" + userName + "']]");
  }
  ```
- Add a one-line comment only when the locator is non-obvious (scope, cell offset, verification status).

## 6b. Styled controls: the element you see is not the element you match

Component libraries (Angular Material, Bootstrap custom controls, many design systems) build switches, checkboxes and
radios out of a **hidden real input plus a decorative label**. The locator that reads naturally is often the one
Selenium cannot use:

| Symptom | Cause | Fix |
|---|---|---|
| `"element not interactable"` on a control that is plainly on screen | The node is **W x 0** (or 0x0): its body is drawn by a `::before` / `::after` pseudo element, which adds nothing to the bounding box | Match the nearest ancestor that has real size, and click that |
| `isDisplayed()` false on an `input[type=checkbox]` / `radio` | `display:none` / `opacity:0` - the input only holds state | Read `.checked` with JS; never wait on it, never click it |
| Click "succeeds" but nothing changes | JS `click()` fired on the **wrapper**, which does not activate the label the way a real click does | Use a native click on the sized wrapper, not a JS fallback |

Check size and hit target before trusting a click target:

```js
x => { const e = document.evaluate(x,document,null,9,null).singleNodeValue;
       const r = e.getBoundingClientRect();
       return [r.width, r.height,
               document.elementFromPoint(r.left+r.width/2, r.top+r.height/2).tagName] }
```

Zero height means pick the parent. `elementFromPoint` tells you which node a real click at that centre actually
reaches - if it is the label, the checkbox will flip. Typical case: a toggle's `label[for=...]` is 40x0 and unusable,
while its wrapper `div.toggle-switch` is 40x20 and a centre click there lands on the label.

**Custom dropdowns** (`mat-select`, react-select, ng-select ...) are not `<select>`: `selectByVisibleText` does not
work. Click the trigger, then click the option in the overlay (`//mat-option[normalize-space()='Text']`,
`//*[@role='option'][normalize-space()='Text']`). A JS `.click()` on the host often does not open them - use a real click.

**Lazily rendered containers.** A collapsed card, an unopened tab or a closed panel in Angular / React is often
*absent from the DOM*, not hidden. `0 matches` there means "not rendered yet", not "wrong XPath" - open the container
first. In this project `isDisplayed(By, int)` (web) **throws** on a missing element rather than returning false, so an
"is it open?" check needs `webElements(by).isEmpty()`.

## 7. Fixing an existing locator

When asked why one is flaky, check in this order and name the cause:
1. Generated id (`mat-select-N`, `cdk-*`, `:rN:`) -> replace with a label / name / test-id anchor.
2. Wrong cell index (`th`/`td` offset) -> recount with `*[n]`.
3. `contains()` on a key that has longer siblings -> make it exact.
4. Padded-space `text()` -> `normalize-space()`.
5. Single-language text -> add the other language's branch, or drop text for an attribute.
6. Unscoped, matching a second open panel -> scope it.
7. Zero-sized or `display:none` target -> §6b, match the sized ancestor.
8. Matches fine but fails at runtime -> not a locator bug. It is a wait (a dialog that opens before its fields exist),
   an animation, an overlay / spinner intercepting the click, or browser autofill; say so instead of rewriting the XPath.

## 8. Checklist before replying

- [ ] Highest-priority anchor available (§1), no generated id, no absolute path
- [ ] Scoped to its container or row
- [ ] Works in every UI language, or is language-independent
- [ ] Match count is 1 - verified, or explicitly marked unverified
- [ ] For a click target: non-zero size, and `elementFromPoint` at its centre reaches it (§6b)
- [ ] Delivered as a named `By` field plus an action method
