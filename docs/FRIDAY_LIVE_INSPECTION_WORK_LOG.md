# Friday Live Inspection — Engineering Work Log

Last updated: 2026-11-28
Controlling tracker: https://github.com/mhw77655-hue/Friday/issues/188
Status: Cloud bridge deployed and healthy; authenticated ChatGPT-to-phone acceptance remains open.
Architecture constraint: This is temporary bootstrap infrastructure. Advanced Friday REA is intended to own the long-term diagnostic, repair, orchestration, verification, and evolution lifecycle. Do not turn this bridge into a permanent substitute for REA.

## Working protocol (keep this file current)
For every meaningful work session:
1. Read this log and issue #188 before changing anything.
2. Verify current live state; never trust stale notes without rechecking.
3. Record each concrete action, changed file/setting, commit/deployment ID, test result, and evidence.
4. Separate VERIFIED, NOT VERIFIED, BLOCKED, and USER ACTION REQUIRED.
5. Do not claim an end-to-end capability from a partial test (e.g. metadata discovery is not OAuth success; a running replica is not a connected MCP client).
6. Keep user requests and architecture constraints intact; do not require the user to repeat context.
7. Before ending, append the session's actions and the exact next action to this file and issue #188.
8. Continue automatically from the next open step when the user says continue/go; ask only when a user-only action or missing credential is genuinely required.

## Verified foundation
- Railway project: Friday Live Inspection.
- Service: friday-inspection.
- Production URL: https://friday-inspection-production.up.railway.app
- MCP endpoint intended for ChatGPT: https://friday-inspection-production.up.railway.app/mcp
- Supabase project: friday-auth (ref: dtvhwuhmcynpbqfehlvs).
- Railway owner variable reference is present on the service. Do not log its value.
- Supabase OAuth 2.1 discovery endpoint responds.
- Friday health and protected-resource metadata endpoints respond.
- Supabase project is ACTIVE_HEALTHY.
- Supabase migration `20261128024611_friday_mcp_audience_hook` is applied.
- Database function `public.friday_mcp_access_token_hook(event jsonb)` exists. Prior DB inspection found SECURITY INVOKER and restricted execute grants. Its intended logic sets the Friday MCP resource audience only for OAuth/token-refresh methods with a client_id, preserving ordinary auth claims.
- Owner supplied dashboard screenshot confirming **Customize Access Token (JWT) Claims hook — ENABLED**, Postgres function, schema `public`, function `friday_mcp_access_token_hook`. This closes the manual Auth Hook activation requirement.
- Latest previously verified Railway deployment: `1d0ca09f-43ca-430f-b3cd-446c242a63e6`, SUCCESS, source commit `e6a95e38362cb8f61e6f36aeaa532844985ddddd` (recheck before relying on this).
- Supabase security advisors previously returned no findings.

## Session log — 2026-11-28
### Actions completed
1. Loaded Supabase and Railway operating instructions and inspected available connected actions.
2. Rechecked Railway production environment and deployments:
   - service online, one replica running;
   - latest verified deployment SUCCESS at the deployment/commit above;
   - no recent failed deployment reported;
   - owner variable name present (value intentionally not exposed).
3. Rechecked Supabase project and migration inventory:
   - project ACTIVE_HEALTHY;
   - audience-hook migration applied;
   - security advisors returned no findings.
4. Inspected the Auth Hook function in the database:
   - function exists;
   - SECURITY INVOKER;
   - execution grants restricted;
   - intended OAuth-specific audience transformation retained.
5. Recorded the owner's dashboard screenshot confirmation in issue #188. Auth Hook activation is now USER-CONFIRMED, not merely inferred from function existence.
6. Re-fetched live endpoints:
   - Supabase OAuth metadata advertises authorization, token, JWKS and dynamic-registration endpoints;
   - Friday health and protected-resource metadata respond;
   - consent page responds.
7. Inspected Railway build logs. The prior two high-severity npm audit findings remain unresolved because the log did not identify package/advisory names.
8. Confirmed `services/friday-inspection/package-lock.json` is absent. A reproducible lockfile and exact audit report are needed before safe dependency remediation.
9. Re-read current Supabase OAuth 2.1, MCP authentication, Custom Access Token Hook, and Auth Hook documentation. Relevant references:
   - https://supabase.com/docs/guides/auth/oauth-server
   - https://supabase.com/docs/guides/auth/oauth-server/getting-started
   - https://supabase.com/docs/guides/auth/oauth-server/mcp-authentication
   - https://supabase.com/docs/guides/auth/auth-hooks/custom-access-token-hook
   - https://supabase.com/docs/guides/auth/auth-hooks
