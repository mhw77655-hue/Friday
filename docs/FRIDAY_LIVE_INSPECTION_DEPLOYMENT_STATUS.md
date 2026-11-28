# Friday live inspection bridge — current status

Last verified: 2026-11-28

## Current state

**Status: cloud service deployed and healthy**

The private live-inspection service is deployed and healthy. The bridge is **not yet connected to ChatGPT or to a physical phone**. Do not describe it as a completed three-way connection.

- GitHub repository: `mhw77655-hue/Friday`, branch `main`.
- Railway project: **Friday Live Inspection**.
- Railway service: `friday-inspection`.
- Production URL: https://friday-inspection-production.up.railway.app
- Latest deployment: `65affd5f-7433-43fb-b634-039903b274da` (`SUCCESS`) on documentation-only commit `d1a80f5ce5a287ddcbbfac8b86c1b608e96a372e`.
- The code deployment for merged PR #201 is `01e5c0d0-2887-4851-80ad-0972cd8b1168`, source commit `7e8041d3ff10a3e49f226d95b06820792fa95ee6`, also `SUCCESS`.
- Railway healthcheck `/health` passed on both deployments.
- External `GET /health` returns `{"service":"friday-live-inspection","status":"ok"}`.
- Service root directory is `/services/friday-inspection`; automatic redeploy watch pattern is now restricted to `services/friday-inspection/**`, so docs-only commits no longer restart the inspection service.
- Persistent volume `friday-inspection-state` is mounted at `/data` (500 MB, region `iad`).
- The production service build runs `npm ci`; Railway reports **0 vulnerabilities**.
- Supabase project `friday-auth` (`dtvhwuhmcynpbqfehlvs`) is `ACTIVE_HEALTHY`; security advisors report no lints.

## OAuth server — verified prerequisites

The Supabase OAuth 2.1 server is now discoverable at:

`https://dtvhwuhmcynpbqfehlvs.supabase.co/.well-known/oauth-authorization-server/auth/v1`

The live metadata advertises:
- Authorization, token, UserInfo, JWKS and dynamic client-registration endpoints.
- PKCE with `S256`.
- `offline_access` and refresh-token support.
- OIDC scopes including `openid`, `email` and `profile`.

The public JWKS endpoint publishes an ES256 P-256 key. The OAuth server can therefore issue verifiable OIDC ID tokens.

The owner-provided Supabase dashboard screenshot showed the **Custom Access Token Hook ENABLED**, selecting `public.friday_mcp_access_token_hook`. The database function exists, `supabase_auth_admin` has EXECUTE permission, and a read-only SQL fixture verified that OAuth authorization-code claims receive the exact Friday MCP audience while password-auth claims are not rewritten.

The applied Supabase migration version is `20261128024611`. The repository migration file was renamed to `20261128024611_friday_mcp_audience_hook.sql` to match the applied version and prevent a duplicate migration record.

### Important owner-account blocker

A read-only count of `auth.users` returned **0 users and 0 confirmed users**. The OAuth server is enabled, but there is no owner account yet.

Railway's variable-name inventory includes `FRIDAY_OWNER_EMAIL`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `PUBLIC_HOST`, `FRIDAY_STATE_PATH` and `NODE_ENV`. The Railway connector redacts variable values, so the actual allowlisted email cannot be independently read or checked here.

Create the Supabase owner account using the **same email configured in Railway's `FRIDAY_OWNER_EMAIL` variable**, then confirm the email. If the account is denied by the owner check, correct that Railway variable before retrying.

No OAuth client, authorization request or active consent exists yet in `friday-auth`; ChatGPT has not registered or authorized.

## Owner setup and pairing page

Merged PR #201: https://github.com/mhw77655-hue/Friday/pull/201

The following routes are deployed:

- `GET /oauth/consent`: owner sign-in and OAuth authorization approval.
- `GET /admin/pairing`: owner sign-in/account bootstrap and one-time phone pairing-code page; verified live after deployment.
- `GET /owner/status`: verifies owner status server-side without exposing the allowlisted email; unauthenticated request was verified to return HTTP 401.
- `POST /admin/pairing-code`: generates a one-time pairing code, expires after ten minutes and can enroll one device.
- `POST /admin/revoke`: owner-authenticated device revocation.

