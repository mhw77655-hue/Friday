# Friday Live Inspection — Engineering Work Log

Last updated: 2026-11-28
Controlling tracker: https://github.com/mhw77655-hue/Friday/issues/188
Status: Bootstrap bridge partially deployed; authenticated end-to-end acceptance remains open.
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
1. **OAuth server configuration and client registration:** verify Supabase Site URL + OAuth Authorization Path point to the deployed consent UI; verify DCR enabled; test registration with a proper POST using an MCP-compatible redirect URI; record result without publishing credentials.
2. **Consent flow:** exercise the deployed consent UI with a real `authorization_id`; confirm sign-in, owner gate, requested scopes, approve/deny and redirect.
3. **PKCE/token exchange:** complete authorization-code exchange, inspect JWT claims locally/securely, and verify refresh-token rotation and revocation.
4. **MCP auth acceptance:** initialize MCP, call `tools/list`, then call `friday_live_status` and `friday_inspection_snapshot`; verify unauthenticated, expired, wrong-owner, wrong-audience and revoked tokens are rejected.
5. **Dependency audit:** obtain a reproducible package lock; run npm audit with full advisory output; identify exact transitive/direct packages; patch only affected dependencies; run tests and deploy.
6. **ChatGPT connection:** provide the verified MCP URL and perform the user-side custom connector setup if required by ChatGPT account permissions. Do not claim connected before authenticated MCP calls succeed.
7. **Phone pairing:** generate owner-authorized short-lived pairing code, pair actual Friday Android installation, retrieve a timestamped fresh snapshot, and test disconnect/revocation.
8. **Closeout:** update issue #188 and this log with acceptance evidence and remaining limitations.

## Current blockers / user action
- No Auth Hook dashboard action remains; owner screenshot confirmed it enabled.
- The OAuth Server Authorization Path + Site URL configuration and DCR toggle still need verification; the hook screenshot does not prove these.
- ChatGPT-side connector creation may require a one-time action in the user's ChatGPT UI after OAuth works.
- Actual Android pairing requires the user's installed phone to participate in the pairing flow.
- Everything else should be pursued through the connected repository, Railway, Supabase and browser tools without asking the user to repeat information.

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
