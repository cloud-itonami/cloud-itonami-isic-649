# Security Policy

This project handles identity-verification case data: applicant
references, sanctions-screening results, and (via referenced, not
directly-held, evidence) links to government-ID document and biometric
liveness data. Treat vulnerabilities as potentially high impact even when
demo data is entirely fictitious (`kyc-actor.store/demo-data!`).

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure (CACAO seed material, `kyc-actor.identity`'s
  `.kyc-actor/identity.edn`)
- real applicant/subject data exposure
- authorization bypass in `kyc-actor.api`
- `VerificationGovernor` bypass (any path that reaches `:commit` without
  passing through `kyc-actor.policy/check`)
- audit-ledger tampering (any path that mutates or drops a
  `kyc-actor.store/append-ledger!` fact after it's written)
- a path that lets a proposal's `accreditation-claim-gate`-violating text
  reach an API response despite the gate

## Scope note

This actor's own code does not itself store raw document/biometric bytes
(see `kyc-actor.store`'s module doc) — a vulnerability in
`ekyc.adapters.kagi-custody` or wherever a real deployment's
`ekyc-native-provider.ports/IEvidenceFetcher` is backed by is out of this
repo's scope but should still be reported to this repo's maintainers if
discovered through this actor's own code paths.
