---
name: agent-development
description: "Create or configure custom subagents with specialized prompts (reviewers, debuggers, domain-specific assistants). Helps determine the location, structure, and system context of a new agent."
---

# Creating Custom Subagents

Subagents are specialized AI assistants running in isolated contexts with custom system prompts. If previous conversation context exists, infer the subagent's purpose from what was discussed.

## Subagent Locations

| Location | Scope | Priority |
|----------|-------|----------|
| `.cursor/agents/` | Current project | Higher |
| `~/.cursor/agents/` | All your projects | Lower |

When multiple subagents share the same name, the higher-priority location wins.

**Project subagents** (`.cursor/agents/`): Ideal for codebase-specific agents. Check into version control to share with your team.

**User subagents** (`~/.cursor/agents/`): Personal agents available across all your projects.

## Subagent File Format

Create a `.md` file with YAML frontmatter and a markdown body (the system prompt):

```markdown
---
name: code-reviewer
description: Reviews code for quality and best practices
---

You are a code reviewer. When invoked, analyze the code and provide
specific, actionable feedback on quality, security, and best practices.
```

### Required Fields

| Field | Description |
|-------|-------------|
| `name` | Unique identifier (lowercase letters and hyphens only) |
| `description` | When to delegate to this subagent (be specific!) |

## Writing Effective Descriptions

The description is **critical** - the AI uses it to decide when to delegate. Include "use proactively" to encourage automatic delegation.

```yaml
# Bad — too vague
description: Helps with code

# Good — specific trigger conditions
description: Expert code review specialist. Proactively reviews code for quality, security, and maintainability. Use immediately after writing or modifying code.
```

## Example Subagent

```markdown
---
name: code-reviewer
description: Expert code review specialist. Proactively reviews code for quality, security, and maintainability. Use immediately after writing or modifying code.
---

You are a senior code reviewer ensuring high standards of code quality and security.

When invoked:
1. Run git diff to see recent changes
2. Focus on modified files
3. Begin review immediately

Review checklist:
- Code is clear and readable
- Functions and variables are well-named
- No duplicated code
- Proper error handling
- No exposed secrets or API keys
- Input validation implemented
- Good test coverage
- Performance considerations addressed

Provide feedback organized by priority:
- Critical issues (must fix)
- Warnings (should fix)
- Suggestions (consider improving)

Include specific examples of how to fix issues.
```

## Subagent Creation Workflow

1. **Decide scope:** project (`.cursor/agents/`) or user (`~/.cursor/agents/`)
2. **Create file:** `mkdir -p .cursor/agents && touch .cursor/agents/my-agent.md`
3. **Write frontmatter:** `name` and `description` (required)
4. **Write system prompt:** what to do when invoked, workflow, output format, constraints
5. **Test:** ask the AI to use the new subagent

## Best Practices

1. Each subagent should excel at one specific task
2. Include trigger terms in descriptions so the AI knows when to delegate
3. Check project subagents into version control
4. Include "use proactively" in descriptions

## Troubleshooting: Subagent Not Found

- File must be in `.cursor/agents/` or `~/.cursor/agents/`
- File must have `.md` extension
- YAML frontmatter syntax must be valid

---
depends_on: []
---
