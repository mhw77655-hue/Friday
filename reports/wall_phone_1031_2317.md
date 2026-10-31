# wall finder, phone side (2026-10-31T23:17:25Z)
## client
path: /usr/bin/opencode  version: 1.18.20  npm: /usr/bin/npm
## free models the client lists
opencode/beam-free
opencode/deepseek-v4.1-flash-free
opencode/flint-40b-free
opencode/glm-5.3-flash-free
opencode/mimo-v2.6-flash-free
opencode/nemotron-3.5-lightning-free
opencode/nemotron-3.5-super-vl-free
## network from the phone
https://opencode.ai/zen/v1/models http=200 t=0.604249s
https://models.dev/api.json http=200 t=7.371347s
https://api.github.com/zen http=200 t=0.289995s
curl: (28) Operation timed out after 15002 milliseconds with 12786973 out of 25068536 bytes received
https://registry.npmjs.org/opencode-ai http=200 t=15.002555s
## the probe that hung all night, 150 s cap
rc=124 secs=150
[0m
> build · big-pickle
[0m
## opencode log lines (errors, limits, retries)
timestamp=2026-10-31T01:32:56.003Z level=ERROR run=e0b0d202 message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e706aa2f9ffe7GBanC7xSWsViw small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reached. Fre
timestamp=2026-10-31T01:53:50.006Z level=ERROR run=be0e6fbb message="stream error" providerID=opencode modelID=deepseek-v4.1-flash-free session.id=ses_e7056a0e5ffeHAMPkz9A6tWZWH small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 att
timestamp=2026-10-31T01:54:47.846Z level=ERROR run=be0e6fbb message="stream error" providerID=opencode modelID=deepseek-v4.1-flash-free session.id=ses_e7056a0e5ffeHAMPkz9A6tWZWH small=false agent=build mode=primary error.error="AI_APICallError: Free usage limi
timestamp=2026-10-31T02:00:57.684Z level=ERROR run=cc969f75 message="stream error" providerID=opencode modelID=glm-5.3-flash-free session.id=ses_e705037a3ffeRZoDTqN4vg02r2 small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts.
timestamp=2026-10-31T02:01:47.989Z level=ERROR run=cc969f75 message="stream error" providerID=opencode modelID=glm-5.3-flash-free session.id=ses_e705037a3ffeRZoDTqN4vg02r2 small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reac
timestamp=2026-10-31T02:07:47.403Z level=INFO run=94fa62d2 message=stream providerID=opencode modelID=big-pickle session.id=ses_e704995a4ffeMFUjM15DBiFJfB small=true agent=title mode=primary
timestamp=2026-10-31T02:08:06.491Z level=ERROR run=94fa62d2 message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e704995a4ffeMFUjM15DBiFJfB small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts. Last er
timestamp=2026-10-31T02:09:18.429Z level=INFO run=94fa62d2 message="llm runtime selected" llm.runtime=ai-sdk llm.provider=opencode llm.model=big-pickle
timestamp=2026-10-31T02:09:18.809Z level=ERROR run=94fa62d2 message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e704995a4ffeMFUjM15DBiFJfB small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reached. Fre
timestamp=2026-10-31T02:30:15.377Z level=ERROR run=2281a470 message="stream error" providerID=opencode modelID=deepseek-v4.1-flash-free session.id=ses_e70357280ffe7xRRyHMlzhP1Az small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 att
timestamp=2026-10-31T02:32:05.612Z level=ERROR run=2281a470 message="stream error" providerID=opencode modelID=deepseek-v4.1-flash-free session.id=ses_e70357280ffe7xRRyHMlzhP1Az small=false agent=build mode=primary error.error="AI_APICallError: Free usage limi
timestamp=2026-10-31T02:37:36.046Z level=ERROR run=2f0eff3d message="stream error" providerID=opencode modelID=glm-5.3-flash-free session.id=ses_e702ec325ffe0CBRLk17qsTokG small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts.
timestamp=2026-10-31T02:39:46.746Z level=ERROR run=2f0eff3d message="stream error" providerID=opencode modelID=glm-5.3-flash-free session.id=ses_e702ec325ffe0CBRLk17qsTokG small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reac
timestamp=2026-10-31T02:44:49.269Z level=ERROR run=7647bdd3 message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e7028398bffeDe4QxrAYBY5NLk small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts. Last er
timestamp=2026-10-31T02:46:58.474Z level=ERROR run=7647bdd3 message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e7028398bffeDe4QxrAYBY5NLk small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reached. Fre
timestamp=2026-10-31T03:06:14.442Z level=ERROR run=089f5a33 message="stream error" providerID=opencode modelID=deepseek-v4.1-flash-free session.id=ses_e70146ca0ffe2ZRRE5Vw2xyYqi small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 att
timestamp=2026-10-31T03:07:37.842Z level=ERROR run=089f5a33 message="stream error" providerID=opencode modelID=deepseek-v4.1-flash-free session.id=ses_e70146ca0ffe2ZRRE5Vw2xyYqi small=false agent=build mode=primary error.error="AI_APICallError: Free usage limi
timestamp=2026-10-31T03:45:17.808Z level=ERROR run=60c1d5b6 message="stream error" providerID=opencode modelID=glm-5.3-flash-free session.id=ses_e6ff7af20ffeAixEoQszCr6WPs small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts.
timestamp=2026-10-31T04:16:20.514Z level=ERROR run=60c1d5b6 message="stream error" providerID=opencode modelID=glm-5.3-flash-free session.id=ses_e6ff7af20ffeAixEoQszCr6WPs small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reac
timestamp=2026-10-31T05:12:31.675Z level=ERROR run=c52860df message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e6fa229a1ffeayaoNRpuxW7zti small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts. Last er
timestamp=2026-10-31T05:57:03.359Z level=ERROR run=c52860df message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e6fa229a1ffeayaoNRpuxW7zti small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reached. Fre
timestamp=2026-10-31T07:24:10.625Z level=ERROR run=6c4bae3c message="stream error" providerID=opencode modelID=deepseek-v4.1-flash-free session.id=ses_e6f3112c9ffeRLS7RxzHjDCV2t small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 att
timestamp=2026-10-31T07:42:45.385Z level=ERROR run=6c4bae3c message="stream error" providerID=opencode modelID=deepseek-v4.1-flash-free session.id=ses_e6f3112c9ffeRLS7RxzHjDCV2t small=false agent=build mode=primary error.error="AI_APICallError: Free usage limi
timestamp=2026-10-31T07:49:06.505Z level=ERROR run=87fc74e0 message="stream error" providerID=opencode modelID=glm-5.3-flash-free session.id=ses_e6f130ffcffeYHCdZDh79LXT07 small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts.
timestamp=2026-10-31T07:53:33.264Z level=ERROR run=87fc74e0 message="stream error" providerID=opencode modelID=glm-5.3-flash-free session.id=ses_e6f130ffcffeYHCdZDh79LXT07 small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reac
timestamp=2026-10-31T07:58:28.378Z level=ERROR run=624eac4b message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e6f0a9ef9ffeQLUFSJPg70AvBS small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts. Last er
timestamp=2026-10-31T08:09:33.574Z level=ERROR run=624eac4b message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e6f0a9ef9ffeQLUFSJPg70AvBS small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reached. Fre
timestamp=2026-10-31T23:18:10.607Z level=WARN run=2a47f923 message="failed to initialize fff" error="Failed to init file picker: Can not run certain FFF features in a file system root or home directories. Consider smaller per-project directories."
timestamp=2026-10-31T23:18:14.524Z level=ERROR run=2a47f923 message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e6bbe7d1dffeTMb6xOeGQVKkIk small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reached. Fre
timestamp=2026-10-31T23:18:18.249Z level=ERROR run=2a47f923 message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e6bbe7d1dffeTMb6xOeGQVKkIk small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts. Last er
## last night full preflight output (/root/pf.txt)
===============================================================
  PREFLIGHT — GITHUB_TOKEN + model connection, before any iteration
===============================================================
PREFLIGHT .... [github]: GET https://api.github.com/user (timeout 20s, token length 40)
PREFLIGHT OK   [github]: token accepted by GitHub (user: mhw77655-hue).
PREFLIGHT .... [model]: one-token probe of opencode / opencode/big-pickle (timeout 420s)
PREFLIGHT FAIL [model]: the opencode probe on opencode/big-pickle exited 124.
  --- raw tool output (verbatim, last 40 lines) ---
[0m
> build · big-pickle
[0m
  ----------------------------------
--preflight-only: a check failed, exiting 1 without iterating.
## newest client from npm, tested here in an isolated home so your setup is untouched
npm rc=0
version: 1.19.5
new client probe: rc=124 secs=150
[0m
> build · big-pickle
[0m
timestamp=2026-10-31T23:23:54.612Z level=ERROR run=6ddb6d19 message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e6bb93147ffeY5gBZzDYQ1RnZr small=false agent=build mode=primary error.error="AI_APICallError: Free usage limit reached. Fre
timestamp=2026-10-31T23:24:00.359Z level=ERROR run=6ddb6d19 message="stream error" providerID=opencode modelID=big-pickle session.id=ses_e6bb93147ffeY5gBZzDYQ1RnZr small=true agent=title mode=primary error.error="AI_RetryError: Failed after 3 attempts. Last er
