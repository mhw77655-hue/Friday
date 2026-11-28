## 2026-11-28 — first Railway deployment is healthy; OAuth and handset pairing remain blocked

Verified directly through the Railway integration:
- Project: Friday Live Inspection; production environment.
- Service: `friday-inspection`, source `mhw77655-hue/Friday`, branch `main`.
- Deployment `301cc907-2b40-46f3-86f9-cf8f178c4557` status is `SUCCESS`, deployed commit `7932f7c416567241d8353b0e51afc923477cdf5b`.
- One production replica is running in `iad`; 500 MB persistent volume `friday-inspection-state` is mounted at `/data`.
- Railway build healthcheck succeeded for `/health`; runtime log says the Node service is listening on port 8080.
- External HTTPS smoke tests returned HTTP content successfully:
  - `/health` → `{"service":"friday-live-inspection","status":"ok"}`
  - `/` → service status ok
  - `/.well-known/oauth-protected-resource` → returns resource metadata
  - `/oauth/consent` → serves the owner consent page.
- The Supabase project `friday-auth` is `ACTIVE_HEALTHY`; Supabase security advisors currently return no lints.
- Railway has two high-severity npm audit findings in the build output. Exact packages have not yet been identified; do not run `npm audit fix --force` blindly.

Important blockers found during acceptance checks:
- The documented Supabase OAuth authorization-server discovery route `https://dtvhwuhmcynpbqfehlvs.supabase.co/.well-known/oauth-authorization-server/auth/v1` currently returns 404, and `/auth/v1/oauth/clients/register` also returns 404. OAuth 2.1 server / dynamic client registration is therefore not enabled or not serving yet. The OIDC configuration route under `/auth/v1/.well-known/openid-configuration` responds, but it does not advertise a registration endpoint.
- Railway environment variable names show `FRIDAY_OWNER_EMAIL` is not configured. The service deliberately fails closed for MCP and owner/admin operations until the owner email allowlist is set.
- Code audit found `friday_create_pairing_code` is currently exposed as an MCP tool even though it mutates pairing state. This contradicts the intended read-only MCP surface and must be moved to the separate owner-authorized admin/setup surface before connecting ChatGPT. Do not present the MCP tool list as read-only until corrected.
- The MCP resource server currently validates Supabase access tokens using `auth.getUser` and an owner-email allowlist, but the complete ChatGPT OAuth acceptance still needs correct resource/audience and scope enforcement. Do not register this endpoint as production-ready until that is verified.
- No device is enrolled or connected; no live handset snapshot or revocation acceptance exists.

Next:
1. Enable Supabase Authentication → OAuth Server, set Authorization Path to `/oauth/consent`, enable dynamic client registration only as required for ChatGPT, and verify the documented metadata/registration endpoints.
2. Set the owner email allowlist on the Railway service.
3. Remove the mutating pairing-code MCP tool; retain owner-authenticated pairing setup separately. Add/verify per-tool OAuth security metadata and resource/audience/scope checks.
4. Resolve the two high npm audit findings with targeted dependency updates and regression tests.
5. Re-deploy and test OAuth discovery, registration, consent, token exchange, invalid token, wrong audience, missing scope and revocation.
6. Connect the custom MCP server in ChatGPT web, generate a one-time pairing code via owner setup, pair the physical Android app, then prove fresh snapshot and revocation from this exact ChatGPT session.

Venture Autopilot remains untouched. This deployment is a real cloud-service milestone, not proof that ChatGPT or the phone is connected.

### Follow-up hardening — 2026-11-28

- Removed `friday_create_pairing_code` from the remote MCP tool catalog because it mutates server pairing state and therefore violated the read-only MCP contract.
- Pairing code creation remains available only through the owner-authenticated `POST /admin/pairing-code` setup endpoint; ChatGPT's MCP surface is now limited to `friday_live_status` and `friday_inspection_snapshot`.
- Source commit: `d012c8df271a82945b56fcac5bef52f166ded46c`.
- Railway deployment for this source commit is currently building. Do not claim the tightened MCP tool catalog is live until that deployment succeeds and the endpoint is rechecked.


### Admin revocation authorization fix — 2026-11-28

- Source audit confirmed `POST /admin/revoke` called the asynchronous `adminOnly()` guard without `await`, allowing the handler to continue before owner authorization resolved.
- Fixed the guard to `if (!await adminOnly(request, response)) return;` in `services/friday-inspection/src/server.js`.
- Commit: `7239a906c19f6246fa2b6d24f8df7e7d8609ed1f` (`Await owner authorization before admin revocation`).
- Railway deployment `e2440eeb-399f-42f1-801f-7abe63d1c297` is BUILDING; do not claim the fix is live until SUCCESS and runtime rejection checks pass.
- Remaining acceptance: OAuth DCR POST + PKCE consent/token flow; verify resource/audience/scope enforcement; test invalid/expired/wrong-owner tokens; inspect npm audit findings; ChatGPT custom MCP connection; pair physical handset; prove fresh snapshot and revocation.
