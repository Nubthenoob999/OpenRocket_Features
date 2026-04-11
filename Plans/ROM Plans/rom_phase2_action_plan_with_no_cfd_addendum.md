# ROM Phase 2 — Scoring Analysis & Action Plan
## Run timestamp: 2026-03-27T18:18:34Z

---

## Executive summary

12 comparison datasets were scored across 3 rockets (Jackpot launches 1 & 2,
Government Work launches 1 & 2) and 3 sensors (AB airbrake, EasyMini altimeter,
Fluctus altimeter). Every single dataset is CRITICAL severity. No dataset scored
above 54. Four datasets scored exactly 0. The failures cluster into four independent
root causes, ranked by frequency and impact below.

| Root cause | Datasets affected | Max score impact |
|---|---|---|
| `rom.atmosphere.temperature` model wrong | 8/12 | Always scores 0.000 |
| Data truncation (no coast/descent) | 4/12 | Forces score to 0 |
| Fluctus sensor data corruption (Jackpot L1) | 2/12 | Score floors at 0 |
| Drag force balance (accelZ/velocityZ) | 11/12 | Scores stuck 0–53 |

Fixing issues 1 and 2 is prerequisite to being able to evaluate issue 4 clearly.
Issue 3 is a data quality guard, not a ROM fix.

---

## Issue 1 — Temperature model: 0.000 contribution in every flagged run

**Flag:** `temperature:rom.atmosphere.temperature:CRITICAL`
**Affected datasets:** 8/12 (all Jackpot pairs, GW L1 easymini vs fluctus,
GW L2 easymini vs fluctus)
**Score contribution from temperature:** `0.000` in 100% of flagged cases

### What the data shows

The `temperature` channel scores exactly 0.000 in every comparison where it
appears as a top contributor. This is not a marginal miss — it is a total failure.
The `rom.atmosphere.temperature` flag tells us the ROM's atmosphere model is
producing temperatures that do not match what any of the three sensor types record,
on any of the four launches, at any flight phase.

The ROM currently uses ISA (International Standard Atmosphere), which assumes a
standard temperature lapse rate of −6.5 K/km from a sea-level baseline of 288.15 K.
Real launch conditions deviate from ISA in two ways that compound:

1. **Ground-level temperature offset**: If the launch site is warmer or cooler than
   ISA's 288.15 K (15°C), every altitude slice is offset by that delta. A 10°C warm
   day shifts the entire temperature profile by +10 K throughout the flight.

2. **Non-standard lapse rate**: The real atmosphere on a given day may have an
   inversion layer, a dry adiabatic region, or a tropopause at a different altitude
   than ISA's 11 km.

Both sensors that report temperature (Fluctus and the AB airbrake) would catch this
immediately. The EasyMini does not report temperature, which is why EasyMini-only
comparisons do not show this flag.

### Action items

**1a. Add measured ground-level temperature as a required input field.**

The ROM atmosphere model must be initialized with the actual measured temperature
at the pad. This is the single most impactful change available. Add a field to
`RomGeometryInput` (or its successor) and to the comparison pipeline:

```java
// In RomAtmosphereModel or equivalent:
public static double temperatureAtAltitude(double altMeters,
                                            double groundTempKelvin,
                                            double groundPressurePa) {
    // Use measured ground T instead of ISA 288.15 K
    double lapseRate = 0.0065; // K/m, ISA standard lapse
    return groundTempKelvin - lapseRate * altMeters;
}
```

The ground temperature can be sourced from: (a) the sensor's first recorded
temperature reading at t=0, or (b) a manual input field in the tuning metadata.
Either is vastly better than ISA cold-start.

**1b. Replace the ISA fixed lapse rate with a two-point linear fit when telemetry is available.**

If the telemetry contains temperature at both low and high altitude (which Fluctus
and AB both provide), use a linear regression over the available T(h) pairs to
estimate the actual lapse rate for that flight. This handles inversions and dry
adiabatic regions that cause ISA to be wrong even after ground-level correction.

```java
// Fit T = a - b*h from telemetry samples where h > 200m (above turbulent boundary layer)
double[] h = ...; // altitude samples
double[] T = ...; // temperature samples at those altitudes
double[] ab = linearFit(h, T); // [intercept, slope]
// Store (ab[0], ab[1]) as the atmospheric profile for this comparison run
```

