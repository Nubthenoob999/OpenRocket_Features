# ROM Phase-Two Prioritized Tuning Guide

## Purpose
Turn the latest phase-two report outputs into a practical tuning order so ROM work focuses on the highest-value failures first and avoids tuning against corrupted or incomplete reference data.

Source reports used:
- `core/build/reports/phase-two/phase-two-summary.csv`
- `core/build/reports/phase-two/phase-two-improvements.csv`
- `core/build/reports/phase-two/phase-two-quantities.csv`
- `core/build/reports/phase-two/phase-two-junit.xml`

Run metadata from the current baseline:
- datasets executed: 8
- junit failures: 6
- plugin success: 100%
- interpolation mode: `CUBIC_HERMITE`
- sample rate: `20 Hz`

## Current Read of the Results

### What is working reasonably well
- The phase-two pipeline itself is running end-to-end.
- Plugin execution is succeeding for every dataset.
- Coverage is usually `1.000`, so most pairings are aligned and scored.
- Atmospheric channels are not the main blocker right now:
  - `temperature` is usually OK
  - `pressure` is usually OK
  - `density` is usually OK

### What is failing repeatedly
- `rom.drag.force-balance`
  - dominant channels: `accelZ`, `velocityZ`
  - this is the most frequent CRITICAL equation group
- `rom.integrator.vertical-kinematics`
  - dominant channels: `altitude`, `apogee`
  - this is the second major ROM failure group
- `data-quality.cross-sensor-consistency`
  - `cdProxy` mismatch is persistent
  - this is often a warning, but it signals that drag-related tuning may be contaminated by logger inconsistency

## Ranked Fix Order

### Priority 0: Do not tune against known-bad reference/candidate pairs
These datasets currently carry strong data-quality contamination and should not drive coefficient tuning until fixed or isolated:

1. `jackpot_launch_1_fluctus_vs_openrocket`
- reference high sentinel rate: `66.6%`
- reference `cdProxy`: `4.298`
- treat as parser/reference cleanup, not ROM calibration evidence

2. `government_work_launch_1_easymini_vs_openrocket`
3. `government_work_launch_1_fluctus_vs_openrocket`
4. `government_work_launch_2_easymini_vs_openrocket`
5. `government_work_launch_2_fluctus_vs_openrocket`
- candidate source flagged as `ORK_FALLBACK_TO_CSV`
- candidate parser schema is `AB_IMU_INTERLEAVED`
- candidate apogee times in `phase-two-quantities.csv` are clearly non-physical:
  - `619.665 s`
  - `994.617 s`
- `government_work_launch_2_*` is explicitly truncated at burnout, so coast/descent are unavailable

Action:
- exclude these pairs from ROM coefficient fitting
- keep them in validation only after parser and schema fixes are complete

### Priority 1: Fix `rom.drag.force-balance`
This is the highest-value ROM tuning target.

Why first:
- it dominates `accelZ` and `velocityZ`
- it is CRITICAL in nearly every meaningful dataset
- it likely drives part of the later altitude/apogee error through bad vertical acceleration and drag response

Strong evidence:
- `jackpot_launch_1_easymini_vs_openrocket`
  - `accelZ 48.659` CRITICAL
  - `velocityZ 66.961` WARNING
- `jackpot_launch_2_easymini_vs_openrocket`
  - `velocityZ 6.379` CRITICAL
  - `accelZ 53.855` CRITICAL
- `government_work_launch_1_easymini_vs_openrocket`
  - `accelZ 0.000` CRITICAL
  - `velocityZ 12.842` CRITICAL

Likely ROM-side causes to inspect:
- effective Cd magnitude
- reference area usage
- dynamic-pressure coupling
- plume-on / plume-off transition handling
- transonic blending and drag-rise placement
- force projection from drag model into vertical acceleration

What to tune first inside this bucket:
1. verify the drag-force equation chain from ROM Cd to acceleration
2. compare predicted Cd proxy against measured trend direction, not just magnitude
3. inspect whether Cd is consistently too high on the candidate side
4. validate the active drag mode across boost/coast/descent

Success target for this phase:
- reduce CRITICAL frequency for `accelZ` and `velocityZ` by at least 50%
- improve `jackpot_launch_1_easymini_vs_openrocket` and `jackpot_launch_2_easymini_vs_openrocket` first, since they have full coverage and cleaner references

### Priority 2: Fix `rom.integrator.vertical-kinematics`
Do this immediately after Priority 1, not before.

