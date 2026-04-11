# ROM Rigorous Testing Plan

## Purpose
Create a repeatable, strict validation loop for ROM development so model changes are measured against both physics-oriented tests and real telemetry comparison outcomes.

## Accuracy Baseline (Current)
From `core/build/reports/phase-two-rigorous/phase-two-summary.csv`:
- Datasets: 12
- CRITICAL full severity: 12
- Zero full scores: 6
- Zero descent scores: 6
- Dominant contributors: `accelZ`, `temperature`, `velocityZ`, `altitude`

## Test Pyramid

### Level 1: Fast Unit/Contract Tests (always run)
1. `PhaseTwoScoreCalculatorTest`
2. `PhaseTwoScoreCalculatorCoverageTest`
3. `TelemetryComparatorSmokeTest`
4. `TelemetryParsersDiagnosticsTest`
5. `PhaseTwoBatchReportWriterPolicyTest`

Goal:
- Catch scoring/reason/coverage regressions in seconds.

### Level 2: ROM Physics Regression Suite (per commit)
1. `RomAerodynamicCalculatorTest`
2. `RomValidationPipelineTest`
3. `RomTrajectorySensitivityTest`
4. `AeroGridEvaluator4DTest`

Goal:
- Preserve monotonicity, boundedness, and transonic behavior while tuning coefficients.

### Level 3: Full Telemetry Accuracy Run (daily or before merge)
Run phase-two reports on `total_testing.json` and compare against previous baseline.

Goal:
- Track real-world ROM fit quality and avoid hidden regressions.

## Commands

### Fast tuning pipeline checks
```powershell
.\gradlew.bat :core:test --tests "info.openrocket.core.tuning.PhaseTwoScoreCalculatorTest" --tests "info.openrocket.core.tuning.PhaseTwoScoreCalculatorCoverageTest" --tests "info.openrocket.core.tuning.TelemetryComparatorSmokeTest" --tests "info.openrocket.core.tuning.TelemetryParsersDiagnosticsTest" --tests "info.openrocket.core.tuning.PhaseTwoBatchReportWriterPolicyTest"
```

### ROM physics suite
```powershell
.\gradlew.bat :core:test --tests "info.openrocket.core.aerodynamics.RomAerodynamicCalculatorTest" --tests "info.openrocket.core.aerodynamics.rom.RomValidationPipelineTest" --tests "info.openrocket.core.aerodynamics.rom.RomTrajectorySensitivityTest" --tests "info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4DTest"
```

### Full phase-two run
```powershell
.\gradlew.bat :core:phaseTwoGenerateReports -PphaseTwoConfig="src/test/java/info/openrocket/core/tuning/jsonFiles_tuning/total_testing.json" -PphaseTwoReportsDir="build/reports/phase-two-rigorous"
```

### Compare baseline vs current
```powershell
$old = Import-Csv "core/build/reports/phase-two-improved/phase-two-summary.csv"
$new = Import-Csv "core/build/reports/phase-two-rigorous/phase-two-summary.csv"
foreach($n in $new){
  $o = $old | Where-Object { $_.dataset -eq $n.dataset } | Select-Object -First 1
  if($o){
    [PSCustomObject]@{
      dataset = $n.dataset
      oldScore = [double]$o.fullScore
      newScore = [double]$n.fullScore
      delta = [math]::Round(([double]$n.fullScore - [double]$o.fullScore), 3)
      oldSeverity = $o.fullSeverity
      newSeverity = $n.fullSeverity
    }
  }
} | Sort-Object delta
```

## Release Gates
A change is merge-ready only when all are true:
1. Level 1 and Level 2 tests pass.
2. Plugin success remains 100% for phase-two run.
3. No increase in CRITICAL dataset count.
4. No increase in zero full/descent score count unless marked with explicit `insufficientDataReason`.
5. Median full score improves or remains stable with documented rationale.

## Recommended Near-Term Milestones
1. Reduce `accelZ` contributor dominance first (force-balance calibration).
2. Reduce `temperature` critical contribution by validating logger-unit normalization and filtering.
3. Address `altitude` and `velocityZ` on datasets with full score `0.000` by tuning vertical integrator coupling and drag transition behavior.
