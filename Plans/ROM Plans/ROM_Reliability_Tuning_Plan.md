# ROM Reliability Tuning Plan

## Goal
Improve phase-two ROM reliability so scores represent true model quality rather than parser/alignment artifacts.

## Success Criteria
- Plugin success remains 100%.
- Critical dataset count drops below 25% for `total_testing.json`.
- `descentScore == 0.000` is rare and only appears when data is truly missing.
- JUnit failures distinguish data quality/alignment failures from ROM equation failures.

## Batch 1 (Implemented)
1. Stabilize launch time alignment.
- Added `estimateLaunchAnchorIndex()` in `TelemetrySeries` using sustained altitude/velocity/acceleration launch cues.
- Replaced comparator and window-estimation T0 usage with launch-anchor index.
- Changed altitude-only fallback behavior to index `0` instead of end-of-series.

2. Reduce near-zero denominator blowups in channel metrics.
- Added adaptive denominator floor in `TelemetryComparator` using channel range.
- MAPE/SMAPE now avoid exploding when reference values are close to zero.

3. Add channel coverage awareness.
- `ChannelMetrics` now stores `coverageRatio`.
- `PhaseTwoScoreCalculator` skips channels with low coverage (`< 0.40`) to reduce unstable score influence.

## Batch 2 (Next)
1. Add parser sanitization parity across all schemas.
- Extend non-physical value filtering used in Fluctus to AB and EasyMini channels.
- Add schema-level unit/sign normalization checks and configurable overrides.

2. Add reliability annotations in outputs.
- Write coverage/alignment quality indicators into summary and junit outputs.
- Include top low-coverage channels in failure details.

3. Add regression tests for reliability behavior.
- Tests for launch-anchor detection.
- Tests for MAPE/SMAPE robustness near zero references.
- Tests for low-coverage channel suppression.

## Batch 3 (ROM equation tuning)
1. Tune equation groups after reliability stabilization.
- Prioritize `rom.drag.force-balance` and `rom.integrator.vertical-kinematics`.
- Use improved metrics to tune by launch family and compare deltas.

## Validation Command
From workspace root:

```powershell
.\gradlew.bat :core:phaseTwoGenerateReports -PphaseTwoConfig="src/test/java/info/openrocket/core/tuning/jsonFiles_tuning/total_testing.json" -PphaseTwoReportsDir="build/reports/phase-two-improved"
```