The owner email is no longer embedded in public HTML. Browser setup uses a normal Supabase session (`aud=authenticated`) only on admin setup routes. The MCP endpoint remains stricter: it requires the exact Friday resource audience and an OAuth `client_id`.

The remote MCP surface is source-limited to two read-only tools:
- `friday_live_status`
- `friday_inspection_snapshot`

The mutating pairing-code tool was removed from MCP; pairing stays outside the ChatGPT tool catalog.

## Android client

Published release `friday-v1.0.631` contains:
- `FridayInspectionRelayClient`
- `FridayInspectionSettingsSection`
- Process-lifecycle integration in `FridayApplication`.

The older `friday-v1.0.610` tag does **not** contain the live inspection client. No new APK is required if the phone is already on 1.0.631 or later. If it is still on 1.0.610, use the existing 1.0.631 release once; do not install intermediate builds.

The phone UI is under **Settings → Live inspection**. The relay is outbound and read-only. The snapshot is a privacy-filtered operational status report; it does not expose screen contents, conversations, files, taps or phone-control actions. Background connection is retained when Friday Runtime is active; otherwise the app may stop the relay when backgrounded.

## What is verified vs. not verified

| Item | Status |
|---|---|
| Railway service deployed and healthy | Verified |
| Supabase OAuth discovery and DCR metadata | Verified |
| Supabase ES256 JWKS and `offline_access` | Verified |
| Custom Access Token Hook shown enabled | Owner screenshot; SQL function/permission/fixture verified |
| MCP audience validator source/tests | Verified by source and unit tests; real OAuth token not yet observed |
| Owner pairing page deployed | Verified |
| Public consent HTML no longer contains owner email allowlist | Verified |
| Dependency audit | 0 vulnerabilities |
| ChatGPT dynamic client registration | Not done |
| ChatGPT PKCE authorization and token exchange | Not tested |
| Real OAuth-issued JWT claims | Not observed |
| Owner Supabase account | Not created (0 users) |
| Physical phone enrollment | Not done |
| Fresh handset snapshot from ChatGPT | Not retrieved |
| Token/consent revocation acceptance | Not tested |

## Remaining acceptance sequence

1. In **ChatGPT web**, add a custom MCP server named **Friday Live Inspection** with endpoint:
   `https://friday-inspection-production.up.railway.app/mcp`
   Choose OAuth and allow ChatGPT to discover the authorization server. The service is read-only. ChatGPT custom MCP setup is web-only; Pro accounts can connect read/fetch tools, while full MCP is available to Business and Enterprise/Edu according to OpenAI's current guidance: https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt
2. When redirected to `/oauth/consent`, create or sign in to the Supabase owner account using the email matching Railway's `FRIDAY_OWNER_EMAIL`; confirm email if signing up; review the client/scopes and approve.
3. Verify ChatGPT discovers exactly `friday_live_status` and `friday_inspection_snapshot`; call `friday_live_status` and confirm the OAuth token has the expected issuer, audience, expiry and client binding.
4. Open https://friday-inspection-production.up.railway.app/admin/pairing, sign in as the owner and generate the one-time code.
5. On a phone running Friday 1.0.631 or later, open **Settings → Live inspection**, enter server URL `https://friday-inspection-production.up.railway.app` and the one-time code, and enable inspection.
6. Call `friday_live_status` and `friday_inspection_snapshot` from the connected ChatGPT app. Verify a fresh timestamp and matching enrolled device.
7. Revoke the device and OAuth consent, then verify the expected denial behavior. Current JWT validation checks signature, issuer, audience, expiry, owner and client binding; immediate access-token revocation semantics still need a real-flow acceptance test.

## Historical notes

- The service started as a separate Railway project so the Venture Autopilot Supabase project remained untouched.
- The MCP surface was hardened to be strictly read-only before ChatGPT connection.
- The repository cleanup log is maintained separately at `docs/FRIDAY_REPOSITORY_STRUCTURE_AND_MIGRATION.md`.
- Progress and acceptance decisions are also recorded in GitHub issue #188: https://github.com/mhw77655-hue/Friday/issues/188

**Truthful completion rule:** the bridge is not complete until a real ChatGPT OAuth client is registered, the owner authorizes it, the phone is enrolled, ChatGPT retrieves a fresh phone snapshot, and revocation is tested.