10. Confirmed Supabase's OAuth server uses authorization code + PKCE, has optional dynamic registration, and redirects to a configured consent UI. The current consent route still needs an actual interactive OAuth flow test; serving HTML is not proof of consent handling.
11. Added the owner-confirmed Auth Hook status and remaining acceptance tasks to GitHub issue #188.
12. Created this persistent work log at `docs/FRIDAY_LIVE_INSPECTION_WORK_LOG.md` (commit `086338b4d3b87dcfec5a728db752ba6037d627d2`) so future "continue/go" requests can resume from recorded state without re-explaining.
13. Reviewed `services/friday-inspection/src/oauth-consent.html` and `src/server.js` from current main:
   - consent page reads `authorization_id`;
   - uses Supabase JS `getAuthorizationDetails`;
   - compares signed-in user email to configured owner email;
   - displays client name, redirect URI and requested scopes;
   - calls Supabase `approveAuthorization` or `denyAuthorization`;
   - server serves this consent page at `/oauth/consent`.
   This is source-level confirmation only. Actual interactive consent and callback remain untested.
14. Confirmed service dependencies are pinned in package.json (`@modelcontextprotocol/sdk@1.32.2`, `ws@8.18.3`, `zod@4.1.12`, `@supabase/supabase-js@2.57.4`), but there is no package-lock.json and no Dockerfile at the expected service path. This means the exact transitive install is not reproducible from the repository at present.
15. Re-read current Supabase docs; they specify OAuth 2.1 authorization-code + PKCE, client registration, and consent UI methods. Documentation references are cited in the assistant response and linked above.

### Important security/implementation findings
- Do not treat OAuth metadata discovery as proof of client registration.
- Dynamic registration must be exercised as a valid POST request and response.
- Do not treat the consent page HTTP 200 or source review as proof of actual consent handling.
- The hook must preserve every required Supabase JWT claim. Test actual issued OAuth and refresh tokens; do not infer the final `aud` claim from SQL source alone.
- Verify exact resource audience, issuer, signature, expiry, subject, client_id, owner identity and any intended scopes on a real issued token.
- Check whether current MCP auth code enforces scope/client authorization, not only owner email and audience.
- The consent page currently exposes a "Create owner account" path; review whether that is appropriate for a single-owner private bridge before production use. Owner email comparison is a second gate, but account creation should not be unnecessarily available.
- No actual ChatGPT MCP authenticated `tools/list` or `tools/call` has yet been accepted.
- No Android device is paired and no live phone snapshot has been retrieved.
- Do not expose secrets, JWTs, owner email, private phone diagnostics, transcripts, or memory in public GitHub comments.

## Remaining work — ordered execution

1. **Owner bootstrap:** create and confirm the Supabase owner account using the exact email configured in Railway `FRIDAY_OWNER_EMAIL`. Current database count is 0 users, 0 OAuth clients, 0 authorizations and 0 consents.
2. **ChatGPT custom MCP connection:** in ChatGPT web, add `https://friday-inspection-production.up.railway.app/mcp` as **Friday Live Inspection**, choose OAuth, review the risk warning, complete DCR/PKCE, sign in as the owner, and approve `openid email`.
3. **MCP acceptance:** verify the two read-only tools are discovered and call `friday_live_status`; inspect the real JWT issuer, exact Friday resource audience, expiry, not-before and OAuth client ID. Confirm refresh-token renewal.
4. **Phone update and pairing:** update the owner's phone from Friday 1.0.610 to official release 1.0.631 (one update only); sign in at `/admin/pairing`, generate the one-time code, then enrol under Settings → Live inspection.
5. **Live snapshot and revocation:** retrieve a fresh timestamped `friday_inspection_snapshot`; test device revocation, OAuth consent revocation and the expected denial behavior.
6. **Closeout:** record end-to-end evidence in issue #188 and this file. Do not claim the bridge complete before the real ChatGPT-to-phone snapshot and revocation test pass.

## Current truthful status

The cloud service and Auth Hook are deployed and verified. Main-branch service tests and dependency audit pass; the Railway service build reports 0 vulnerabilities. The MCP tool definitions now advertise OAuth security requirements and the server sends standards-aligned scope-aware challenges. The owner pairing and OAuth consent pages are live.

