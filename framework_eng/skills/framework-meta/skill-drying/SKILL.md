---
name: skill-drying
description: "Compression of skills and rules to reduce token consumption without losing agent behavior. Use when reviewing skills for redundancy, when a skill exceeds ~150 lines or when prompt size is critical. Applies to SKILL.md, .mdc rules, subagent prompts, and workflows."
---

# Skill Drying

## Behavioral criterion

Ask five questions about each fragment. If the answer is “no” to all of them, the fragment is water—remove it.

1. Does it change the choice of tool or skill?
2. Does it change the order of steps?
3. Does it add a nontrivial prohibition or exception-handling path?
4. Does it eliminate a dangerous ambiguity?
5. Is it necessary for a nontrivial counterexample?

## Drying rules

### Structure

**P1. One norm—one source of truth.** If the policy is recorded in a `.mdc` rule, the skill does not repeat it. The skill references the rule and adds only the operational algorithm.

**P2. Frontmatter is routing, the body is new information.** Remove `## Purpose` sections that literally repeat the `description` field. The body begins with the first element that adds context not present in the description.

**P3. One skill registry per subagent.** List skills only in the frontmatter `skills:`. The body describes responsibilities, protocol, and boundaries—not a dependency catalog. `depends_on` at the end of the file is for the resolution mechanism, not for agent reading.

**P4. Cross-skill duplicates—reference + delta.** If two skills contain the same rule (e.g., “do not swallow exceptions” in `coding-standards` and `error-handling`), the full version stays in one place. The second skill gets a one-line link.

### Motivation and clarifications

**P5. Motivation is one line of consequence.** Multi-paragraph “Why:” blocks are replaced by a single sentence about the consequence of violation. Remove entirely if the agent behaves identically without the block.

**P6. External links—drop the URL, keep the marker.** `[ITS Standard: “Module Texts”](https://its.1c.ru/...)` → `ITS Standard: “Module Texts”`. The agent will not open the URL, but the standard name is a useful signal for the LLM.

**P7. “Sources” and “Related resources”—minimize.** Remove blocks with 3–4 links to ITS. Keep cross-references only if they affect the workflow (“after this skill—run vanessa-run”).

### Examples

**P8. One canonical pattern + one nontrivial counterexample per rule.** Remove “incorrect” samples that are trivial inversions of “correct.” The criteria for keeping a counterexample are in “Red lines.”

**P9. Scenarios—inside the algorithm.** Multiple similar scenarios are replaced by a single algorithm. Four search scenarios → one cascade `LSP → metadata → platform API → БСП → AI` + a trigger table.

### Reference material

**P10. Large code blocks—move to `references/`.** Code blocks longer than 20 lines that serve as copyable references are moved to `references/`. The skill body keeps only: when to use it, the file name, and key parameters.

**P11. Data structures and diagrams—move to `references/`.** Directory trees, JSON schemas, ASCII diagrams go to the appendix. The body retains only critical paths and mandatory fields.

**P12. Summary table or expanded rules—choose one.** If the skill contains both and the whole skill is loaded, the summary duplicates the rules. Keep whichever form is more compact for the content.

## Red lines—do not dry

- **Precise operation order**, where deviation leads to data loss (transactions, locks, rollback/logging sequences)
- **Nontrivial counterexamples**—the error is tied to hidden platform behavior, shows specific names/constructions the agent might accidentally use, or is not derivable from the correct example
- **Agent responsibility boundaries** (“DO NOT write tests,” “DO NOT modify protected paths”)
- **Domain-specific API names** that the LLM does not know from training (1С BSL function names, CLI syntax)
- **Cause-and-effect chains** affecting decisions in edge cases (e.g., “O(N²) memory,” “counter transactions,” composite types in a JOIN)

## Process

1. Read the target skill entirely.
2. Apply the behavioral criterion to each section/paragraph.
3. Apply P1–P12 in order.
4. Compare before/after: ensure the behavioral signal remains.
5. If `references/` exists—move extracted blocks with clear file names.
6. Update `depends_on` and cross-references if deduplication changed the placement of a norm.