**1c. Add a temperature model quality gate to the scoring pipeline.**

Before running a full comparison, check whether the ROM atmosphere's predicted
temperature at apogee matches the reference sensor's apogee temperature to within
±10 K. If it does not, log a `TEMPERATURE_PROFILE_MISMATCH` warning and
automatically apply the ground-level offset correction from (1a).

---

## Issue 2 — Data truncation: missing coast and descent phases

**Affected datasets (complete phase loss):**
- `government_work_launch_1_ab_vs_easymini_custom`: coast=0/0, descent=0/0
- `government_work_launch_1_ab_vs_fluctus_custom`: coast=0/0, descent=0/0
- `government_work_launch_2_easymini_vs_government_work_custom`: descent=0/0
- `government_work_launch_2_fluctus_vs_government_work_custom`: descent=0/0

**Score impact:** Immediate floor to 0.000 (no coverage in those phases = no match possible)

### What the data shows

**Government Work Launch 1 — AB sensor:**
The AB sensor on GW L1 recorded 1419 samples, all in the boost phase. Coast and
descent coverage is exactly 0/0, not 0/1419 — the timeline itself has no coast or
descent nodes. This means the AB recording was terminated at or very near motor
burnout. The `AB_IMU_INTERLEAVED` schema (used on GW launches) recorded continuously
through boost but the file ends there.

**Government Work Launch 2 — government_work sensor:**
200 matched samples, coast phase present (164 samples), descent = 0/0.
The recording ended after coast phase — before or at apogee/ejection charge.
This `AB_IMU_INTERLEAVED` file appears to stop at deployment deployment event.

Both cases are specific to the `AB_IMU_INTERLEAVED` schema, not seen in `AB_EXTENDED`
(which has complete coverage on all Jackpot runs).

### Action items

**2a. Diagnose `AB_IMU_INTERLEAVED` recording termination.**

The `AB_EXTENDED` schema consistently records complete flights (boost + coast +
descent). The `AB_IMU_INTERLEAVED` schema does not. Investigate whether:
- The AB firmware in IMU-interleaved mode stops recording on motor burnout detection
- The IMU-interleaved mode runs out of onboard buffer before descent
- The file export truncates at a fixed byte count or time limit

If the issue is firmware-side, flag `AB_IMU_INTERLEAVED` datasets as
`TRUNCATION_RISK` in the parser and require manual verification of coast/descent
coverage before any comparison that weights those phases.

**2b. Add a pre-comparison coverage check with actionable failure reasons.**

The `insufficientDataReason` field is empty for all these datasets despite the
zero coverage. It should be populated. Add a coverage gate that, when any phase
has 0 timeline samples, writes a reason like:

```
insufficientDataReason = "AB_IMU_INTERLEAVED source truncated at burnout;
coast and descent phases unavailable — use AB_EXTENDED schema or substitute
with EasyMini/Fluctus as reference for coast/descent analysis"
```

This prevents the misleading `score=0.000` from being misread as a ROM physics
failure when it is purely a data availability issue.

**2c. For zero-coverage phases, exclude that phase weight from the overall score.**

Currently a score of 0 on coast or descent (due to no data) tanks the full score
even if boost score is excellent. The scoring formula should treat zero-coverage
phases as `EXCLUDED` rather than `FAILED` — they should not contribute weight to
the aggregate. Concretely:

```
fullScore = Σ(phase_weight_i * phase_score_i) / Σ(phase_weight_i)
// where the sum excludes phases with zero timeline samples
```

Under this fix, `government_work_launch_1_ab_vs_easymini_custom` would report
`boostScore=0.000` (because accelZ/velocityZ/altitude are wrong on boost) rather than
the misleading `fullScore=0.000` (which conflates data truncation with ROM failure).

---

## Issue 3 — Fluctus sensor corruption on Jackpot Launch 1

**Affected datasets:**
- `jackpot_launch_1_ab_vs_fluctus_custom`: fullScore=0.000, cdProxy=4.298
- `jackpot_launch_1_easymini_vs_fluctus_custom`: fullScore=0.000, cdProxy=4.298

**Key indicators:**
- 45,126 sentinel drops out of 22,597 accepted rows — a 2:1 bad-to-good ratio
- `cdProxyMean=4.298` — physically impossible for any subsonic rocket (theoretical max
  Cd for a flat plate normal to flow is ~1.2; 4.3 implies the sensor is reading
  large negative accelerations or corrupted drag inversion)