The phone is still on Friday 1.0.610 according to the owner. Supabase currently has no owner user or OAuth client, and ChatGPT has not registered or authorized. No phone is enrolled and no live snapshot has been retrieved. The remaining blockers are therefore user-side account bootstrap, ChatGPT authorization, and the one-time phone update/pairing—not cloud deployment.

## Session log — 2026-11-28 (continued: phone version and OAuth hardening)

1. Owner confirmed the installed Android app is **Friday 1.0.610**. Verified that the live-inspection client is in release 1.0.631, not 1.0.610.
2. Verified official GitHub release asset `friday-v1.0.631/app-release.apk`, 129,802,992 bytes, GitHub asset SHA-256 `ef2a34966d87fab617d43882e2678a4c1999ee207046450933fb606f8d557b47`. Release metadata says the APK certificate is checked against the pinned Friday release fingerprint. User must install it over 1.0.610; no uninstall or intermediate APK is required.
3. Re-read current Supabase OAuth 2.1 and Custom Access Token Hook documentation. Supabase documents authorization-code + PKCE, OAuth-specific `client_id` claims, and a refresh-token grant. The custom hook preserves normal password-session audiences and sets the exact Friday MCP resource audience for OAuth authorization-code and refresh-token issuance.
4. Ran fresh read-only Supabase checks:
   - Project `friday-auth` is `ACTIVE_HEALTHY`.
   - `auth.users`: 0.
   - `auth.oauth_clients`: 0.
   - `auth.oauth_authorizations`: 0.
   - `auth.oauth_consents`: 0.
   - Security advisors: no lints.
   - `public.friday_mcp_access_token_hook` is SECURITY INVOKER; `supabase_auth_admin` can execute; `anon` and `authenticated` cannot.
5. Inspected current Supabase Auth JS `getClaims`: it verifies the token signature and expiry but does not itself enforce the MCP resource audience or issuer. Friday's own validator therefore continues to enforce issuer, exact audience, expiry, issued-at, subject, client ID, owner email, and now `nbf`.
6. Reviewed current OpenAI custom MCP docs. They specify protected-resource metadata, per-tool `_meta.securitySchemes`, scope-aware OAuth challenges, resource binding, and PKCE. Updated Friday's MCP descriptors to declare OAuth with `openid email`; narrowed resource metadata from `openid email profile` to `openid email`; added `WWW-Authenticate` with resource metadata and scope for unauthenticated/invalid MCP requests; ensured browser owner-setup routes do not emit an MCP OAuth challenge; and added `nbf` rejection plus tests.
7. Merged PR #202: https://github.com/mhw77655-hue/Friday/pull/202. Merge commit: `c4c3a7ffc7e8d984ed7e03963d1c20cabca39725`.
8. Verified main-branch Friday inspection service tests SUCCESS and dependency audit SUCCESS on the merge commit.
9. Verified Railway deployment `7a5951e4-2166-4008-8813-ed52f6377211` reached SUCCESS for that merge commit. Build output reports 0 vulnerabilities.
10. Fetched live production endpoints after deployment:
    - `/health` returns `status: ok`.
    - `/.well-known/oauth-protected-resource` now advertises exactly `openid email` and the Supabase authorization server.
    - `/admin/pairing` and `/oauth/consent` both serve successfully.
11. Confirmed the actual ChatGPT DCR/PKCE exchange has not happened: OAuth client, authorization, consent and user counts remain zero. No Android device is enrolled and no fresh phone snapshot or revocation test exists.
12. Added the new state and required user-side actions to GitHub issue #188 and updated the deployment status document.

### User action required to unblock the next acceptance stage

- Install the official 1.0.631 APK over the installed 1.0.610 app: https://github.com/mhw77655-hue/Friday/releases/download/friday-v1.0.631/app-release.apk
- Create/confirm the Supabase owner account with the email matching Railway `FRIDAY_OWNER_EMAIL`.
- Add the custom MCP server in ChatGPT web at `https://friday-inspection-production.up.railway.app/mcp`, choose OAuth, and approve the owner consent.
- Then generate the one-time pairing code at `https://friday-inspection-production.up.railway.app/admin/pairing` and enter it in Friday → Settings → Live inspection.

## Current truthful status
Cloud service and Auth Hook configuration are verified. The consent implementation has been reviewed in source, but OAuth server URL configuration, client registration, owner consent, PKCE token issuance, authenticated MCP calls, ChatGPT connector acceptance, physical phone pairing, and fresh phone diagnostics are NOT YET VERIFIED.


