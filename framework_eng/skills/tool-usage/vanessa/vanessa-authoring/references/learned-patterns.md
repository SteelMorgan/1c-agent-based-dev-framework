# Learned Patterns — vanessa-authoring

Proven practices and antipatterns accumulated from real tasks.
`confirmed` — confirmed rule, `candidate` — requires re-verification.

---

```
status: confirmed
area: document form filling
technique: before saving/posting the document fill all required fields —
       they are visually marked with a red dashed underline (red dashed underline)
anti-pattern: trying to save/post the document with unfilled required fields
why: platform 1С blocks the save and raises an error; the test fails on the save/post step
steps: |
  1. Open the document form
  2. Visually determine the required fields (red dashed underline)
     or use screenshot + visual-check
  3. Fill ALL required fields
  4. Only then execute "Save" / "Post"
source: universal behavior of platform 1С:Предприятие
```

---

```
status: confirmed
area: order of filling fields
technique: fill the form fields from left to right, top to bottom — in the order they appear visually. This matters because the value of one field may affect the availability or content of the next fields (autofill, filtering dropdowns, field availability).
anti-pattern: filling fields in arbitrary order or starting from the lower fields
why: 1С form fields are tied to event handlers — when a higher-level field changes, lower ones may clear, refill, or become unavailable. Breaking the order leads to loss of entered data or an incorrect form state.
source: universal behavior of platform 1С:Предприятие
```

---

```
status: confirmed
area: document header filling
technique: before writing the scenario, query how similar documents are populated in the database. Sort by date DESC, filter by already known fields (from the task), and take the FIRST 5-10 records. This yields real field values, correct combinations of requisites, and hints about which fields are required.
anti-pattern: guessing field values or filling with arbitrary data
why: document header fields are often linked (organization → warehouse → price type); the wrong combination causes an error during save/post. Fresh documents show the currently allowed combinations.
steps: |
  1. Determine the document type from the task
  2. Run the query: SELECT TOP 10 ... FROM Документ.{Тип} ORDER BY Дата DESC
     with filters on known fields (counterparty, organization, etc.)
  3. Study the populated values — which fields are filled, which combinations are used
  4. Use real values in the scenario
source: universal technique for working with 1С data
```

---

```
status: confirmed
area: diagnosing errors on the document form
technique: 1С displays messages at the bottom of the screen. Evaluate each:
       error → investigate the cause (otherwise save/post stay blocked);
       informational → can be ignored.
       There is no explicit visual distinction between "error" and "information" —
       rely on the message text (presence of words like "error", "not filled", "incorrect",
       negative context).
anti-pattern: ignoring all messages or treating every message as an error
why: an unnoticed error in the messages causes the test to fail on the next step
        (save/post); a false alarm on an informational message wastes time
steps: |
  1. After any action on the form (filling, saving, posting) — check the message area
     at the bottom of the screen (screenshot / visual-check)
  2. If a message looks like an error but the meaning is unclear — search the code for the text:
     a) document form module
     b) object module
     c) manager module
     d) global keyword search
  3. When searching, account for templated text: the error may contain substituted
     values (nomenclature names, counterparties). Search by the keywords that define
     the error nature rather than by specific names.
source: general behavior of platform 1С:Предприятие
```

---

```
status: confirmed
area: closing a modified form
technique: the "*" symbol in the form title indicates unsaved changes.
       When closing such a form the platform shows the dialog "Данные были изменены.
       Сохранить изменения?" with buttons "Да / Нет / Отмена".
       If saving is not required by the test conditions — click "Нет".
       If saving is required — save first, then close.
anti-pattern: closing a modified form without handling the confirmation dialog —
           the test will hang on the modal window
why: the modal dialog blocks all actions; Vanessa cannot perform the next step and the test will hang due to a timeout
steps: |
  1. If the form is modified (there is "*" in the title) and saving is unnecessary:
     And I click the form button "Close"
     Then the "1С:Предприятие" window opens
     And I click the form button "No"
  2. If saving is required:
     And I click the form button "Save"
     And I click the form button "Close"
source: general behavior of platform 1С:Предприятие
```