- Jackpot Launch 2 Fluctus has 0 sentinel drops and cdProxy=0.693 on the same rocket
  — confirming the hardware itself was functional, but something was wrong on L1

This is not a ROM issue. The Fluctus sensor produced corrupted data on Jackpot
Launch 1. However, the pipeline still accepted it and scored it at CRITICAL severity
without flagging the source data as suspect.

### Action items

**3a. Add a sentinel-drop rate gate to the parser.**

If `sentinelDrops / (rowsAccepted + sentinelDrops) > 0.30` (30%), the dataset
should be flagged `DATA_QUALITY: SUSPECT` in the output and excluded from
the scoring comparison with an explanation. For reference, Jackpot L1 Fluctus
had a 67% sentinel rate — far beyond any acceptable threshold.

```java
double sentinelRate = (double) sentinelDrops / (rowsAccepted + sentinelDrops);
if (sentinelRate > SENTINEL_RATE_THRESHOLD) {
    flags.add("DATA_QUALITY:HIGH_SENTINEL_RATE:" + String.format("%.1f%%", 100*sentinelRate));
    status = PluginStatus.DATA_QUALITY_WARNING;
}
```

**3b. Add a cdProxy sanity check.**

Add a pre-scoring check: if `cdProxyMean < 0.01` or `cdProxyMean > 2.5`, flag the
source as `PHYSICALLY_SUSPECT`. The acceptable range for any rocket in flight (from
subsonic drag ≈ 0.2 to hypersonic with large fins ≈ 1.5) should be well within this
band. A value of 4.3 is unambiguously corrupted data.

```java
if (cdProxy < CD_PROXY_MIN || cdProxy > CD_PROXY_MAX) {
    flags.add("PHYSICS:CD_PROXY_OUT_OF_RANGE:" + cdProxy);
}
```

**3c. When a source dataset is flagged as suspect, report this in failureReason.**

The current pipeline reports `critical-full-score-0.000` as the failure reason for
these runs. It should instead report:
`"candidate-data-quality: FLUCTUS cdProxy=4.298 (max physical=2.5), sentinel-rate=67%"`.

---

## Issue 4 — Drag force balance: accelZ and velocityZ

**Flag:** `accelZ:rom.drag.force-balance:CRITICAL` and/or `velocityZ:rom.drag.force-balance:CRITICAL`
**Affected datasets:** 11/12 (everything except jackpot_launch_2_ab_vs_fluctus has
this flag in some form)

This is the core ROM physics issue. Even in the best-performing dataset
(`jackpot_launch_1_ab_vs_easymini`, fullScore=53.172), the `accelZ` contributor
scores only 2.276/100 and `velocityZ` scores 69.218/100. The drag force balance
is consistently wrong on acceleration but partially recovers on velocity.

### What the data shows

**Pattern 1: accelZ is harder to match than velocityZ.**

Across all non-zero-score datasets, accelZ scores are always much lower than
velocityZ scores. This is expected: accelZ = d(velocity)/dt so it's the derivative
of velocity. A drag model that's wrong in magnitude but right in shape will score
poorly on accelZ but acceptably on velocityZ because velocity is the integral (errors
average out over time).

This means the ROM's drag *magnitude* is off, not just its *timing*. The drag force
at any given (M, Re, α) point is wrong, not just the transonic peak location.

**Pattern 2: cdProxy discrepancy between sensors on the same launch.**

| Launch | Sensor | cdProxy | Ratio to EasyMini |
|---|---|---|---|
| Jackpot L1 | AB_EXTENDED | 0.372 | 2.66× |
| Jackpot L1 | EasyMini | 0.140 | 1.00× |
| Jackpot L2 | AB_EXTENDED | 0.058 | 1.16× |
| Jackpot L2 | EasyMini | 0.050 | 1.00× |
| GW L1 | AB_IMU_INTERLEAVED | 1.178 | 8.12× |
| GW L1 | EasyMini | 0.145 | 1.00× |
| GW L2 | EasyMini | 0.051 | 1.00× |
| GW L2 | GW (AB_IMU_INTERLEAVED) | 0.137 | 2.69× |

