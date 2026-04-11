# ROM Phase-Two Remediation Playbook

## Objective
Reduce phase-two CRITICAL failures by fixing ROM-vs-real-data mismatch sources in this order:
1. Telemetry harmonization across logger families (AB, EasyMini, Fluctus)
2. Drag force-balance channel mismatch (`velocityZ`, `accelZ`)
3. Vertical kinematics mismatch (`altitude`)
4. Atmosphere temperature mismatch (`temperature`)
5. Window coverage collapse (`full/coast/descent` score zeroing)

## Current Symptoms (From Latest Phase-Two Reports)
- CRITICAL severity for all datasets.
- Frequent equation flags:
  - `accelZ:rom.drag.force-balance:CRITICAL`
  - `velocityZ:rom.drag.force-balance:CRITICAL`
  - `altitude:rom.integrator.vertical-kinematics:CRITICAL`
  - `temperature:rom.atmosphere.temperature:CRITICAL`
- Multiple datasets still show `0.000` in full/coast/descent windows.
- Fluctus pairings show large CD proxy divergence versus AB/EasyMini pairings.

## Remediation Strategy

### Phase 1: Data Harmonization (Do First)
Goal: Ensure model tuning is not biased by schema/device inconsistencies.

Tasks:
1. Add schema-parity sanitization for AB and EasyMini to match Fluctus safety filtering.
2. Normalize sign conventions for vertical velocity and acceleration at parser boundary.
3. Add unit validation for every parsed channel (`m`, `m/s`, `m/s^2`, `C`).
4. Add sensor-specific outlier filtering for impossible spikes.
5. Emit per-dataset parser diagnostics (`droppedSamples`, `unitCorrections`, `outlierDrops`).

Acceptance criteria:
- No physically impossible means/peaks in `phase-two-quantities.csv`.
- Cross-logger pairings show comparable velocity/accel ranges after normalization.

### Phase 2: Alignment and Coverage Reliability
Goal: Prevent score collapse caused by weak overlap or misaligned windows.

Tasks:
1. Keep launch-anchor alignment as primary t0 strategy.
2. Add per-window coverage outputs:
   - `matchedSampleCount`
   - `timelineSampleCount`
   - `coverageRatio`
3. Enforce minimum window coverage thresholds before scoring.
4. Mark low-coverage windows as `INSUFFICIENT_DATA` (instead of silent zero-like collapse).
5. Add summary columns for `fullCoverage`, `coastCoverage`, `descentCoverage`.

Acceptance criteria:
- `0.000` scores appear only with explicit `INSUFFICIENT_DATA` reason.
- At least 80% of datasets have non-zero coast and descent windows with adequate coverage.

### Phase 3: ROM Dynamics Calibration (After Data Stability)
Goal: Correct physics mismatch after telemetry is trustworthy.

Tasks:
1. Calibrate drag force-balance terms driving `velocityZ` and `accelZ` errors.
2. Re-tune vertical integrator coupling for altitude trajectory stability.
3. Revisit transonic weighting and base/wave drag blending for logger-consistent behavior.
4. Add bounded sensitivity sweeps for key coefficients to avoid overfitting one launch family.
5. Re-evaluate CD proxy construction against measured channels with robust filtering.

Acceptance criteria:
- `accelZ/velocityZ` critical flag counts drop by at least 50%.
- `altitude` critical flags drop by at least 50%.
- Full-score median improves and CRITICAL dataset ratio drops below 25%.

### Phase 4: Atmosphere/Temperature Consistency
Goal: Remove persistent `rom.atmosphere.temperature` criticals.

Tasks:
1. Validate temperature unit and offset handling per logger format.
2. Apply smoothing/lag compensation for noisy thermal channels.
3. Separate thermal diagnostic weighting from primary trajectory channels when coverage is weak.
4. Add explicit thermal diagnostics to report (`tempOffsetMean`, `tempRMSE`, `tempCoverage`).

Acceptance criteria:
- Temperature critical flags reduced to less than or equal to 20% of datasets.

## Reporting Upgrades Required
Update report outputs to make failures actionable:

1. `phase-two-summary.csv`
- Add coverage fields per window.
- Add `failureReason` and `insufficientDataReason`.
- Include top 3 channel contributors by weighted error.

2. `phase-two-quantities.csv`
- Add parser diagnostics per source.
- Add normalized range statistics per channel.

3. `phase-two-junit.xml`
- Include structured details:
  - coverage metrics
  - top channel contributors
  - parser diagnostics summary

## Regression Tests to Add
1. Parser parity tests across AB/EasyMini/Fluctus for unit/sign normalization.
2. Launch-anchor and window coverage tests.
3. Near-zero denominator robustness tests for MAPE/SMAPE.
4. Force-balance synthetic tests verifying monotonic response to drag coefficient changes.
5. Temperature channel normalization tests with known offsets.

## Execution Sequence
1. Implement Phase 1 and Phase 2 first.
2. Re-run reports and verify coverage/data quality gates.
3. Implement Phase 3 ROM calibration only after parser/alignment metrics are stable.
4. Implement Phase 4 temperature fixes and rebalance thermal scoring weights if needed.
5. Re-run full phase-two and compare against baseline.

## Validation Commands
From workspace root:

```powershell
.\gradlew.bat :core:phaseTwoGenerateReports -PphaseTwoConfig="src/test/java/info/openrocket/core/tuning/jsonFiles_tuning/total_testing.json" -PphaseTwoReportsDir="build/reports/phase-two-remediation"
```

Optional quick comparison command:

```powershell
$old = Import-Csv "core/build/reports/phase-two/phase-two-summary.csv"
$new = Import-Csv "core/build/reports/phase-two-remediation/phase-two-summary.csv"
foreach($n in $new){
  $o = $old | Where-Object { $_.dataset -eq $n.dataset } | Select-Object -First 1
  if($o){
    [PSCustomObject]@{
      dataset = $n.dataset
      oldScore = [double]$o.fullScore
      newScore = [double]$n.fullScore
      delta = [math]::Round(([double]$n.fullScore - [double]$o.fullScore), 3)
    }
  }
} | Sort-Object delta
```

## Done Criteria
This remediation is complete when all are true:
- Plugin success remains 100%.
- CRITICAL dataset ratio is less than or equal to 25%.
- Zero-score windows are explained by explicit insufficient-data reasons.
- Force-balance and altitude critical flags are reduced by at least 50%.
- Temperature critical flags are reduced to less than or equal to 20%.
