---
id: vh-architecture-contract
slug: documentation-contract
type: documentation-standard
system: vellumhub
status: active
related: [vh-architecture, vh-services]
---

# Architecture documentation contract — humans and agents

Keep **code as the implementation source of truth**, API/Kafka schemas as contract sources, and GitHub Actions as verification evidence. Markdown is the durable, versioned documentation source; the HTML explorer is only a viewer and must never become a second diagram source.

## Required metadata

- `id`: immutable, unique identifier, e.g. `vh-service-catalog`.
- `slug`: lowercase kebab-case, stable in file names and links.
- `type`: one of `architecture-index`, `service-registry`, `c4-container`, `c4-context`, `c4-container-diagram`, `c4-component-diagram`, `sequence-diagram`, `state-machine`, `documentation-standard`.
- `system: vellumhub`; `status: implemented-in-repository` for code-backed runtime diagrams, `status: operating-guidance` for desired rollout states.
- `related`: list of stable document IDs (not prose or rendered URLs).
- Service cards additionally require `code` (`VH-AUT`/`VH-GTW`/`VH-USR`/`VH-CAT`/`VH-ENG`/`VH-REC`), `source_path` and `data_store`.

Use normal **relative Markdown links in the body** to make navigation work on GitHub, Obsidian exports and generic agents even when frontmatter indexing is unavailable. No links depend on a local Obsidian installation.

## Change protocol

1. Inspect the actual source/config/contract and identify whether the change affects system context, container dependencies, a component, an event sequence or a persisted state transition.
2. Update the nearest owning **service card** and its Mermaid diagrams. Maintain the same canonical code/slug in frontmatter, tables and diagram labels.
3. Update the top-level registry/README only for meaningful topology or lifecycle changes; do not paste every technical detail into the top-level README.
4. Treat a **state machine as a claim about permitted transitions**, not an aspirational workflow. Label proposed/operational states separately from persisted database states. Outbox PENDING/IN_FLIGHT/PUBLISHED is verified from JdbcOutboxStore; it has **no persisted FAILED terminal state**.
5. Mark historical diagrams/measurements with date and source SHA rather than silently rewriting old evidence. Never claim that an approved PR or a Kind test proves deployed production behavior.
6. Cross-link relevant existing ADRs, runbooks, endpoint contracts and CI artifacts. In Obsidian, create a thin linked architecture index instead of copying the entire canonical diagrams and diverging.

### Suggested review checks

- Stable IDs and service codes are unique; each diagram's `related` references an existing ID.
- Every relative path in an architecture document resolves at the expected commit.
- Mermaid fenced blocks have a supported diagram type (`C4Context`, `C4Container`, `C4Component`, `sequenceDiagram` or `stateDiagram-v2`); code blocks are balanced.
- The container C4 diagram distinguishes **Auth-issued tokens** from Gateway routing, **Kafka** from Redis, **domain caches** from Gateway rate limiting, and **owned databases** from copied projections.
- No diagram implies cross-service SQL joins, synchronous Recommendation fan-out, globally exactly-once delivery, external TLS-tested performance or live production Redis without evidence.
- PR CI validates Markdown links and structure where possible; visually inspect the rendered GitHub diagrams or [HTML explorer](architecture-explorer.html).

## Viewer

From the repository root, run `python3 -m http.server 8000` and open `http://localhost:8000/docs/architecture/architecture-explorer.html`. The explorer fetches local Markdown and uses Mermaid from a pinned public CDN; network access is needed for graphical rendering. If unavailable, it presents the source text instead. Do not open via `file://` (fetch restrictions), and never put API keys or private data in these public pages.
