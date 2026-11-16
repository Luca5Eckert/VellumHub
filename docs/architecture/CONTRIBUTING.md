---
id: vh-architecture-contract
slug: documentation-contract
type: documentation-standard
system: vellumhub
status: active
related: [vh-architecture, vh-services]
---

# Architecture documentation — maintenance contract

Keep this public documentation **concise and implementation-oriented**. Source, API/Kafka schemas, migrations and tests define behavior; Markdown C4, state and sequence diagrams summarize the current code. Detailed investigations, alternatives, ADRs, plans, benchmarks and historical evidence belong in the engineering Vault rather than being copied here.

- Every page has stable unique `id`, `slug`, `type`, `system: vellumhub`, `status` and `related` document IDs; service cards also declare `code`, `source_path`, `data_store`.
- Preserve codes `VH-AUT`, `VH-GTW`, `VH-USR`, `VH-CAT`, `VH-ENG`, `VH-REC` and relative Markdown links. Change diagrams only after checking actual source/config.
- Distinguish persisted states from operational rollout guidance; Auth is the only issuer; PostgreSQL is canonical; Kafka is at-least-once and downstream projections are eventually consistent.
- Do not present CI/Kind, benchmark figures or PR merges as proof of production deployment. Keep numeric claims linked to their reproducible evidence and environment.
- Update the nearest service card or diagram for contract changes; keep the top-level README focused on capabilities, results, architecture and how to run/verify.

Run `python3 tools/architecture/validate_docs.py` to validate frontmatter, IDs, links and Mermaid fences. **This is not a Mermaid rendering test**: inspect the actual diagrams visually in GitHub or in the [HTML explorer](architecture-explorer.html).

For the local explorer, run `python3 -m http.server 8000` at the repository root and open `http://localhost:8000/docs/architecture/architecture-explorer.html`. Mermaid is loaded from a pinned CDN; without network the original source remains visible.
