---
name: docs-driven-development
description: Enforce that AI-generated code follows the project documentation under `docs/`, including technical schemes, API contracts, and database design. When code changes are needed, update the related docs in the same change.
---

# Docs-Driven Development

Use this skill whenever working in this repository so code and documentation stay aligned.

## Core rule

Treat the files in `docs/` as the source of truth for:

- technical architecture and implementation approach
- API contracts and request/response shapes
- database schema and field definitions
- module boundaries and naming
- business rules and workflow requirements

If code must change, the related documentation must be reviewed and updated in the same task.

- Treat SQL files under `server-springboot/sql/` as part of the source of truth for schema structure, initialization data, indexes, migration scripts, and status definitions.
- When modifying database-related code, always review and synchronize the related SQL scripts under `server-springboot/sql/`.
- Do not change entity fields, table structure, indexes, enums, or initialization data without updating the corresponding SQL files.

## Required workflow

1. Find the most relevant document in `docs/` before making changes.
2. Compare the code change against the document and identify mismatches.
3. Implement the code change so it matches the documented spec.
4. Update the document if the implementation changes any of these:
   - endpoint path or method
   - request/response fields
   - table structure, indexes, or status values
   - module structure or naming
   - flow logic or error handling
5. Keep docs and code consistent before finishing.

## Change policy

- Do not invent new API fields, database columns, or workflow steps unless the doc is updated too.
- Do not silently change behavior that is already documented.
- If the document is vague or incomplete, extend the document first or alongside the code.
- Prefer small, traceable changes that preserve the documented design.

## Implementation checklist

Before completing a task, verify:

- [ ] Code matches the latest docs
- [ ] Any changed API contract is reflected in `docs/`
- [ ] Any changed DB structure is reflected in `docs/`
- [ ] Any new module or behavior is documented
- [ ] Naming is consistent across front end, back end, and docs

## Output expectation for future edits

When generating code in this repository, always mention whether the implementation is:

- fully aligned with existing docs
- requires a docs update
- intentionally changing the spec and updating docs accordingly

