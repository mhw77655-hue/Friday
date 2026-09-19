# Jarvis Next Build Target

## Immediate objective

Reach the Builder Handover Point.

Jarvis must eventually be able to:

REQUEST
 UNDERSTAND
 PLAN
 RESEARCH
 MODIFY CODE
 BUILD
 RUN
 OBSERVE
 DIAGNOSE
 REPAIR
 VERIFY
 ROLLBACK

without requiring a human to manually direct every development step.

## Current local AI substrate

Primary local model:
LFM2.5-1.2B-Instruct-Q4_K_M

Inference runtime:
llama.cpp

Local endpoint:
http://127.0.0.1:8080

Secondary model:
Qwen3-1.7B-Q4_K_M

Qwen is currently an on-demand candidate, not a permanently resident model.

## Resource rule

Do not keep unnecessary models resident simultaneously.

Use model lifecycle/resource management before adding more local models.

## Architecture rule

Models are replaceable computational substrates.

Jarvis cognition must not become coupled directly to one model.

## Development order

1. Establish truthful current build state.
2. Prove the Jarvis cognitive/execution spine.
3. Complete the Builder.
4. Add Builder self-repair.
5. Integrate research and Algorithm Atlas.
6. Add limitation-breaking/self-audit mechanisms.
7. Add brain/component reconfiguration.
8. Add self-evolution.
9. Add distributed presence/continuity.
