# Swarm report {{session_id}} (FR-10, AC-10)

Template for the clustered swarm report. The swarm is divergent: the report records
findings, their attribution, and validation statuses and DOES NOT reduce them to a single consensus —
the dispositions of findings (accept/reject/defer) are decided by the Orchestrator outside
this document. The `{{...}}` placeholders are filled by `swarm.py report`.

- Tier: {{tier}}
- Created: {{created_at}}
- Orchestrator: {{orchestrator}}

## Dedup clusters (FR-06)

{{clusters}}

## Validation of findings, rounds 2–4 (FR-07)

Statuses: confirmed / withdrawn / reclassified / contested / unvalidated
(degradation by wall-clock budget — TD §11, not a silent cutoff).

{{threads}}

## Orchestrator arbitration (FR-08)

{{arbitration}}

## Findings routed from rounds 2–4 (§6.3.6)

New bugs from rounds 2–4 responses are not accepted into threads — they go here, in a shared pool.

{{routed}}

## Findings rejected before accounting (FR-05)

{{rejected}}

## Incidents (NFR-01)

{{incidents}}

## Cost (NFR-02, NFR-08)

{{cost}}
