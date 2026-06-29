---
name: security
description: "For secrets, tokens, crypto, privileges use security"
alwaysApply: true
---
# 1C Security

> **Trigger:** when working with passwords, tokens, encryption keys, digital signatures, certificates, privileged mode, or data masking in logs. When triggered, apply the `security` skill (`framework/skills/bsl-practices/security/SKILL.md`).

**GUARD:** secrets in the event log/comments/temp files block acceptance; privileged mode without explicit justification does too.

---
depends_on:
  - security
---
