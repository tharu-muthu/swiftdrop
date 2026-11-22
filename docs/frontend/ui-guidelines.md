# SwiftDrop UI baseline

The login/registration reference screens and the implemented customer dashboard
and booking flow define the shared visual direction: navy navigation, blue primary
actions, an orange brand accent, pale page backgrounds and white bordered cards.
Keep the customer hero illustration and booking steps as feature content.

## Shared building blocks

- `src/app/theme.css`: colour, surface, border, radius and shadow tokens. Use
  `--sd-*` variables rather than introducing a separate feature palette.
- `Brand`: shared SVG and wordmark, with its own stylesheet. Do not copy the logo
  into individual headers or replace it with a text symbol.
- `PortalShell`: desktop navigation rail, white account header, main landmark,
  skip link, content container and horizontally scrolling mobile navigation.
  The header toggle switches between the full sidebar and a narrow icon rail at
  every viewport width. The rail uses the existing SwiftDrop S symbol, retains
  role-filtered links and active states, and gives each icon an accessible name
  and hover title. `sidebarPreference.js` shares the expanded/collapsed choice
  across route mounts; the existing visible/hidden storage values remain compatible.
  All authenticated portals use it. `WorkspacePage` adds the common data/form
  content styling; `BookingFrame` retains the existing customer content styling.
- `WorkspaceNavigation`: one set of role-filtered links with shared SVG icons,
  current-page indication and `AppLink` navigation. A single-role account has no
  portal switcher. Customers without MERCHANT see “Apply for merchant”.
- `AccountMenu`: shared header dropdown with name, email, current role badges,
  My account and Sign out. It supports keyboard navigation, Escape, outside-click
  dismissal and the existing session-revoking logout operation. Sign-out failures
  retain the authentication client?s safe retry path.
- `AppLoading`: shared session restoration and Google completion indicator.
- `AuthLayout`: retains the supplied split-screen identity design. Public tracking
  uses PortalShell when signed in, including the active tracking link and sidebar
  toggle. Anonymous shared tracking links remain public standalone pages with
  the same Brand and visual tokens; the tracking data and SSE contracts are shared.

## Content rules

Use 24px desktop page titles, 20px card headings and 14px body/control text inside
portals. Feature hero headings may remain larger. Use 24px card padding and 20px
gaps; on narrow screens use 16px page gutters and 20px card padding. Primary actions
are blue, secondary actions have white surfaces and borders, destructive actions
are red. Keep readable labels alongside status colours. Preserve focus outlines,
error/status announcements, disabled controls and responsive table overflow.

The shell owns the main landmark; feature content must not add nested `main`
elements. Components with their own page heading can use `titleAs="p"` on the
shell. Keep feature styles scoped: the public tracking card and customer dashboard
tracking card must not change each other's width, typography or layout.

This is a presentation refactor. Role checks, APIs, ownership checks, session
restoration and business actions remain with their existing owners.

## Review and validation

The source review found divergent green workspace, white profile and minimal
courier layouts; these now share PortalShell. Duplicated booking shell markup and
the obsolete feature shell rules were removed. Booking/public tracking selector
collisions were scoped to their owning pages. Existing role navigation tests were
updated for anchor semantics, with shared-shell regressions added.

Tests, build, Docker rebuild and browser visual checks are intentionally left to
the user, as requested. Run from the repository root:

```powershell
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
docker compose up --build --no-deps --detach --wait frontend
```

Check login, registration, password recovery, portal selection, customer dashboard,
booking steps/history/details, merchant application/orders/keys, webhooks,
dispatch, user management, merchant approval, pricing, notifications, courier
deliveries, profile and public tracking at desktop and mobile widths. Confirm
active navigation, form errors, empty/loading states and long table/URL content.

## Account profile extensions

Account security has separate Change email / Change password forms. New inbox
confirmation and Google reauthentication tokens stay in React memory after their
fragments are removed. No security credentials are persisted in browser storage.
Saved addresses use the shared profile card/form styles; booking loads them only
on demand and copies the chosen record into the current draft.

This extension changes backend behavior and adds V13. In addition to the frontend
commands above, run the full backend verification with Docker Desktop available:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -Pintegration verify
```

Use a full `docker compose up --build --detach --wait` after migration-version and
SMTP/Google coordination; rebuilding only frontend cannot activate these APIs.
AccountSettingsIT covers real PostgreSQL token reuse/expiry/rotation, email
uniqueness rollback, session revocation, Google-subject/freshness binding, address
ownership/version checks, concurrent default addresses and HTTP CSRF/DTO checks.
These checks are added but not executed by the agent per the user's preference.

Profile grouping: saved addresses are a subsection of Personal information;
email/password changes are inside Security & sign-in. Change email is rendered
only for the backend emailVerified flag. A connected Google account alone does
not override verification: existing backend flows verify an inbox link or a
verified Google provider email matching the account address. New-email inbox
confirmation and backend verification gates remain mandatory.
