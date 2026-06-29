---
name: find-skills
description: "Когда нужен поиск или установка агентского навыка"
capabilities: skills-management
---

# Find Skills

Discover and install skills from the open agent skills ecosystem (`npx skills`).

## When to Use

User asks "how do I do X", "find a skill for X", "can you do X", wants to extend capabilities, or mentions a specific domain (design, testing, deployment, etc.).

## Key Commands

```bash
npx skills find [query]        # Search by keyword
npx skills add <owner/repo@skill> -g -y  # Install globally
npx skills check               # Check for updates
npx skills update              # Update all
```

## Workflow

1. **Identify** domain + specific task from user's request
2. **Search:** `npx skills find <relevant keywords>` (use specific terms: "react testing" > "testing")
3. **Present** skill name, install command, and skills.sh link
4. **Install** if user agrees: `npx skills add <owner/repo@skill> -g -y`

If no skills found: offer to help directly, suggest `npx skills init` for custom skill.

## Common Categories

| Category        | Example Queries                          |
| --------------- | ---------------------------------------- |
| Web Development | react, nextjs, typescript, css, tailwind |
| Testing         | testing, jest, playwright, e2e           |
| DevOps          | deploy, docker, kubernetes, ci-cd        |
| Documentation   | docs, readme, changelog, api-docs        |
| Code Quality    | review, lint, refactor, best-practices   |
| Design          | ui, ux, design-system, accessibility     |
| Productivity    | workflow, automation, git                |

Machine-readable copy: `domains.json` next to this skill.

