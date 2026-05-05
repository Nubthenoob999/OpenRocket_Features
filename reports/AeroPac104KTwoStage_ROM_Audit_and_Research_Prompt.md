# AeroPac 104K Two-Stage ROM Audit and Research Prompt

## Target
- ORK: `core/src/test/java/info/openrocket/core/tuning/Above_Mach_1/AeroPac104kTwoStage/AeroPac104KStageOne&Two-2_ROM.CDX1.ork`
- Expected apogee: `104659 ft`
- Reproduction command:
  - `./gradlew.bat :core:test --tests "info.openrocket.core.tuning.AboveMachOneAuditTest.auditAeroPac104KTwoStageMachCase" --no-daemon`
- Audit harness:
  - `core/src/test/java/info/openrocket/core/tuning/AboveMachOneAuditTest.java`

## Executive Summary
The current headless reproduction does **not** show a negative-pressure exception.

With ROM enabled, the simulation aborts at `2.528 s` with `Stage began to tumble under thrust`, producing only about `504.6 ft` of apogee and `Mach 0.355`.

With ROM disabled on the same ORK and same stage configuration, the flight completes normally enough to:
- burn out the booster at `16.135 s`
- separate stages at `16.135 s`
- ignite the sustainer at `22.135 s`
- reach about `58012.9 ft`
- reach `Mach 2.439`

This means the catastrophic `~505 ft` failure is not explained by atmosphere setup or a simple stage-timing misconfiguration alone. It is ROM-path-specific, or ROM-amplified, during early boost. After that problem is fixed, there is still a second, separate underprediction gap: even the ROM-disabled baseline is still about `46646 ft` below the expected apogee.

## Reproduced Evidence
### ROM-enabled run
- `useISA=true`
- `launchPressurePa=88355.733`
- `romEnabled=true`
- `romMode=PATHLINE_STANDARD`
- `romSurfaceSource=PATHLINE_ACTIVE`
- `simulationStatus=ABORTED`
- `fallbackSamples=0`
- `lowConfidenceSamples=0`

### Stage and motor configuration
- Active stages: `2`
- Sustainer stage separation: `Current stage ejection charge`, delay `0.000 s`
- Booster stage separation: `Current stage motor burnout`, delay `0.000 s`
- Sustainer motor: `M685W-Proto-P`, ignition `First burnout of previous stage`, delay `6.000 s`
- Booster motor: `N1048-0`, ignition `Automatic (launch or ejection charge)`, delay `0.000 s`

### ROM-enabled event chain
- `Launch @ 0.000 s`
- `Booster motor ignition @ 0.000 s`
- `Lift-off @ 0.210 s`
- `Launch rod clearance @ 0.520 s`
- `Warning @ 2.399 s: Large angle of attack encountered (39.8°)`
- `Simulation abort @ 2.528 s: Stage began to tumble under thrust`

### ROM trace hints
The ROM trace and summary show:
- `fallback_weight=0`
- `fallbackSamples=0`
- early `boundary-layer separation detected`
- early `boundary-layer marcher became stiff`
- early `angle of attack exceeds trusted Phase I band`

That means the problematic run is not just falling back to legacy aerodynamics. The active ROM/pathline force path is actually being used.

### ROM-disabled comparison run
- `romMode=BARROWMAN_ONLY`
- `romSurfaceSource=PATHLINE_DISABLED`
- `simulationStatus=Up To Date`
- branch count: `2`
- warnings: `No recovery device defined` and `Body calculations may not be entirely accurate at supersonic speeds`

ROM-disabled event chain:
- `Booster ignition @ 0.000 s`
- `Booster burnout @ 16.135 s`
- `Stage separation @ 16.135 s`
- `Sustainer ignition @ 22.135 s`
- `Sustainer burnout @ 31.745 s`
- `Apogee @ 68.667 s`
- final apogee about `58012.9 ft`

## Primary Conclusion
The first issue to fix is **not** negative pressure.

The first issue to fix is: **why the ROM-enabled path drives the stacked vehicle into a tumble-under-thrust abort before booster burnout**.

Until that is fixed, the sustainer never ignites, so the `~505 ft` result is not a meaningful high-Mach ROM tuning outcome. It is an early-flight stability failure.

## Research Priorities
1. Isolate the ROM term that drives the early divergence.
   - Compare ROM-on vs ROM-off aerodynamic outputs from `t=0` to `t=2.53 s`.
   - Inspect `Cd`, `Cn`, and `Cm` deltas, not just total drag.
   - Determine whether the destabilizing contribution comes primarily from drag, normal force, or moment correction.

