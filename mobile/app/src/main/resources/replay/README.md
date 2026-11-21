# recorded_turns.jsonl — the CONTINUITY-LAW replay fixture

ONE copy of this file, on the app's classpath, read by the production
composition, the JVM twin and the tests alike
(`RecordedTurns.fromClasspath()` in
`com.friday/app/cognition/workspace/ReplayCheck.kt`). A second copy of a
baseline is a second baseline, which is the exact failure this story exists to
remove — so if you are about to copy it somewhere else, you are about to break
the check.

## Owner-authorized identity baseline

The source transcripts below were captured while the legacy app identity was named FRIDAY. The current entity's owner-authorized identity is FRIDAY. The `expectedIdentity` values therefore record the current continuity baseline (`identity:name=Friday`) while preserving the original verbatim turn inputs and their provenance. This is an explicit baseline migration, not a claim that the historical transcript originally said Friday.

## What a line is

One recorded turn per line, JSON Lines, append-only:

    turnId            the turn's stable id
    input             the real user text, verbatim
    provenance        where that text actually came from — REQUIRED, and a blank
                      value is a hard error at load time
    expectedIdentity  the human-authored facts this turn's answer must still
                      carry after any number of accepted slow changes

## How the expectations were authored

By a person, from the real cold-start baseline this repository actually
produces:

- `identity:name` = `FRIDAY` — `FallbackIdentity.name`
  (`humancore/fallback/FallbackIdentity.kt`), reached in production through
  `SelfModel.identity()` over `HumanCoreIdentitySource`.
- `identity:version` = `1` — `FallbackIdentity.record.version`.
- `user` = `Venon` — `WorldModelService.USER_NODE_NAME`.

These are CORE fields. A legitimate accepted SLOW change may move a persona
trait or a trust tier; it may never move these. That is the whole point of
authoring CORE facts and nothing else here: an expectation on a SLOW field
would fail on exactly the change the law is supposed to permit.

**Maintenance, stated plainly:** when HumanCore's durable identity is revised
by an authorized path, a human updates this file in the same change. That is the
intended loop, not a workaround — the fixture is the record of what the
deployment's identity is contractually allowed to be.

## What the check does with it

`ReplayCheck` renders each turn against current state (live CORE identity, the
durable `ChangeLog` for SLOW fields, live claims for FAST fields) and then
fails the suite on any of:

1. an `expectedIdentity` fact that no longer holds;
2. a CORE field whose value differs from the baseline;
3. an answer that no longer covers `ReplayCheck.MIN_FIELD_SIMILARITY` of the
   baseline's identity fields.
