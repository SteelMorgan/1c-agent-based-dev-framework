---
description: Discover and install relevant agent skills when user asks for new capabilities
alwaysApply: true
---

# Find Skills

## Goal
Help users quickly find installable skills for requested tasks and install trusted ones automatically.

## Use when
Apply this flow when the user:
- asks “how do I do X?” and X may be covered by a skill;
- asks to find a skill/tool/template/workflow;
- asks if the agent can do a specialized task;
- wants to extend capabilities in a domain.

## Procedure
1. Extract a short query (2–5 keywords: domain + task).
2. Search:
   ```bash
   npx skills find <query>
   ```
3. Return best matches (name, purpose, install command, skills.sh link).
4. Install policy:
   - **Allow-listed package** → install immediately in this project:
     ```bash
     npx skills add <owner/repo@skill> -y
     ```
   - **Not allow-listed** → ask user before install.
5. If no matches: say so, offer direct help, and optionally suggest creating a skill:
   ```bash
   npx skills init <skill-name>
   ```

## Hard rules
- Project-only installs by default (no `-g`).
- Use `-g` only if user explicitly asks for global install.
- Do not auto-install non-allow-listed skills.
- Keep suggestions focused (up to ~5 relevant options).