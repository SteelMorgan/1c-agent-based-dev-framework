# Accumulated Practices — xml-generation

```
status: candidate
class: Incomplete ifAbsent semantics for a composite hierarchical selector
recipe: For a selector with multiple identity levels, apply absent mode at each level: a missing parent and a missing terminal node both pass through the shared fail/noop contract; ambiguity always remains an error.
anti-recipe: Do not combine the states “parent missing” and “parent ambiguous” with a single exact-one check before handling ifAbsent.
why: Otherwise noop works only for a missing leaf, but fails on a schema without a parent, breaking idempotency and the shared batch-operation contract.
steps: Split cardinality into zero/one/many; pass zero to shared absent handling, continue with one, reject many; cover strict, noop, and mixed rollback.
source: Review of an atomic XML editor with hierarchical selector preflight, 2026-07
```

```
status: candidate
class: Hidden indent before XML declaration in a byte-sensitive Java fixture
recipe: For an XML fixture where BOM, CRLF, and exact byte spans are verified, build the declaration and root opening as explicit strings or keep the same incidental indent on every line of the text block; before starting mutation, separately verify parsing of the source fixture and the first character after the BOM.
anti-recipe: Do not mix in one Java text block lines with the usual source indentation and lines that start with a literal tab from the zero column: the minimal incidental indent will become zero, and spaces before `<?xml ...?>` will remain.
why: The XML parser rejects a declaration that is not at the start of the document, so the regression fails before the checked operation and masks its actual behavior with a false infrastructural error.
steps: Decode the fixture with BOM handling; check that the first character is `<`; perform a baseline parse; only then run the editor and byte-diff; for CRLF fragments, do not apply a global LF replacement after concatenating already formed CRLF parts.
source: Regression of the atomic lossless Designer XML editor, 2026-07
```

```
status: candidate
class: False check for line-ending uniformity in byte-lossless XML
recipe: Check CRLF only on structural spans that the editor added or replaced, and prove the rest of the document unchanged by byte comparison after restoring the addressed span.
anti-recipe: Do not remove CRLF globally and do not require the absence of remaining LF throughout the entire Designer XML: text content nodes can canonically contain bare LF.
why: A global check mixes structural line endings with the contents of text nodes and falsely rejects a correct lossless operation.
steps: Extract the addressed direct node; check its EOL separately; replace it with the original span and compare the whole document byte-exactly; separately verify BOM and mode.
source: Regression of the atomic/lossless editor for mixed Designer XML, 2026-07
```
