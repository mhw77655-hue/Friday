# wall probe from a GitHub runner 2026-10-31T23:17:34Z, cpus=4, MemTotal: 16365628 kB
client: npm rc=0 version=1.19.5
## free models listed
opencode/beam-free
opencode/deepseek-v4.1-flash-free
opencode/flint-40b-free
opencode/glm-5.3-flash-free
opencode/mimo-v2.6-flash-free
opencode/nemotron-3-ultra-free
opencode/nemotron-3.5-lightning-free
opencode/nemotron-3.5-super-vl-free
## network from the runner
https://opencode.ai/zen/v1/models http=200 t=0.159187s
https://models.dev/api.json http=200 t=0.206069s
https://api.github.com/zen http=200 t=0.156801s
## probes, 90 s cap each, in parallel
opencode/big-pickle rc=0 secs=7
opencode/beam-free rc=0 secs=7
opencode/deepseek-v4.1-flash-free rc=0 secs=7
opencode/flint-40b-free rc=0 secs=7
--- /tmp/p1.out, last 6 lines
[0m
> build · big-pickle
[0m
ok
--- /tmp/p2.out, last 6 lines
[0m
> build · beam-free
[0m
ok
--- /tmp/p3.out, last 6 lines
[0m
> build · deepseek-v4.1-flash-free
[0m
ok
--- /tmp/p4.out, last 6 lines
[0m
> build · flint-40b-free
[0m
ok
## opencode log lines
VERDICT: 4 of 4 probes answered from the runner
