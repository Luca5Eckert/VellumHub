---
name: backend-code-review
description: Review a VellumHub backend diff or PR — including your own changes before handing them over — for correctness, security, failure semantics, readability, architecture boundaries and test quality, with file/line evidence and concrete failure scenarios. Use it for "review this", self-review before a PR, responding to review comments, merge-readiness questions, or when asked to find low-readability or low-quality code in the services.
---

# Backend code review

A useful review separates what is wrong (with a scenario that breaks) from what is merely unfamiliar. Severity and evidence matter more than the number of comments.

## Procedure
1. Read the issue acceptance and repository rules, then the diff against its parent rather than the file list.
2. Follow changed behaviour end to end: entry point → authorization → domain → storage or event → response or log.
3. Probe negative and adversarial cases: invalid input, foreign or hidden resources, concurrency, partial failure, retry, duplicate or out-of-order events, schema compatibility.
4. Check migrations and contracts, secrets, what reaches response bodies and logs (no SQL/parser text, no personal values), resources and timeouts, and the existing architecture rules.
5. For readability, look for the patterns that cost the most here: copy-pasted blocks that should be one helper, several statements per line, methods mixing lookup, validation, side effects and checkpointing, application code depending on HTTP DTOs, stale names and Javadoc that describes something else. Propose the refactor only with a way to show behaviour is unchanged (characterization tests, byte-identical SQL/messages).
6. Treat tests as proofs: do they assert observable outcomes, at the right boundary, and did they actually run?
7. Re-review your own fixes: a fix can introduce a new problem (for example moving a leaked value from a response into a log).
8. After fixes, review the final diff and the exact SHA; earlier CI results do not cover it.

## Output
Findings ordered by severity, each with file/line, failure scenario and minimal correction; maintainability suggestions separated from blockers; paths explicitly not verified.

Adapted from obra/superpowers `requesting-code-review` and `receiving-code-review`.
