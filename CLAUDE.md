<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
specs/005-personal-center-likes-analytics/plan.md
<!-- SPECKIT END -->

## Active Technologies
- Kotlin on JVM, Java 21 (backend); TypeScript 5 / Node.js 24 (frontend) + Spring Boot WebFlux, Spring Security (reactive), Spring Data JPA/Hibernate, Flyway, jjwt; Next.js App Router, Reac (004-admin-control-panel)
- PostgreSQL (Flyway migrations; existing `users`, `connections`, `configured_models`, `email_verifications`, `revoked_tokens`) (004-admin-control-panel)

## Recent Changes
- 005-personal-center-likes-analytics: Personal Center hub (profile/display_name, authenticated password change invalidating other sessions via `users.sessions_valid_from`, email-verification resend); per-response named likes + anonymous likes on opaque revocable share links; user-scoped usage analytics over the existing immutable `provider_responses` (extended with `chat_turns.client_ip` + snapshot `connection_id`, read-time cost from new `model_definitions` pricing columns). New backend packages: `reaction`, `share`, `analytics`. Frontend `(app)/account` (AccountShell) + public `(app)/share/[token]`.
- 004-admin-control-panel: Added Kotlin on JVM, Java 21 (backend); TypeScript 5 / Node.js 24 (frontend) + Spring Boot WebFlux, Spring Security (reactive), Spring Data JPA/Hibernate, Flyway, jjwt; Next.js App Router, Reac

## UX Principles (Constitution VIII — NON-NEGOTIABLE for user-facing work)

Every user-facing surface MUST be **consistent, fluent, responsive, and connected**. A feature is not
done until all four hold and the page has been visually verified (browser/Playwright) — a green
type-check is necessary but not sufficient.

- **Consistent** — reuse the existing design system, never raw unstyled HTML controls. Match a
  comparable existing page (e.g. `frontend/src/app/(app)/settings/models/ModelsSettingsPage.tsx`):
  the shared `@/components/ui/Button`, the warm canvas gradient, the `#c96442`/`#b75536` accent, the
  stone palette, `rounded-2xl border-stone-200 shadow-sm` cards, and the eyebrow + title + description
  header. Admin pages share `@/components/admin/AdminShell` for this reason.
- **Fluent** — show loading skeletons/spinners, disable controls while busy, surface inline
  success/error banners. No dead clicks or silent failures.
- **Responsive** — work from mobile widths up; wide tables scroll/reflow, never overflow.
- **Connected** — every authorized page MUST be reachable via in-app nav (a URL-only page is
  incomplete); link related views and show active-section state. Internal links use `next/link`.

See `.specify/memory/constitution.md` §VIII for the full text.