Why second:
- altitude and apogee are repeatedly bad
- but some of that error is probably downstream of force-balance mismatch
- tuning the integrator before drag is stabilized risks compensating for the wrong root cause

Strong evidence:
- `jackpot_launch_1_easymini_vs_openrocket`
  - `altitude 58.862` CRITICAL
  - apogee error `68.293 m` / `8.700%`
- `jackpot_launch_2_easymini_vs_openrocket`
  - `altitude 40.398` CRITICAL
  - apogee error `332.042 m` / `28.846%`
- `jackpot_launch_2_fluctus_vs_openrocket`
  - `altitude 0.000` CRITICAL
  - apogee error `349.020 m` / `30.914%`

Likely ROM-side causes to inspect:
- acceleration-to-velocity integration stability
- velocity-to-altitude propagation
- event timing around burnout/apogee
- phase transition handling between boost/coast/descent
- any damping or clipping introduced in vertical state propagation

Success target for this phase:
- reduce apogee percent error below `5%` on the cleaner jackpot/easymini pairs
- eliminate `0.000` altitude channel scores on datasets with valid full coverage

### Priority 3: Stabilize `cdProxy` interpretation
This is not the first knob to tune, but it should be tracked in parallel as a diagnostic.

Why third:
- the report keeps surfacing `data-quality.cross-sensor-consistency`
- the candidate Cd proxy is often much higher than reference:
  - jackpot 1 easymini: `0.140` vs `0.361`
  - jackpot 2 easymini: `0.050` vs `0.283`
- this may reflect both ROM drag error and logger/sensor inconsistency

Action:
- use `cdProxy` as a guardrail, not the primary optimization objective
- only trust it on datasets without sentinel-rate or schema-fallback warnings
- compare trend shape across phases, not only single full-flight means

### Priority 4: Leave atmosphere channels alone for now
Atmospheric channels are currently not the limiting factor.

Reason:
- `temperature`, `pressure`, and `density` are frequently scoring in the `90+` range
- they are not dominating the JUnit failures in this report set

Action:
- do not spend ROM tuning cycles here until drag-force and vertical kinematics are materially improved

## Recommended Dataset Order for Tuning

### Use first for ROM tuning
1. `jackpot_launch_1_easymini_vs_openrocket`
2. `jackpot_launch_2_easymini_vs_openrocket`

Reason:
- full coverage
- cleaner reference logger
- failures are concentrated in the ROM dynamics channels you actually want to tune

### Use second for cross-checking
3. `jackpot_launch_2_fluctus_vs_openrocket`

Reason:
- useful to test logger-family robustness after the ROM starts improving
- not ideal as the primary fit target because some scores are already collapsing to zero

### Hold out until parser/data cleanup
4. `jackpot_launch_1_fluctus_vs_openrocket`
5. all `government_work_*`

## Practical Tuning Loop

1. Keep the current phase-two baseline reports unchanged.
2. Tune only one ROM bucket at a time:
- first `rom.drag.force-balance`
- then `rom.integrator.vertical-kinematics`
3. After each ROM change, rerun:

```powershell
.\gradlew.bat :core:phaseTwoGenerateReports -PphaseTwoConfig="src/test/java/info/openrocket/core/tuning/jsonFiles_tuning/total_testing.json" -PphaseTwoReportsDir="build/reports/phase-two-next"
```

4. Judge progress using:
- `fullScore`
- `accelZ`
- `velocityZ`
- `altitude`
- `apogeeErrorPercent`
- whether any `0.000` channel scores disappear on valid datasets

5. Reject changes that improve one logger family only by making the cleaner jackpot/easymini pairs worse.

## Immediate Next Actions
1. Freeze parser/data cleanup as a separate lane from ROM coefficient tuning.
2. Use `jackpot_launch_1_easymini_vs_openrocket` as the first calibration target.
3. Focus first code review/debugging on the full drag-to-vertical-acceleration path.
4. After drag-force improvements, retune vertical propagation and apogee behavior.
5. Reintroduce `government_work_*` only after the `AB_IMU_INTERLEAVED` fallback/truncation issue is fixed.

## Done Criteria for This Tuning Pass
- `rom.drag.force-balance` is no longer the dominant CRITICAL group on the jackpot/easymini datasets.
- `rom.integrator.vertical-kinematics` no longer produces large apogee overshoot on jackpot/easymini.
- `0.000` full/coast/descent channel scores are limited to datasets with explicit insufficient-data reasons.
- atmosphere channels remain stable while drag and vertical dynamics improve.
