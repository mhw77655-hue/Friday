# Friday live inspection bridge — current status

Last verified: 2026-11-28

## Current state

**Status: cloud service deployed and healthy**

The private live-inspection service is deployed and healthy. The bridge is **not yet connected to ChatGPT or to a physical phone**. Do not describe it as a completed three-way connection.

- GitHub repository: `mhw77655-hue/Friday`, branch `main`.
- Railway project: **Friday Live Inspection**.
- Railway service: `friday-inspection`.
- Production URL: https://friday-inspection-production.up.railway.app
- Latest production deployment: `7a5951e4-2166-4008-8813-ed52f6377211` (`SUCCESS`), source commit `c4c3a7ffc7e8d984ed7e03963d1c20cabca39725` (merged PR #202).
- Previous owner-pairing code from PR #201 remains included in this deployment.
- Railway healthcheck `/health` passed; external health and metadata endpoints were fetched successfully after deployment.
- External `GET /health` returns `{"service":"friday-live-inspection","status":"ok"}`.
- Service root directory is `/services/friday-inspection`; automatic redeploy watch pattern is now restricted to `services/friday-inspection/**`, so docs-only commits no longer restart the inspection service.
- Persistent volume `friday-inspection-state` is mounted at `/data` (500 MB, region `iad`).
- The production service build runs `npm ci`; Railway reports **0 vulnerabilities**. Main-branch service tests and dependency audit both completed `SUCCESS` on commit `c4c3a7ffc7e8d984ed7e03963d1c20cabca39725`.
- Supabase project `friday-auth` (`dtvhwuhmcynpbqfehlvs`) is `ACTIVE_HEALTHY`; security advisors report no lints.

## OAuth server — verified prerequisites

The Supabase OAuth 2.1 server is now discoverable at:

`https://dtvhwuhmcynpbqfehlvs.supabase.co/.well-known/oauth-authorization-server/auth/v1`

The live metadata was previously verified to advertise authorization, token, UserInfo, JWKS and dynamic client-registration endpoints, with PKCE `S256`. Supabase's current OAuth flow documentation confirms authorization-code + PKCE and a `refresh_token` grant; the token response includes a refresh token. Friday now requests only the least-privilege `openid email` scopes in its protected-resource metadata and tool security declarations. The real ChatGPT token/refresh exchange remains untested.

The public JWKS endpoint publishes an ES256 P-256 key. The OAuth server can therefore issue verifiable OIDC ID tokens.

The owner-provided Supabase dashboard screenshot showed the **Custom Access Token Hook ENABLED**, selecting `public.friday_mcp_access_token_hook`. The database function exists, `supabase_auth_admin` has EXECUTE permission, and a read-only SQL fixture verified that OAuth authorization-code claims receive the exact Friday MCP audience while password-auth claims are not rewritten.

The applied Supabase migration version is `20261128024611`. The repository migration file was renamed to `20261128024611_friday_mcp_audience_hook.sql` to match the applied version and prevent a duplicate migration record.

### Important owner-account blocker

A fresh read-only query returned **0 `auth.users`, 0 OAuth clients, 0 OAuth authorizations and 0 OAuth consents**. The OAuth server is enabled, but no owner account or ChatGPT client has been created yet.

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

Merged PR #202 adds per-tool `_meta.securitySchemes` (`oauth2`, scopes `openid email`) and scopes the protected-resource metadata to `openid email`. Unauthenticated/invalid MCP requests now return a scope-aware `WWW-Authenticate` challenge with the resource-metadata URL. Browser owner-setup routes deliberately do not emit the MCP OAuth challenge. JWT validation now rejects future `nbf` values as well as checking signature, issuer, audience, expiry, issued-at, subject, client binding and owner email.

The mutating pairing-code tool was removed from MCP; pairing stays outside the ChatGPT tool catalog.

## Android client

Published release `friday-v1.0.631` contains:
- `FridayInspectionRelayClient`
- `FridayInspectionSettingsSection`
- Process-lifecycle integration in `FridayApplication`.

The owner confirmed the phone is currently on **Friday 1.0.610**, which does **not** contain the live inspection client. It must be updated once to 1.0.631 or later; no intermediate builds are needed. Official APK: https://github.com/mhw77655-hue/Friday/releases/download/friday-v1.0.631/app-release.apk. GitHub release asset SHA-256: `ef2a34966d87fab617d43882e2678a4c1999ee207046450933fb606f8d557b47`. The release uses the pinned Friday signing certificate and is intended to install over 1.0.610 without uninstalling.

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
| Dependency audit | 0 vulnerabilities; main CI passed |
| ChatGPT dynamic client registration | Not done |
| ChatGPT PKCE authorization and token exchange | Not tested |
| Real OAuth-issued JWT claims | Not observed |
| Owner Supabase account | Not created (0 users) |
| Physical phone enrollment | Not done |
| Fresh handset snapshot from ChatGPT | Not retrieved |
| Token/consent revocation acceptance | Not tested |

## Remaining acceptance sequence

1. Update the phone from Friday 1.0.610 to the official 1.0.631 APK linked above. Install over the existing app; do not uninstall.
2. In **ChatGPT web**, add a custom MCP server named **Friday Live Inspection** with endpoint:
   `https://friday-inspection-production.up.railway.app/mcp`
   Choose OAuth and allow ChatGPT to discover the authorization server. The service is read-only. ChatGPT custom MCP setup is web-only; Pro accounts can connect read/fetch tools, while full MCP is available to Business and Enterprise/Edu according to OpenAI's current guidance: https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt
3. When redirected to `/oauth/consent`, create or sign in to the Supabase owner account using the email matching Railway's `FRIDAY_OWNER_EMAIL`; confirm email if signing up; review the client/scopes and approve.
4. Verify ChatGPT discovers exactly `friday_live_status` and `friday_inspection_snapshot`; call `friday_live_status` and confirm the OAuth token has the expected issuer, audience, expiry, `nbf`, and client binding.
5. Open https://friday-inspection-production.up.railway.app/admin/pairing, sign in as the owner and generate the one-time code.
6. On the updated phone, open **Settings → Live inspection**, enter server URL `https://friday-inspection-production.up.railway.app` and the one-time code, and enable inspection.
7. Call `friday_live_status` and `friday_inspection_snapshot` from the connected ChatGPT app. Verify a fresh timestamp and matching enrolled device.
8. Revoke the device and OAuth consent, then verify the expected denial behavior. Immediate access-token revocation semantics still need a real-flow acceptance test.

## Historical notes

- The service started as a separate Railway project so the Venture Autopilot Supabase project remained untouched.
- The MCP surface was hardened to be strictly read-only before ChatGPT connection.
- The repository cleanup log is maintained separately at `docs/FRIDAY_REPOSITORY_STRUCTURE_AND_MIGRATION.md`.
- Progress and acceptance decisions are also recorded in GitHub issue #188: https://github.com/mhw77655-hue/Friday/issues/188

**Truthful completion rule:** the bridge is not complete until a real ChatGPT OAuth client is registered, the owner authorizes it, the phone is enrolled, ChatGPT retrieves a fresh phone snapshot, and revocation is tested.
