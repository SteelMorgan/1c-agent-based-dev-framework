---
description: При создании навыка в framework-meta создавать симлинк в .claude/skills
globs: framework/skills/framework-meta/**/*
---

# Framework-meta skill → symlink

При создании или добавлении нового навыка в `framework/skills/framework-meta/` **обязательно** создай симлинк в `.claude/skills/`:

```bash
ln -sf "../../framework/skills/framework-meta/<skill-name>" ".claude/skills/<skill-name>"
```

**Пример:** новый навык `framework/skills/framework-meta/my-skill/` → симлинк `.claude/skills/my-skill` → `../../framework/skills/framework-meta/my-skill`
