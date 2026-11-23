# Friday ChatGPT plan Seat

## Purpose

Friday can use an owner's eligible ChatGPT plan as one explicitly authorized Intelligence Seat in the local Table and Builder. This is separate from the OpenAI API key/billing path. It uses OpenAI's documented open-source Sign in with ChatGPT (SIWC) token-sharing flow and the public Responses API.

## Authentication contract

- First registration uses `client_id=dynamic_agent_client`, `agent_name_hint=Friday`, and a stable per-installation `ext_agent_host_id`.
- The issued `oaiapp_...` client ID returned by the callback is retained and reused for later sign-ins.
- Authorization uses the system browser, loopback HTTP callback on `127.0.0.1` with the exact `/auth/callback` path, random state and nonce, and PKCE S256.
- Authorization, token, JWKS and revocation endpoints are loaded from OpenAI's production OpenID discovery document; the issuer and endpoint origin are validated against `https://auth.openai.com` before use.
- Token exchange uses the issued client ID, code verifier, exact redirect URI and `resource=https://api.openai.com/v1`. No client secret or API key is used.
- The ID token is verified against the configured OpenAI JWKS. Friday checks RS256 signature, issuer, audience/authorized party, expiry, issued-at, nonce and subject before storing credentials.
- The token response must include `chatgpt.tokens.use.direct`; a valid identity token alone is insufficient.
- Access/refresh/ID tokens and account identity are encrypted with an Android Keystore AES-GCM key. Tokens are never written to Table logs, crash reports or GitHub diagnostics.
- A newly issued client ID is persisted as pending before code exchange, so an `invalid_grant` retry uses that issued ID rather than registering a duplicate client.
- Refresh is serialized across all in-process Seat instances to protect rotating refresh tokens; refresh rotates credentials atomically and preserves the issued client ID and account identity.
- Sign-out discovers OpenID configuration, revokes the renewable refresh-token session, then clears local access/refresh/ID tokens while retaining the encrypted account/client mapping and host ID. If remote revocation cannot be confirmed after bounded retries, Friday reports that honestly and points to ChatGPT Settings.

## Inference and privacy contract

- Model choices are fetched from `GET /v1/models` for the currently authorized account and filtered to `visibility=list`.
- Inference uses `POST /v1/responses` with the OAuth access token, `store=false`, and `stream=true`.
- The stream is accepted only after `response.completed`. Failed, incomplete or interrupted streams are not treated as successful reviews.
- The Seat accepts only `SeatDataClass.PUBLIC` requests with `externallyShareable=true`. Private, sensitive and unapproved data is rejected before token access or network activity.
- Background use is disabled by default. The owner must separately enable the Settings switch after sign-in. Sign-in alone never authorizes background Builder use.
- Usage limits are surfaced as rate limiting. Friday does not silently switch to a paid API key, a different account, or an unapproved provider.
- This integration does not provide access to ChatGPT conversations, chat history, memories or private ChatGPT UI state. It provides only the explicitly authorized Responses API inference capability.

## Builder integration

When background consent is enabled and the account has the direct-plan scope, the ChatGPT Seat joins the local Friday Table alongside the existing anonymous OVH guest. All external replies are retained in the Table transcript and presented to the local Builder as untrusted advisory input. The local Friday conductor remains authoritative; external Seats cannot execute commands, approve merges or release artifacts.

## Tests and proof

Instrumentation tests cover Android Keystore credential round-tripping and ciphertext-only persistence, explicit consent default-off behavior, private-data rejection before token/network access, Responses API request flags and stream-completion handling, rate-limit/no-paid-fallback behavior, and RS256 ID-token verification against a test JWKS.

A successful CI run proves the implementation and mocked protocol contracts. Actual account authorization, account eligibility, model availability and real inference must still be verified by the owner completing the in-app sign-in and Friday recording a successful Seat turn. Plan limits and account eligibility are controlled by OpenAI, not Friday.
