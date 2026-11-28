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

### Important security/implementation findings
- Do not treat OAuth metadata discovery as proof of client registration.
- Dynamic registration must be exercised as a valid POST request and response.
- Do not treat the consent page HTTP 200 as proof it uses Supabase OAuth authorization APIs correctly.
- The hook must preserve every required Supabase JWT claim. Test actual issued OAuth and refresh tokens; do not infer the final `aud` claim from SQL source alone.
- Verify exact resource audience, issuer, signature, expiry, subject, client_id, owner identity and any intended scopes on a real issued token.
- Check whether current MCP auth code enforces scope/client authorization, not only owner email and audience.
- No actual ChatGPT MCP authenticated `tools/list` or `tools/call` has yet been accepted.
- No Android device is paired and no live phone snapshot has been retrieved.
- Do not expose secrets, JWTs, owner email, private phone diagnostics, transcripts, or memory in public GitHub comments.

## Remaining work — ordered execution
1. **OAuth app/client registration:** test Supabase DCR with a proper POST using an MCP-compatible redirect URI; record client registration outcome without publishing credentials.
2. **Consent flow:** inspect and exercise the deployed consent UI with a real `authorization_id`; confirm it fetches authorization details, authenticates the owner, displays requested scopes/client, and calls approve/deny methods.
3. **PKCE/token exchange:** complete authorization-code exchange, inspect JWT claims locally/securely, and verify refresh-token rotation and revocation.
4. **MCP auth acceptance:** initialize MCP, call `tools/list`, then call `friday_live_status` and `friday_inspection_snapshot`; verify unauthenticated, expired, wrong-owner, wrong-audience and revoked tokens are rejected.
5. **Dependency audit:** obtain a reproducible package lock; run npm audit with full advisory output; identify exact transitive/direct packages; patch only affected dependencies; run tests and deploy.
6. **ChatGPT connection:** provide the verified MCP URL and perform the user-side custom connector setup if required by ChatGPT account permissions. Do not claim connected before authenticated MCP calls succeed.
7. **Phone pairing:** generate owner-authorized short-lived pairing code, pair actual Friday Android installation, retrieve a timestamped fresh snapshot, and test disconnect/revocation.
8. **Closeout:** update issue #188 and this log with acceptance evidence and remaining limitations.

## Current blockers / user action
- No Auth Hook dashboard action remains; owner screenshot confirmed it enabled.
- ChatGPT-side connector creation may require a one-time action in the user's ChatGPT UI after OAuth works.
- Actual Android pairing requires the user's installed phone to participate in the pairing flow.
- Everything else should be pursued through the connected repository, Railway, Supabase and browser tools without asking the user to repeat information.

## Current truthful status
Cloud service and Auth Hook configuration are verified. OAuth registration, owner consent, PKCE token issuance, authenticated MCP calls, ChatGPT connector acceptance, physical phone pairing, and fresh phone diagnostics are NOT YET VERIFIED.