2. Explain the rapid angle-of-attack growth.
   - The critical symptom is the rise to `39.8°` by `2.399 s`.
   - Determine whether ROM is shifting CP, over-amplifying side/normal force, or producing a destabilizing moment during low-Mach boost.

3. Verify whether ROM is creating instability or exposing marginal launch stability already present in the model.
   - Recheck full-stack CG/CP margin at launch.
   - Recheck mass and inertia values.
   - Recheck launch guide geometry, effective guide length, and rail-button/guide interaction.
   - Recheck whether the selected flight configuration has an off-rail stability margin that is already marginal without ROM.

4. Inspect launch-stability logic that interacts with the active aerodynamic calculator.
   - Compare launch/off-rail stability calculations using baseline aerodynamics vs the ROM-active path.
   - Confirm whether weathercocking or launch-stability compensation is using an aerodynamic calculator that becomes unstable only when ROM is enabled.

5. Validate staging after the boost-instability problem is fixed.
   - Once the booster reaches burnout, separation should occur immediately.
   - The sustainer should ignite at `burnout + 6 s`.
   - Confirm that this `6 s` delay is intentional and realistic, because even the ROM-disabled run still underpredicts apogee significantly.

6. Treat the remaining apogee gap as a second-phase problem.
   - After fixing the early ROM abort, investigate why the completed baseline-style flight is still only about `58013 ft` instead of `104659 ft`.
   - That later gap is the actual high-Mach performance-tuning problem.

## Concrete Debugging Checks
1. Dump ROM-on and ROM-off time-aligned aerodynamic terms for the first `3 s`.
2. Log CP, CG, static margin, AoA, angular velocity, and thrust near `2.3 s` to `2.53 s`.
3. Temporarily force baseline aerodynamics for launch-stability evaluation while keeping ROM enabled later in flight, then see whether the abort disappears.
4. Temporarily scale ROM moment correction toward zero and rerun.
5. Temporarily scale ROM normal-force correction toward zero and rerun.
6. Temporarily scale ROM drag correction toward zero and rerun.
7. Increase low-Mach/high-AoA blending back toward baseline and check whether the tumble disappears without destroying later Mach `1-3` behavior.
8. After the ROM-enabled run survives boost, compare stage-separation and sustainer-burn behavior against the ROM-disabled baseline.

## Success Criteria
- ROM-enabled run no longer aborts under thrust.
- Booster burnout occurs.
- Stage separation occurs.
- Sustainer ignition occurs.
- ROM-enabled apogee rises well above `505 ft` and at least clears the current ROM-disabled baseline of about `58013 ft`.
- Any remaining delta to `104659 ft` is then treated as a second-phase supersonic tuning issue rather than an early-flight stability failure.
- No negative-pressure exception is reproduced in the validated headless path.

## Ready-To-Use Research Prompt
Use the following prompt for the next debugging/tuning pass:

> Investigate the ORK `AeroPac104KStageOne&Two-2_ROM.CDX1.ork` in the current OpenRocket codebase. Do not assume the primary failure is negative pressure. The current headless reproduction shows that with ROM enabled (`PATHLINE_STANDARD`, `PATHLINE_ACTIVE`) the flight aborts at `2.528 s` because the vehicle `began to tumble under thrust`, after a `39.8°` AoA warning at `2.399 s`, producing only about `504.6 ft` apogee. With ROM disabled on the same ORK, the booster burns out at `16.135 s`, stages separate correctly, the sustainer ignites at `22.135 s`, and the flight reaches about `58012.9 ft` and `Mach 2.439`. Your goal is to identify which ROM/pathline force or launch-stability interaction causes the early boost divergence, fix that first, and only then tune the remaining post-fix apogee gap versus the expected `104659 ft`. Focus on ROM-vs-baseline `Cd/Cn/Cm` deltas during the first `3 s`, CP/CG/static-margin behavior, launch-guide/off-rail stability, and whether low-Mach/high-AoA ROM corrections need stronger damping or blending to baseline.

## Source Artifacts
- Test harness: `core/src/test/java/info/openrocket/core/tuning/AboveMachOneAuditTest.java`
- XML report: `core/build/test-results/test/TEST-info.openrocket.core.tuning.AboveMachOneAuditTest.xml`
- ROM summary: `core/build/tmp/jackpot-rom-regression/openrocket-rom-logs/Simulation_1_20260503_173814_rom_summary.log`
- ROM trace: `core/build/tmp/jackpot-rom-regression/openrocket-rom-logs/Simulation_1_20260503_173814_rom_trace.csv`