## Session continuation — 2026-11-28 (dependency remediation)
### Completed
16. Added dependency audit workflow `.github/workflows/friday-inspection-dependency-audit.yml` (commit `21b0eade36212477b51e149a8fb8578d3ceb963b`). It generates and commits a lockfile, installs with `npm ci`, runs checks/tests, captures npm audit JSON, and uploads the report.
17. First reproducible audit found two direct high-severity packages:
   - `ws@8.18.3`: GHSA-96hv-2xvq-fx4p (high DoS, affected <8.21.0; npm fix 8.23.1) and GHSA-58qx-3vcg-4xpx (moderate).
   - `zod@4.1.12`: GHSA-mrhx-c7xc-m3w5 (high memory exhaustion, affected <4.6.6; npm fix 4.6.7).
18. Updated direct dependencies to patched same-major versions `ws@8.23.1` and `zod@4.6.7` in commit `e54cf85aba11b0889e6cb3e8bf2fd5e815ae03ed`.
19. The audit workflow regenerated the lockfile and ran `npm ci`, `npm run check`, and `npm test`; all 11 tests passed. Final npm audit summary: info 0, low 0, moderate 0, high 0, critical 0, total 0.
20. Workflow committed the corrected lockfile in commit `32c256364c9725f40620f90374df67c6e530dec7`. Audit report artifact ID `15244848492`; workflow run `46100664786` completed SUCCESS.
21. Railway build for intermediate commit `e54cf85` failed because it started before the audit workflow pushed the matching updated lockfile. Exact error: lockfile still had ws 8.18.3 and zod 4.1.12. This is a sequencing failure, not an application test failure.
22. Railway has a newer deployment for corrected lockfile commit `32c2563`, deployment `1eea396b-d084-43d7-9fdb-6c1aff34c9ff`, status NEEDS_APPROVAL at last check. The prior deploy of commit `140b7ac` succeeded; do not claim patched dependencies are live until the lockfile commit's deployment succeeds.
23. Health endpoint still responded during the checks. The currently serving service is still an older successful build until the latest pending deployment is approved and succeeds.

### Next actions
1. Resolve Railway's pending approval for deployment `1eea396b-d084-43d7-9fdb-6c1aff34c9ff` (if the dashboard still shows NEEDS_APPROVAL). User-side approval may be required; do not bypass approval.
2. Verify Railway build uses `npm ci` with the corrected lockfile, reaches SUCCESS, and health + protected-resource metadata respond.
3. Re-run/inspect the npm audit workflow after lockfile commit if needed; verify zero findings remain on the actual main HEAD.
4. Verify Supabase OAuth Server Site URL + Authorization Path + Dynamic Client Registration setting. The Auth Hook screenshot only confirmed Auth Hook, not these OAuth Server settings.
5. Complete real DCR POST, consent, PKCE, token claim inspection, MCP initialize/tools/list/call, then ChatGPT connector and Android pairing.


## Session continuation — 2026-11-28 (confirmed Android enrollment)

Owner corrected the phone version: installed app is **Friday 1.0.631**, not 1.0.610. Treat 1.0.631 as current; do not request another APK update.

### Verified production evidence
- Pairing page and owner status endpoint returned HTTP 200.
- Owner pairing-code generation: POST /admin/pairing-code returned HTTP 200 at 2026-11-28T04:48:37Z.
- Android enrollment: POST /device/enroll returned HTTP 201 at 2026-11-28T04:48:44Z, user-agent okhttp/4.12.0.
- Runtime emitted device_enrolled and device_connected for the same device ID at 2026-11-28T04:48:44Z.
- Production /health returns status ok.
- Latest application deployment remains SUCCESS: 7a5951e4-2166-4008-8813-ed52f6377211, commit c4c3a7ffc7e8d984ed7e03963d1c20cabca39725. Later documentation-only commits were correctly SKIPPED.

### Status correction
Earlier statements that no Android device was enrolled are superseded. Phone pairing is VERIFIED. This does not yet prove ChatGPT OAuth registration/authorization or retrieval of a fresh friday_inspection_snapshot.

### Next acceptance actions
1. Inspect device last-seen/heartbeat state without exposing private diagnostic payloads.
2. Complete ChatGPT MCP OAuth registration/authorization and verify the authenticated tool catalog.
3. Execute friday_live_status and friday_inspection_snapshot through the authorized MCP client; confirm snapshot timestamp and freshness.
4. Test device revocation and OAuth consent/token revocation, then confirm access is denied.
