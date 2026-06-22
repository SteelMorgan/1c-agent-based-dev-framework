---
name: security
description: "Working with passwords/tokens/crypto/privileges → apply the security skill"
alwaysApply: true
---
# 1C Security

> **Trigger:** when working with passwords, tokens, encryption keys, digital signatures, certificates, privileged mode, or data masking in logs. When triggered, apply the `security` skill (`framework/skills/bsl-practices/security/SKILL.md`).

**GUARD:** secrets in the registration log/comments/temporary files block acceptance; privileged mode without explicit justification does too.

---
depends_on:
  - security
---