The EasyMini is the most consistent sensor across launches (0.050–0.145). EasyMini
measures altitude via barometric pressure and derives velocity and acceleration by
differentiation — it sees exactly what the ROM integrator needs to predict. It is the
ground truth.

The `AB_IMU_INTERLEAVED` schema produces cdProxy values that are dramatically
inflated: 1.178 for GW L1 vs 0.145 for EasyMini on the same flight. This is an
8× discrepancy, which strongly suggests the unit correction pipeline is not being
applied to `AB_IMU_INTERLEAVED` data — note that `AB_EXTENDED` applied 19,848 and
38,775 unit corrections respectively, while `AB_IMU_INTERLEAVED` applied exactly 0
on the GW L1 reference data.

The Fluctus cdProxy is consistently higher than EasyMini on the same launch (2–14×)
but not absurdly so on GW launches. On Jackpot L1 it's corrupted as discussed in
Issue 3.

### Action items

**4a. Investigate missing unit corrections on AB_IMU_INTERLEAVED.**

The `AB_EXTENDED` schema applied 3 unit corrections per row (consistent with
converting raw IMU counts to SI units for accelerometer X, Y, Z). The
`AB_IMU_INTERLEAVED` schema applied 0. Either:

- The `AB_IMU_INTERLEAVED` parser is reading pre-converted values (SI units already
  in file), in which case the cdProxy inflation comes from a different source, OR
- The parser is reading raw counts but not applying the scale factors, which would
  cause an 8–10× magnitude error on all force quantities exactly as observed.

Audit the `AB_IMU_INTERLEAVED` parser: confirm whether the accelerometer values in
the raw CSV are in m/s², g-units, or raw LSB counts. Apply the correct scale and
bias corrections to match the AB_EXTENDED pipeline.

**4b. Add a cross-sensor cdProxy consistency check before scoring.**

Before running the full comparison, compute the ratio `max(cdProxy) / min(cdProxy)`
across all sensors for the same launch. If this ratio exceeds 3.0, log a warning
that at least one sensor has an anomalous drag proxy and the comparison results
should be interpreted with caution. This would have immediately flagged the GW L1
AB data (ratio = 1.178/0.145 = 8.1) and the Jackpot L1 Fluctus data (4.298/0.140 = 30.7).

**4c. Separate the ROM drag physics fix into two sub-issues.**

Once the unit correction issue (4a) is resolved, the remaining accelZ error will
reflect genuine ROM drag model errors. Based on the patterns visible now:

**Sub-issue 4c-i: Descent drag is consistently the worst phase.**

Across all non-zero datasets, descentScore is always the lowest phase score
(range: 0–58, vs boostScore range 0–84 and coastScore range 0–80).
During descent, the rocket is tumbling or flying at high angle of attack under
drogue/main parachute — the `SideslipModel` and high-AoA `InducedDragModel` are
most critical during this phase. The improvements to `NormalForceModel.CN` (Phase 1
Task 2 supersonic fix) and `SideslipModel.cdBodyCrossflow` (Phase 1 Task 8 Mach
correction) directly address descent. Verify those were applied and are active.

**Sub-issue 4c-ii: Boost score variability suggests motor thrust integration error.**

