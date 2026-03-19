# Learned Patterns — vanessa-authoring

Proven practices and antipatterns gathered from real tasks.
`confirmed` — verified rule, `candidate` — requires re-verification.

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
  4. Only after that execute "Save" / "Post"
source: general behavior of platform 1С:Предприятие
```

---

```
status: confirmed
area: document header filling
technique: before writing the scenario — inspect how similar documents are filled in the database by running a query. Sort by date DESC, filter by already known fields (from the task), FIRST 5-10 records. This gives real field values, correct combinations of requisites, and hints which fields are required.
anti-pattern: guessing field values or filling with arbitrary data
why: in the document header the fields are often linked (organization → warehouse → price type); an incorrect combination causes an error during save/post. Fresh documents show the currently allowed combinations.
steps: |
  1. Determine the document type from the task
  2. Run the query: SELECT TOP 10 ... FROM Документ.{Тип} ORDER BY Дата DESC
     with filters on known fields (counterparty, organization, etc.)
  3. Study the filling — which fields are set, which combinations are used
  4. Use real values in the scenario
source: universal technique for working with 1С data
```

---

```
status: confirmed
area: diagnosing errors on the document form
technique: 1С displays messages at the bottom of the screen. Evaluate each:
       error → investigate the cause (otherwise save/post remain blocked);
       informational → can be ignored.
       There is no explicit visual indicator distinguishing error versus informational —
       rely on the text (presence of words indicating errors, missing values, incorrect data,
       negative context).
anti-pattern: ignoring all messages or treating every message as an error
why: an unnoticed error in the messages causes the test to fail at the next step
        (save/post); a false alarm on an informational message wastes time
steps: |
  1. After an action on the form (filling, saving, posting) — check the message area
     at the bottom of the screen (screenshot / visual-check)
  2. If a message looks like an error but the meaning is unclear — search the code for the text:
     a) document form module
     b) object module
     c) manager module
     d) global keyword search
  3. When searching, account for templates: the error text may contain substituted
     values (nomenclature names, counterparties). Search by the keywords defining
     the error nature, not by specific names.
source: general behavior of platform 1С:Предприятие
```

---

```
status: confirmed
area: closing a modified form
technique: the "*" symbol in the form title indicates unsaved changes.
       When closing such a form, the platform shows the dialog "Data has been changed. Save changes?" with buttons "Yes / No / Cancel".
       If saving is not required by the test conditions — press "No".
       If it is required — save first, then close.
anti-pattern: closing a modified form without handling the confirmation dialog —
           the test will hang on the modal window
why: the modal dialog blocks all actions; Vanessa cannot execute the next step and the test hangs due to a timeout
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
