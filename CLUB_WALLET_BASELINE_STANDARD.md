# Club Wallet Baseline Standard

This document is the locked reference for implementing list modules in SQUAD.
Use this as the default pattern for all future modules (Finance, Sessions, Teams, Users, Players, Dashboard, Master Panel).

## 1) Core Principle

- Backend does filtering, searching, sorting, and pagination at DB level.
- Frontend only sends filters/page params and renders returned rows/meta.
- No client-side slicing/filtering for server-paged list data.

## 2) Pagination and Search Contract

- Request supports:
  - `page` or `pageNumber` (1-based)
  - `size` or `pageSize`
  - module filters (status, team, player, session, etc.)
  - `search` for supported searchable field
- Sort is latest-first with deterministic tie-breaker:
  - primary: `updatedAt` desc (or module equivalent)
  - secondary: `_id` desc
- Response is typed DTO, not `Object`/`Map`.
- Paged response contains rows + shared page meta DTO.

## 3) DTO Architecture (Mandatory)

- DTOs must be in module-based packages:
  - `com.squad.backend.dto.request.<module>`
  - `com.squad.backend.dto.response.<module>`
- Shared pagination meta stays common:
  - `com.squad.backend.dto.response.PageMetaResponse`
- Avoid anonymous response objects in controllers.

## 4) Frontend Behavior Rules

- Search call fires only when:
  - input length `>= 2`, or
  - input cleared to empty string.
- Use debounce (400-600ms) and latest-request handling.
- On filter/search change:
  - reset to first page
  - request backend
  - render backend result only
- Paginator uses backend `total/meta`.

## 5) Code Quality Rules

- Reuse helpers/utilities for repeated pagination/filter logic.
- Remove dead legacy code after migration.
- Keep naming, folder structure, and response style consistent.
- Add only minimal comments for genuinely complex logic.

## 6) Verification Checklist (Before Locking a Module)

- Backend:
  - All list endpoints are DB-paged and DB-filtered.
  - Typed DTO response used for every endpoint touched.
  - Imports aligned with module package structure.
  - `mvn -DskipTests compile` passes.
- Frontend:
  - No client-side list slicing/filtering for paged data.
  - Search trigger and filter behavior aligned with contract.
  - Paginator/empty states use backend meta and rows.
- Safety:
  - No unrelated regressions in nearby flows.
  - No duplicate/obsolete methods left behind.

## 7) Locked Decision

Club Wallet pattern is the baseline standard.  
All future modules must follow this document unless explicitly approved to diverge.