Boost scores range from 0 (GW L1 AB — but that's a data issue) to 83.6
(jackpot_launch_2_ab_vs_fluctus boost). The wide range across launches using the
same ROM suggests the motor thrust curve or propellant mass flow is not consistently
accurate. During boost, drag is small relative to thrust, so accelZ errors in boost
are dominated by thrust, not Cd. Add a check: if boostScore < 30 but the launch has
complete coverage, the motor model is more likely wrong than the drag model.

**Sub-issue 4c-iii: Coast scores around 40–65 suggest Re-dependent errors.**

All non-zero coast scores cluster in the 37–80 range but never reach the 90s. This
is the phase where pure aerodynamic drag determines deceleration with no motor thrust.
Coast phase velocity spans from peak velocity (post-burnout) down to near-apogee
near-zero velocity — a wide Reynolds number range. The `SkinFrictionModel` transition
from laminar to turbulent (governed by `re_tr=5e5` in `cfIncompressible`) is the
most sensitive parameter in this range. Review whether the transition Reynolds number
is appropriate for the actual rocket surface finish and body diameter, and consider
exposing it as a tunable parameter in the comparison pipeline.

---

## Issue 5 — AB_IMU_INTERLEAVED schema: structural differences from AB_EXTENDED

This is a cross-cutting issue affecting Issues 2 and 4. The two AB schemas behave
very differently:

| Property | AB_EXTENDED | AB_IMU_INTERLEAVED |
|---|---|---|
| Unit corrections per row | 3 | 0 |
| Coverage completeness | Full flight | Boost-only or boost+coast |
| cdProxy (typical) | 0.06–0.37 | 0.14–1.18 |
| Launch | Jackpot | Government Work |

The most likely explanation is that `AB_IMU_INTERLEAVED` stores data from a
different firmware mode where the IMU data is interleaved with the airbrake servo
telemetry in a single stream, versus `AB_EXTENDED` which is a dedicated
post-processed export. If so, they require different parsing, different unit
conversions, and different phase-detection logic.

### Action items

**5a. Write a dedicated parser test that validates AB_IMU_INTERLEAVED output.**

Create a unit test using the GW L1 AB data that asserts:
- Peak velocity derived from accelerometer integration matches EasyMini peak
  velocity within 15% (GW L1: EasyMini peak=89.7 m/s)
- First 10 accelZ values are in the range [-50, +300] m/s² (physical for boost)
- cdProxyMean is in [0.05, 1.5] (physical range)

If any of these fail, the AB_IMU_INTERLEAVED parser needs a unit-conversion audit.

**5b. Document which AB firmware version produces each schema.**

Add a `schemaVersion` field to parser metadata and include it in the comparison
report header so failures can be attributed to firmware version rather than
blamed on the ROM.

---

## Priority order and estimated effort

| Priority | Issue | Task | Effort | Expected score gain |
|---|---|---|---|---|
| 1 | Issue 1 | Use measured ground-temp in atmosphere model (1a) | Small | +10–30 pts on all Jackpot/GW runs |
| 2 | Issue 2 | Exclude zero-coverage phases from aggregate score (2c) | Small | Uncovers real GW L1 boost failure |
| 3 | Issue 4 | Audit AB_IMU_INTERLEAVED unit corrections (4a) | Medium | Uncovers real GW drag errors |
| 4 | Issue 3 | Add sentinel-rate gate and cdProxy sanity check (3a, 3b) | Small | Correctly rejects Jackpot L1 Fluctus |
| 5 | Issue 2 | Populate `insufficientDataReason` for truncated AB data (2b) | Small | Diagnostic only |
| 6 | Issue 4 | Coast phase Re-transition tuning (4c-iii) | Medium | +5–15 pts on coast scores |
| 7 | Issue 4 | Descent high-AoA drag verification (4c-i) | Medium | +5–20 pts on descent scores |
| 8 | Issue 5 | Parser test suite for AB_IMU_INTERLEAVED (5a) | Medium | Prevents regression |
| 9 | Issue 1 | Two-point lapse-rate fit from telemetry (1b) | Medium | +5–10 pts over (1a) alone |
| 10 | Issue 1 | Temperature model pre-check gate (1c) | Small | Diagnostic only |

---

## Baseline scores to beat

After fixes 1–4 above are implemented, re-run phase 2 and check these baselines:

| Dataset | Current fullScore | Target (post-fix) |
|---|---|---|
| jackpot_launch_1_ab_vs_easymini | 53.2 | > 65 (temperature fix removes 0.000 contributor) |
| jackpot_launch_2_ab_vs_easymini | 41.6 | > 60 (same) |
| jackpot_launch_2_easymini_vs_fluctus | 41.6 | > 55 |
| government_work_launch_1_easymini_vs_fluctus | 37.3 | > 50 |
| government_work_launch_2_easymini_vs_fluctus | 40.7 | > 55 |
| jackpot_launch_1_ab_vs_fluctus | 0.0 | EXCLUDED (Fluctus L1 corrupted) |
| government_work_launch_1_ab_vs_easymini | 0.0 | Boost-only score reported, target > 30 |
| government_work_launch_2_*_vs_government_work | 0.0 | Boost+coast score reported, investigate accelZ |

Any dataset that remains below 40 after the temperature fix has a genuine ROM
drag physics problem that warrants deeper investigation of that specific flight's
sensor data.

---

## Open questions requiring clarification

1. **Why does Jackpot L2 have so many more samples than L1?**
   L2 AB: 12,925 rows vs L1 AB: 6,616 rows. Was L2 a longer flight, or was
   the logging rate different? The sample counts affect alignment quality.

2. **What is the actual rocket geometry for Government Work?**
   The AB on GW L1 was in `AB_IMU_INTERLEAVED` mode — does this rocket have the
   airbrakes deployed at all, or is it a passive flight? The cdProxy of 1.178 for
   boost-only data is suspicious.

3. **Is the `referenceCdProxy` field derived from the telemetry or from the ROM prediction?**
   If it is from telemetry, the discrepancy between sensors is a data quality issue.
   If it is from the ROM, it explains why comparisons with the ROM as reference
   all score poorly on the drag channels.

4. **What altitude (MSL) are the launch sites?**
   ISA assumes sea-level = 0 m. If launches happen at elevation (say, a desert site
   at 1500 m MSL), the ISA pressure and density at "h=0" are already wrong, which
   compounds the temperature error into density errors that feed directly into drag.


---

## Addendum — ROM must remain usable without CFD data

The ROM being developed in this plan should **not** require CFD data to be useful, valid, or runnable. CFD data may be used later as a refinement, validation, or calibration source, but it must remain optional. The baseline ROM must be able to initialize itself, generate its own lookup tables, and produce meaningful predictions from equations alone.

### Required interpretation for this project

The ROM should be treated as an **equation-driven reduced-order model** first, and only secondarily as a CFD-informed model. In practice, this means:

- The ROM must be able to **precompute its coefficient tables internally** from analytical and semi-empirical equations.
- The ROM must be able to run in a fully functional mode when **no CFD exports, CFD meshes, or CFD-derived lookup tables** are present.
- Any CFD data that is later introduced should be treated as an **optional tuning dataset**, not as a required dependency for model operation.
- The default build, test, and validation flow should succeed using equations, geometry, atmospheric inputs, and flight conditions alone.

### Equation-based table generation requirement

The table-generation pipeline should be designed so that it can populate all major aerodynamic and atmospheric tables from equations such as:

- standard atmosphere / measured-at-pad atmosphere models,
- compressibility and transonic corrections,
- drag buildup from body, fin, and protuberance components,
- lift and normal-force approximations,
- Reynolds-number and Mach-dependent skin-friction relations,
- angle-of-attack and sideslip correction terms,
- actuator or airbrake geometry relationships.

If CFD data exists, it may be used to compare against or improve these equation-based tables, but the tables themselves must still be generated without CFD.

### Implementation language to keep in the project plan

Add the following requirement to the ROM architecture and acceptance criteria:

> The ROM shall not depend on CFD data to compute its baseline aerodynamic tables. It must be able to generate usable tables directly from equations and known vehicle geometry, with CFD treated as optional validation or refinement data only.

> The ROM shall remain functional in a no-CFD environment. A missing CFD dataset must not block initialization, table generation, simulation execution, or scoring.

> The ROM shall support a purely analytical fallback path that precomputes aerodynamic and atmospheric quantities from equations before any optional empirical corrections are applied.

### Practical consequences

This means the following should be true in the implementation:

- A new vehicle or launch configuration should be simulatable even if no CFD study has been run.
- Table generation should be repeatable from inputs that are already known at design time.
- The ROM should fail only if geometry or required flight inputs are missing, not because CFD files are absent.
- Any “CFD required” code path should be removed or downgraded to an optional enhancement path.
- Tests should explicitly verify that the ROM can bootstrap its tables from equations only.

### Suggested acceptance criteria

A Phase 2 ROM implementation is not complete unless all of the following are true:

1. The model can generate baseline aerodynamic tables from equations alone.
2. The model runs end-to-end with zero CFD assets present.
3. Optional CFD data can refine the model, but the absence of CFD does not disable the ROM.
4. The generated equation-based tables produce physically reasonable values across the expected Mach, Reynolds, and angle-of-attack ranges.
5. The scoring and comparison pipeline can evaluate the ROM even when the lookup-table source is purely analytical.

### Positioning statement for the project

The ROM should be viewed as a self-contained predictive model that is **not dependent on CFD to be useful**. CFD is valuable as a comparison and calibration resource, but the core ROM must stand on its own through equations, geometry, and flight data. That is the version of the model this Phase 2 plan should support.

