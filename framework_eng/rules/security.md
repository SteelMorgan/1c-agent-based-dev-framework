---
name: security
description: "Working with passwords/tokens/crypto/privileges → apply the security skill"
alwaysApply: true
---
# 1C Security

> **Trigger:** when working with passwords, tokens, encryption keys, digital signatures, certificates, privileged mode, or data masking in logs. When triggered, apply the `security` skill (`framework/skills/bsl-practices/security/SKILL.md`).

Secrets do not belong in the registration log, comments, or temporary files. Privileged mode is only with explicit justification and minimal scope.

---
depends_on:
  - security
---
