---
name: architecture-diagrams
description: Write, update or audit VellumHub architecture documentation as code-backed diagrams — C4 context/container/component views, Mermaid sequence diagrams for request and event flows, and state machines for persisted lifecycles (outbox, quarantine, identity, cache, wire format) under docs/architecture — and remove legacy, issue-era or historical prose. Use it whenever behaviour, a flow, a status column, a route or a service boundary changes, when a README or docs page describes how something works, when docs look outdated, or when the user asks for documentation, diagrams, flows or "how does X work".
---

# Architecture diagrams (VellumHub)

Documentation here is a picture of the code as it is today. A diagram backed by a table of source links stays checkable; long issue-by-issue prose rots and starts contradicting the code (this happened with User-as-issuer and HS256 pages that outlived the Auth cutover). History, plans and experiments belong in the engineering Vault, not in the repository (`docs/architecture/CONTRIBUTING.md`).

## Choose the diagram
- **C4** (`diagrams/`): who talks to whom and who owns which data. Change it when a deployable, datastore or external dependency changes.
- **Sequence** (`flows/`): one request or event path with its alternatives (`alt`/`opt`), including failure branches and status codes. Use it for HTTP flows, provisioning, outbox relay, consumers.
- **State machine** (`states/`): a value persisted in a column with a constrained set of states (`PENDING/IN_FLIGHT/PUBLISHED`, `BLOCKED/RETRY_REQUESTED/RESOLVED`, `PENDING_PROFILE/ACTIVE`). Derive states from the CHECK constraint, enum or SQL, never from intent.

## Procedure
1. Read the code first: SQL, enums, controllers, config defaults and migrations. Every arrow and state must exist in source; if you are unsure, leave it out.
2. Start from the matching template in `references/page-templates.md`. One Mermaid block per page, then a table mapping each transition or step to the class or file that implements it, then two or three sentences on guarantees and explicit non-claims (for example "no exactly-once", "live Google not evidenced").
3. Register the page: frontmatter `id`/`slug`/`type`/`system`/`status`/`related`, a row in `docs/architecture/README.md` and an entry in the `pages` list of `architecture-explorer.html`.
4. Point READMEs and runbooks at the diagram instead of repeating the flow in prose. Service READMEs keep one short `## Security` section with current rules only.
5. Remove legacy material instead of annotating it: issue-numbered status lines ("draft PR", "not merged", "on this branch"), superseded issuers or algorithms, removed routes. Search the whole repo (Markdown, workflows, alert `runbook` URLs, scripts) for references before deleting a page, and move any still-true operational fact to its owning README.
6. Validate:
   - `python tools/architecture/validate_docs.py` (frontmatter, IDs, links, fences, and `;`/`#` in sequence and state text, which end a Mermaid statement).
   - Parse every diagram with the real Mermaid version used by the explorer (11.4.1): install `mermaid@11.4.1 jsdom@24.1.0` with `--ignore-scripts` into a temporary directory and run `node scripts/check_mermaid.mjs TEMP_DEPS_DIR FILE...`. The Python validator does not parse diagrams; this does.
   - Look at the result: `node scripts/render_mermaid.mjs TEMP_DEPS_DIR BROWSER_EXE OUT_DIR FILE...` renders each diagram to PNG through a locally installed Chrome or Edge (`npm install --ignore-scripts puppeteer-core@23.11.1` into the same temporary directory; it uses an isolated profile, so an open browser does not block it). Read the PNGs before publishing.
   - Check relative links in Markdown outside `docs/architecture`, which the validator does not cover.

## Writing rules that keep diagrams readable
- Messages are short verb phrases; put detail in the table, not the arrow.
- Use participant aliases with the service code (`VH-AUT Auth`). Avoid `<`, `>` in aliases and `;`/`#` anywhere in text.
- Show the failure branches people ask about (401/403/404/429, duplicate, gap, retry exhausted).
- State diagrams name the trigger and guard on each transition (`IN_FLIGHT --> PENDING: send failed / backoff`). Express self-transitions (rebalance, restart) as notes; Mermaid draws them as long loops that cross the diagram.

## Output
The new or changed pages, what was removed and where its true facts went, and validator plus Mermaid parse results.
