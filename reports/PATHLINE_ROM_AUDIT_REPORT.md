# Pathline ROM Deep Code-and-Physics Audit Report

**Date:** 2026-06-18  
**Scope:** `core/src/main/java/info/openrocket/core/aerodynamics/rom/` (~70 files, 12 sub-packages)  
**Auditor:** Automated deep audit (6-phase)  
**Constraint envelope:** Mach 0–5, transonic care M 0.8–1.2, semi-empirical base/plume, 6-DOF force/moment, fallback/confidence, physical interpretability, OpenRocket runtime practicality

---

## A. Executive Diagnosis

The Pathline ROM is an ambitious, architecturally sound semi-empirical aerodynamics framework. The pipeline (geometry → flow state → regime → BL march → integrate → derive forces → confidence → fallback blend) is well-structured, the tuning/validation infrastructure is excellent, and the benchmark suite is anchored to peer-reviewed NASA/NACA references.

**However, the ROM is not yet production-ready.** Three categories of problems block release:

1. **Critical stub code** — `RomCache.evaluateAtPoint()` returns hardcoded linear approximations (`CD = 0.2 + 0.05*mach`), meaning the entire pre-computed cache is filled with fake physics. Any code path hitting the cache gets nonsense.

2. **Missing 6-DOF loads** — The physics suite delivers 3-DOF capability (axial, normal, pitch). Side force $C_Y(\beta)$, yawing moment $C_n(\beta)$, and rolling moment $C_l$ models are **absent**. The `SideslipModel` produces only a scalar $\Delta C_D$, not a lateral force vector. `NormalForceModel` accepts `betaRad` but ignores it.

3. **Transonic inconsistency** — The outer-flow `RegimeBlender` uses LINEAR 2-way blending while `TransonicBlendingModel` uses SIGMOID 3-way blending for the same M 0.8–1.2 band. `FullOuterFlowDispatch` uses a QUINTIC blend. Three different transonic interpolations coexist, producing inconsistent edge-state vs. drag coefficients.

**The Pathline ROM direction is sound** and should be preserved. The issues are implementation gaps and magic-constant provenance, not architectural flaws. The tuning infrastructure (Phase II/III batch runners, NASA wind-tunnel corpus, scoring pipeline) is ready to validate once the physics bugs are fixed.

---

## B. Repository Map

### Package Structure

```
rom/
├── PathlineROMCalculator.java          ★ Main entry point (~600 lines)
├── RomAerodynamicCalculator.java       Compatibility wrapper (19 lines)
├── RomSettings.java                    13 settings fields
├── RomFallbackMode.java                Enum: BLEND/FORCE_ROM/BARROWMAN_ONLY
├── InducedDragModel.java               ★ DUPLICATE (legacy, uses RomGeometryParameters)
│
├── control/
│   └── FallbackBlender.java            Legacy/ROM blending
│
├── core/
│   ├── geometry/
│   │   ├── RomGeometryInput.java       DTO for geometry
│   │   └── RomGeometryParameters.java  ★ BUG: finWettedArea semantics
│   └── physics/
│       ├── BaseDragModel.java          ★ DUPLICATE, dead exponent code
│       ├── FinDragModel.java           Fin friction/wave/interference/induced
│       ├── InducedDragModel.java       ★ DUPLICATE (new, uses RomGeometryInput)
│       ├── NormalForceModel.java       ★ betaRad unused, no body Mach correction
│       ├── PitchingMomentModel.java    ★ Moments about nose, not CG
│       ├── PlumeModel.java             ★ Hardcoded p_inf, P-M unit mixing
│       ├── PlumeState.java             Approximate plume inversion
│       ├── SideslipModel.java          ★ Only ΔCD, no C_Y/C_n/C_l
│       ├── SkinFrictionModel.java      ★ Magic 0.4 multiplier, PR_TURB dead
│       ├── TransonicBlendingModel.java ★ DUPLICATE (also in rom/)
│       └── WaveDragModel.java          ★ M=1.0 discontinuity, no M>1 gate
│
├── grid/
│   ├── DragGridEvaluator.java          Component build-up 80×20×10
│   ├── DragSurfaceInterpolator.java    PCHIP tensor-product interp
│   └── BaseDragClosures.java           Gaussian bucket blending
│
├── integration/
│   ├── CalibrationOverlay.java         ★ Magic 0.1/0.05 factors, dead code
│   ├── ConfidenceScorer.java           ★ noseHalfAngle unused, hardcoded false
│   ├── ForceIntegrator.java            ★ WEAKEST LINK: magic multipliers
│   └── RomCache.java                   ★ CRITICAL STUB: evaluateAtPoint
│
├── marching/
│   ├── BoundaryLayerMarcher.java       Thwaites→Head BL chain
│   ├── FullOuterFlowDispatch.java      K-T / shock-expansion dispatch
│   ├── HeadTurbulent.java              Von Kármán momentum integral
│   ├── PoweredOuterFlow.java           ★ Plume Cp boost: no axial gating
│   ├── ThwaitesLaminar.java            Textbook RK4 Thwaites ODE
│   └── ENTransition.java               e^N amplification (dead path)
│
├── math/
│   ├── GasDynamics.java                Oblique shock, P-M, Sutherland
│   └── RegimeBlender.java              ★ LINEAR blend vs SIGMOID elsewhere
│
└── gui/
    ├── RomPrestepPanel.java            Swing pre-step config
    ├── RomTuningPanel.java             Tuning knob UI
    └── RomAdvancedPanel.java           Advanced settings
```

### Tuning Infrastructure

```
tuning/
├── EquationTuningRuleEngine.java       Channel→equation-group diagnostic flags
├── HeadlessOrkSimulationRunner.java    .ork headless runner + outlier filter
├── VerticalKinematicsReconstructor.java Complementary filter (baro+IMU fusion)
├── PhaseTwoScoringConfig.java          Warning/critical thresholds + weights
├── PhaseTwoScoreCalculator.java        MAPE/SMAPE/NRMSE composite scorer
├── PhaseTwoBatchRunner.java            Phase II batch orchestrator
├── PhaseThreeBatchRunner.java          Phase III production runner
├── PhaseThreeAnalysisSupport.java      ISA lapse, CD proxy, apogee scoring
└── jsonFiles_tuning/                   7 JSON configs (4 launch campaigns)
```

### Benchmark Suite

```
benchmark/
├── A06BodyAloneBenchmarkTest.java      Jorgensen NASA TR R-474
├── A07BodyWingTailBenchmarkTest.java   Jorgensen + PNK
├── A09TransonicComponentBenchmarkTest  Bachalo-Johnson transonic
├── A32DragClosureBenchmarkTest.java    Fleeman Tactical Missile Design
├── A34ConeCylinderBenchmarkTest.java   Taylor-Maccoll / NACA 1135
├── ClosureB06PittsNielsenTest.java     NACA TR 1307 interference
├── ClosureLockingBenchmarkTest.java    Multi-source closure locks
├── BenchmarkHelper.java
└── BenchmarkAssertions.java
```

---

## C. Ranked Bug List

### CRITICAL (blocks release)

| # | File | Bug | Impact |
|---|------|-----|--------|
| C1 | `RomCache.evaluateAtPoint()` | **STUB** — returns `CD = 0.2 + 0.05*mach`, ignores geometry/alpha/powered/ROM entirely | Cache is filled with fake data. Any query() hit returns nonsense. |
| C2 | `ForceIntegrator.java` | Magic multipliers: `0.025` axial, `0.35` CN_α, `0.55` CG proxy, `0.15` moment coupling. **Ignores separation flag from BL solver.** | Integrated forces are numerically tuned to a single configuration, not physics-derived. Forces are wrong for any rocket that doesn't match the tuning target. |
| C3 | `WaveDragModel.searsHaackDecay` | M=1.0 returns 1.0; M=1.001 returns ~9.1. **Discontinuity of ~8×** at sonic point | Drag coefficient jumps by 8× over 0.001 Mach increment at transonic. |
| C4 | `RegimeBlender` vs `TransonicBlendingModel` vs `FullOuterFlowDispatch` | Three different blending functions (LINEAR, SIGMOID K=30, QUINTIC) for M 0.8–1.2 | Inconsistent edge states, drag, and forces in transonic. Results depend on which code path is hit. |
| C5 | Missing $C_Y(\beta)$, $C_n(\beta)$, $C_l$ models | No side force, yawing moment, or rolling moment anywhere in physics suite | Cannot do 6-DOF simulation. Sideslip produces only ΔCD. |
| C6 | `PlumeModel.java` | `p_inf = 101325.0` **hardcoded** — ignores altitude. P-M angle mixes radians + ln(pressure ratio) | Powered-flight base pressure wrong at all altitudes except sea level. P-M expansion angle dimensionally inconsistent. |

### HIGH (wrong physics, significant error)

| # | File | Bug | Impact |
|---|------|-----|--------|
| H1 | `PitchingMomentModel.java` | Moments computed about **nose** (x=0), not CG. Formula: $C_m = -C_N \cdot x_{CP}/L_{body}$ | If caller expects moments about CG, every Cm value is wrong. Stability assessment invalidated. |
| H2 | `NormalForceModel.java` | Body CN = $2\alpha$ has **no Mach correction**; fin CN gets full P-G/Ackeret | Body-to-fin force ratio increasingly wrong with Mach. At M≈2, body term is ~2× too low. |
| H3 | `InducedDragModel.java` | Supersonic (M≥1.2) falls through with `compressibilityFactor=1.0` — uses incompressible lift slope at supersonic | Supersonic induced drag under-predicted. Should use Ackeret $C_{N\alpha}=4/\sqrt{M^2-1}$. |
| H4 | `InducedDragModel.java` | `alphaRad < 1e-6` check ignores negative AoA — returns 0 for nose-down | Zero induced drag for negative alpha (half the AoA envelope). |
| H5 | `PoweredOuterFlow.java` | Plume Cp boost applied to **ALL stations** (nose to base), not just aft-body | Upstream stations see spurious pressure modification from nozzle plume. |
| H6 | `WaveDragModel.cdFinWaveSupersonic` | `sqrt(M²-1)` at M=1 → division by zero, **no epsilon guard** | NaN/Infinity for Mach exactly 1.0 or 1+ε. |
| H7 | `WaveDragModel` conical nose | Returns nonzero "wave drag" at **subsonic** Mach — no `M>1` gate | Subsonic rockets get spurious wave drag added. |
| H8 | `RomGeometryParameters.java` | `finWettedArea` is TOTAL (×finCount), not per-fin as documented | Any code using it as per-fin gets finCount× too much area. Double-counting with explicit finCount multiplication. |
| H9 | `BaseDragModel.java` | Exponent `n` computed but **never used** — dead variable | Base drag formula is missing its Reynolds-number exponent term. |
| H10 | `FullOuterFlowDispatch` | `cpWithHump = cpBlended - deltaCd * 0.5` subtracts a **drag coefficient** from a **pressure coefficient** | Dimensionally wrong — CD and Cp are different quantities. |
| H11 | `FallbackBlender.blendCp` | CP position blended by flat lerp, not CN-weighted | If legacy CN≫ROM CN, the blended CP moves toward ROM's position despite ROM contributing negligible force. |
| H12 | Two duplicate `BaseDragModel.java` | `rom/` and `rom/core/physics/` — diverging types | Silent correctness risk: callers can get different results from different copies. |
| H13 | Two duplicate `TransonicBlendingModel.java` | `rom/` and `rom/core/physics/` — typo drift (`finessRatio` vs `finenessRatio`) | Same model exists twice with inconsistent naming. |
| H14 | Two duplicate `InducedDragModel.java` | `rom/` (RomGeometryParameters) vs `rom/core/physics/` (RomGeometryInput) — protuberance default comment says 1.04 but field is 1.02 | Type mismatch between copies; comment/code disagreement. |

### MEDIUM (degraded accuracy, missing features)

| # | File | Bug | Impact |
|---|------|-----|--------|
| M1 | `SkinFrictionModel.java` | `0.4` multiplier in `cdFriction()` — should be geometry-dependent wetted/reference area ratio | Friction drag wrong for any rocket with wetted/ref ratio ≠ 0.4. |
| M2 | `NormalForceModel.java` | No crossflow drag term at high α — slender body `2α` breaks down above ~15° | CN severely under-predicted at large AoA. |
| M3 | `NormalForceModel.java` | Supersonic PG factor `1/√(M²-1)` applied as multiplier on incompressible $C_{N\alpha}$ | Double-counts: incompressible lift slope × Ackeret correction ≠ standard supersonic result. |
| M4 | `ConfidenceScorer.generateWarnings` | Hardcodes `calibrationActive=false` — always warns in transonic | User sees spurious "no calibration" warning even when calibration overlay is loaded. |
| M5 | `RomGeometryParameters.java` | `baseArea = referenceArea` — ignores boattail | Boattailed rockets get wrong base drag (too high). |
| M6 | `RomGeometryParameters.java` | `motor.getDiameter()` returns casing OD, not nozzle exit diameter | Plume model exit area wrong; base drag annular area wrong. |
| M7 | `CalibrationOverlay.importOpenFOAMSurface` | `xNorm = pointIndex / 100` — ignores actual (x,y,z) coordinates | CFD overlay x-positions are meaningless; interpolation on imported data is wrong. |
| M8 | `CalibrationOverlay` | `0.1` (CA scaling) and `0.05` (base drag scaling) are unphysical magic constants | Cp→force integration should use surface-area weighting, not scalar factors. |
| M9 | `BoundaryLayerMarcher.java` | `N_CRIT = 9.0` declared, amplification factor computed via `ENTransition.stepN`, but never compared to N_CRIT | e^N transition path is dead code — transition is purely Re-based. |
| M10 | `PlumeModel.java` | `addyFactor()` computed but **never called** from `poweredBaseCp()` | Plume entrainment correction is dead code. |
| M11 | `PlumeModel.java` | `Ra_Rj` parameter passed to `jetOnBaseCp()` but unused inside | Parameter exists for future use but currently does nothing. |
| M12 | `SkinFrictionModel.java` | Van Driest II: potential division by zero when mach→0 and t_ratio=1 | Edge-case numerical instability at very low Mach. |
| M13 | `BaseDragClosures.java` | `Math.max(endpointBlend, bucket)` creates C0 but not C1 continuous transition | Gaussian width=0.09 is very narrow → derivative jumps at blend edges. |
| M14 | `DragGridEvaluator.java` | Plume-on `cd_trans` missing `cd_base_transonic_peak` addend that plume-off includes | Powered transonic drag under-predicted vs coast. |
| M15 | `InducedDragModel.java` | No sideslip coupling — all induced drag in pitch plane only | Under-predicts total vortex drag in combined (α, β) flight. |
| M16 | `PitchingMomentModel.java` | `cnBody = 2|α|` decomposition ignores compressibility corrections in the input CN | Over-attributes force to body, collapses xCp to nose position at high Mach. |
| M17 | `PitchingMomentModel.java` | ELLIPSOID CP = 0.333 (same as CONICAL) — should be 0.500 | ~50% moment arm error for ellipsoidal nose cones. |
| M18 | `RomGeometryParameters.java` | Multi-fin-set rockets use only first `TrapezoidFinSet`'s geometry | Second/third fin sets completely ignored. |
| M19 | `FallbackBlender.java` | CD components (pressure+base+friction) blended independently of total CD | Additive decomposition breaks: `CD ≠ pressureCD + baseCD + frictionCD` after blending. |
| M20 | `RomCache.java` | Cache key is geometry-only (hash), doesn't include RomSettings | Changing ROM settings (e.g., fallback mode, transonic band width) doesn't invalidate cache. |

### LOW (cosmetic, future-proofing, minor inaccuracy)

| # | File | Issue |
|---|------|-------|
| L1 | `InducedDragModel` | β² floor of 0.35 is arbitrary, not standard |
| L2 | `InducedDragModel` | Body cross-flow coefficient 0.075 — no cited reference |
| L3 | `InducedDragModel` | Span efficiency e=0.9 hardcoded, no Mach dependence |
| L4 | `FinDragModel` | Form factor `1+2(t/c)` oversimplified — standard is `1+2(t/c)+60(t/c)⁴` |
| L5 | `FinDragModel` | Interference factor 0.04 constant — should depend on d/s, finCount, Mach |
| L6 | `FinDragModel` | Induced drag uses `CL*sin(αeff)` instead of `CL²/(π·AR·e)` |
| L7 | `SideslipModel` | crossflowCdCf flat at 0.8 for all M≥1 — should vary |
| L8 | `SideslipModel` | areaRatio formula doesn't use g.referenceArea — implicit base-area assumption |
| L9 | `ConfidenceScorer` | `noseHalfAngle` parameter is dead in both `score()` and `generateWarnings()` |
| L10 | `CalibrationOverlay` | `ValidationReport` inner class is dead code (never instantiated) |
| L11 | `SkinFrictionModel` | `PR_TURB = 0.9` declared but never used |
| L12 | `GasDynamics` | `maxDeflectionAngle` brute-force scan of 2001 points — slow |
| L13 | `GasDynamics` | Prandtl-Meyer inversion initial guess poor for large ν |
| L14 | `BaseDragModel` | Comment says 215.8 but code uses 175.0 |
| L15 | `FullOuterFlowDispatch` | Anchor states at M=0.80/1.20 don't adjust density/pressure isentropically |
| L16 | `FullOuterFlowDispatch` | Syvertson-Dennis correction method exists but never called |
| L17 | Codebase-wide | Inconsistent smoothstep orders: cubic (NormalForceModel), quintic (FullOuterFlowDispatch) |
| L18 | `NormalForceModel` | `betaRad` accepted but never used |
| L19 | `PitchingMomentModel` | No Mach dependence on nose CP fractions |

---

## D. Tuning Audit

### Tuning Infrastructure Assessment: EXCELLENT

The tuning pipeline is well-designed and physics-grounded:

| Component | Assessment |
|-----------|-----------|
| `EquationTuningRuleEngine` | Clean diagnostic mapper — channel→equation-group. No fudge factors. Physically grounded. |
| `HeadlessOrkSimulationRunner` | Outlier filter thresholds (250 m/s² floor, 6× multiplier) are reasonable for 20 Hz telemetry. Not compensating for bugs. |
| `VerticalKinematicsReconstructor` | Textbook complementary filter. ISA-standard barometric constants. Phase-dependent gains (0.12/0.18/0.30) follow standard sensor-fusion practice. |
| `PhaseTwoScoringConfig` | Channel weights (velocity 2.0, altitude 1.5, atmosphere 1.0, lateral 0.5) correctly prioritize aero-sensitive channels. |
| `PhaseTwoScoreCalculator` | MAPE/SMAPE/NRMSE composite (45/35/20) — standard time-series methodology. |
| `PhaseThreeAnalysisSupport` | All constants are ISA standard, physically bounded, or engineering policy. |
| `Benchmark suite` | 9 tests anchored to NASA TR R-474, NACA TR 1307, Fleeman, Taylor-Maccoll. Gold standard. |

### Tuning Constants vs Bug Compensation

**None of the tuning infrastructure constants appear to be compensating for bugs.** The complementary filter gains, scoring weights, outlier thresholds, and atmospheric model parameters are all independently justified.

**However:** The physics model magic constants (ForceIntegrator's 0.025/0.35/0.55/0.15, CalibrationOverlay's 0.1/0.05, PoweredOuterFlow's 0.15, InducedDragModel's 0.075, FinDragModel's 0.04) are **unvalidated empirical knobs** that have never been through a tuning pass — TUNING_LOG.md is blank.

### Tuning Knob Inventory

| Location | Constant | Value | Physically Justified? |
|----------|----------|-------|----------------------|
| ForceIntegrator | axial pressure multiplier | 0.025 | **NO** — unexplained 2.5% |
| ForceIntegrator | CN_α gain | 0.35 | **NO** — unexplained 35% |
| ForceIntegrator | CG proxy fraction | 0.55 | **NO** — hardcoded, should use actual CG |
| ForceIntegrator | moment coupling | 0.15 | **NO** — unexplained 15% |
| PoweredOuterFlow | plume Cp boost | 0.15/M | **NO** — no physical derivation |
| CalibrationOverlay | CA scaling | 0.1 | **NO** — Cp→CA needs area integration |
| CalibrationOverlay | base drag scaling | 0.05 | **NO** — same problem |
| InducedDragModel | body cross-flow | 0.075 | **NO** — no reference |
| FinDragModel | interference factor | 0.04 (4%) | **NO** — should depend on d/s ratio |
| SkinFrictionModel | area ratio | 0.4 | **NO** — should be geometry-derived |
| VerticalKinematics | boost alt gain | 0.12 | YES — sensor fusion |
| VerticalKinematics | coast alt gain | 0.18 | YES — sensor fusion |
| VerticalKinematics | descent alt gain | 0.30 | YES — sensor fusion |
| PhaseTwoScoring | MAPE weight | 0.45 | YES — statistical |
| PhaseThreeAnalysis | CD proxy bounds | 0.01–2.5 | YES — physics bounds |

---

## E. Patch Plan

### Priority 1: Critical Fixes (blocks all validation)

#### E1. Implement `RomCache.evaluateAtPoint()` (C1)
**File:** `integration/RomCache.java`, `evaluateAtPoint()` method  
**Action:** Replace stub with actual ROM evaluation. The method already receives `PathlineROMCalculator rom` and `GeometryFeatures geometry` — wire them to the real evaluation path. Create a synthetic `FlightConditions` at each (Mach, α, powered) grid point and call `rom.evaluate()`.  
**Risk:** Low — the method signature already has everything needed.  
**Test:** After fix, verify `query()` returns non-linear, geometry-dependent values.

#### E2. Fix WaveDragModel sonic discontinuity (C3, H6, H7)
**File:** `core/physics/WaveDragModel.java`  
**Action:**  
- Add epsilon guard: `betaM = sqrt(max(M²-1, 1e-6))` in `cdFinWaveSupersonic`  
- Add `if (mach < 1.0) return 0.0;` gate to conical nose wave drag  
- Replace `searsHaackDecay` step function with continuous sigmoid transition over M 0.95–1.05  
**Test:** Verify CD is continuous through M 0.9–1.1 with max slope < 10/Mach-unit.

#### E3. Unify transonic blending (C4)
**Files:** `RegimeBlender.java`, `TransonicBlendingModel.java`, `FullOuterFlowDispatch.java`  
**Action:** Choose ONE blending function (recommend quintic smoothstep from `FullOuterFlowDispatch`) and apply consistently. Delete the duplicates. Use configurable `RomSettings.transonicBandHalfWidth` for the blend boundaries.  
**Test:** Verify that RegimeBlender, TransonicBlendingModel, and FullOuterFlowDispatch produce identical weights at M=0.85, 0.95, 1.0, 1.05, 1.15.

#### E4. Fix PlumeModel altitude dependence (C6)
**File:** `core/physics/PlumeModel.java`  
**Action:** Replace `p_inf = 101325.0` with the actual freestream pressure from `FlowState`. Fix the Prandtl-Meyer angle computation to use consistent radian units throughout.  
**Test:** Verify base Cp changes with altitude at constant Mach/thrust.

### Priority 2: High-Impact Physics Fixes

#### E5. Fix ForceIntegrator magic constants (C2)
**File:** `integration/ForceIntegrator.java`  
**Action:**  
- Replace `0.025` with proper $\int C_p \cos\theta \, dA / S_{ref}$ integration  
- Replace `0.35` with $C_{N\alpha}$ from slender body + fin theory (already computed in NormalForceModel)  
- Replace `0.55 * bodyLength` with actual CG from `FlightConfiguration`  
- Replace `0.15` coupling term with physics-derived $C_m$ from `PitchingMomentModel`  
- **Use** the separation flag from BL solver to reduce forces in separated regions  
**Test:** Compare integrated forces to benchmark datasets A06, A07, A32.

#### E6. Fix PitchingMomentModel reference point (H1)
**File:** `core/physics/PitchingMomentModel.java`  
**Action:** Change to $C_m = C_N \cdot (x_{CG} - x_{CP}) / L_{ref}$ where $x_{CG}$ is passed as parameter. Document the moment reference convention.  
**Test:** Verify sign convention: positive Cm = nose-up for CG ahead of CP.

#### E7. Add body Mach correction to NormalForceModel (H2)
**File:** `core/physics/NormalForceModel.java`  
**Action:** Apply Prandtl-Glauert/Ackeret correction to `cnBody` term, not just fins. For supersonic, use $C_{N,body} = 2\alpha / \sqrt{M^2-1}$ per Ackeret.  
**Test:** Verify body/fin CN ratio is within 10% of Jorgensen data (A06) across Mach range.

#### E8. Fix InducedDragModel supersonic fallthrough (H3, H4)
**File:** `core/physics/InducedDragModel.java`  
**Action:**  
- Add `else { compressibilityFactor = 1.0 / sqrt(M²-1); }` for M≥1.2  
- Change `alphaRad < 1e-6` to `Math.abs(alphaRad) < 1e-6`  
**Test:** Verify induced drag is nonzero at M=2.0, α=5° and at α=−5°.

#### E9. Fix PoweredOuterFlow axial gating (H5)
**File:** `marching/PoweredOuterFlow.java`  
**Action:** Apply plume Cp boost only to stations with `x_norm > 0.8` (aft-body near nozzle). Use exponential decay upstream.  
**Test:** Verify nose Cp is unchanged between coast and powered at same Mach.

#### E10. Deduplicate BaseDragModel, TransonicBlendingModel, InducedDragModel (H12–H14)
**Files:** Delete `rom/BaseDragModel.java`, `rom/TransonicBlendingModel.java`, `rom/InducedDragModel.java`. Keep `rom/core/physics/` versions only. Update all imports.  
**Test:** Full build + benchmark suite passes.

#### E11. Fix FallbackBlender CP weighting (H11)
**File:** `control/FallbackBlender.java`  
**Action:** Change `blendCp()` to use CN-weighted average: $x_{CP} = (w_{rom} C_{N,rom} x_{rom} + w_{leg} C_{N,leg} x_{leg}) / (w_{rom} C_{N,rom} + w_{leg} C_{N,leg})$  
**Test:** Verify blended CP is closer to high-CN source's CP.

#### E12. Fix RomGeometryParameters finWettedArea semantics (H8)
**File:** `core/geometry/RomGeometryParameters.java`  
**Action:** Either (a) rename to `finWettedAreaTotal` and update all callers, or (b) store per-fin area and multiply by finCount at usage sites. Audit all 6+ call sites.  
**Test:** Verify skin friction drag matches hand calculation for a 4-fin rocket.

### Priority 3: 6-DOF Completion (C5 — new models needed)

#### E13. Add $C_Y(\beta)$ side-force model
**New file:** `core/physics/SideForceModel.java`  
**Action:** Mirror `NormalForceModel` but for the yaw plane. $C_Y = C_{Y,body}(\beta) + C_{Y,fin}(\beta, M)$.

#### E14. Add $C_n(\beta)$ yawing moment model
**New file or extend `PitchingMomentModel`**  
**Action:** $C_n = C_Y \cdot (x_{CG} - x_{CP,lateral}) / L_{ref}$ — identical logic to pitch moment but in yaw plane.

#### E15. Add $C_l$ rolling moment model
**New file:** `core/physics/RollMomentModel.java`  
**Action:** Include fin cant angle, differential fin loading from (α,β), and roll damping.

### Priority 4: Medium Fixes

| ID | Fix |
|----|-----|
| E16 | `ConfidenceScorer.generateWarnings`: pass actual `calibrationActive` state instead of hardcoded `false` |
| E17 | `RomGeometryParameters`: compute `baseArea` from actual boattail/nozzle geometry |
| E18 | `RomGeometryParameters`: use `motor.getNozzleExitDiameter()` if available |
| E19 | `CalibrationOverlay.importOpenFOAMSurface`: use actual x-coordinate from CFD mesh |
| E20 | `RomCache.isValid`: include `RomSettings` hash in cache key |
| E21 | `NormalForceModel`: add crossflow drag term for α > 15° |
| E22 | `BoundaryLayerMarcher`: either use e^N transition or remove dead N_CRIT code |
| E23 | `SkinFrictionModel`: make 0.4 multiplier geometry-derived or pass wetted/ref ratio |
| E24 | `FallbackBlender`: compute total CD from components, not independent lerp |

---

## F. Regression and Benchmark Plan

### Tier 1: Unit-Level Physics Guards

For each fix in §E, add a focused unit test:

| Fix | Test |
|-----|------|
| E1 (RomCache) | `RomCachePhysicsTest` — verify cached CD varies with geometry, alpha, Mach |
| E2 (WaveDrag) | `WaveDragContinuityTest` — sweep M 0.8–1.3, assert max dCD/dM < threshold |
| E3 (Transonic) | `TransonicBlendConsistencyTest` — all three blending paths produce identical weights |
| E4 (Plume) | `PlumeAltitudeTest` — base Cp at 10km differs from sea level by > 10% |
| E5 (ForceInt) | `ForceIntegratorBenchmarkTest` — match A06/A07 within ±15% |
| E6 (Cm ref) | `MomentReferenceTest` — Cm sign correct for CG ahead/behind CP |
| E8 (Induced) | `InducedDragSymmetryTest` — CD(+α) = CD(−α) |

### Tier 2: Existing Benchmark Suite (Must Not Regress)

Run the full benchmark suite after each Priority 1–2 fix:
```
./gradlew.bat :core:test --tests "info.openrocket.core.aerodynamics.rom.benchmark.*"
```

Acceptance criteria: no benchmark tolerance band increases > 5% absolute.

### Tier 3: Phase II Batch Validation

After Priority 1–3 fixes, run the Phase II batch comparison against all 4 launch campaigns:
```
./gradlew.bat :core:test --tests "info.openrocket.core.tuning.PhaseTwoBatchRunner"
```

Track per-channel scores. Expected improvement in `velocityZ` and `accelZ` channels (currently dominated by ForceIntegrator magic constant errors).

### Tier 4: Cross-Configuration Regression

Test the fixed ROM against varied rocket geometries:

| Test Case | Key Feature |
|-----------|-------------|
| Single-stage, 3-fin, ogive nose, M < 0.6 | Baseline subsonic |
| Single-stage, 4-fin, conical nose, M ≈ 0.95 | Transonic stress test |
| Two-stage, M > 1.5 | Supersonic + staging |
| Boattailed with nozzle, powered flight | Base drag + plume |
| High AoA (α = 15°, 25°) | Crossflow + separation |
| Sideslip β = 10° (after 6-DOF models added) | Lateral force validation |

### Tier 5: Validation Ladder (per-physics-model)

| Model | Reference | Quantity | Target |
|-------|-----------|----------|--------|
| `SkinFrictionModel` | Eckert reference-temperature, Van Driest II | $C_f$ vs Re, M | ±5% |
| `BaseDragModel` | Fleeman Fig 3-12, Love ARL 151 | $C_{D,base}$ vs M | ±10% |
| `WaveDragModel` | Sears-Haack theory, NACA 1135 | $C_{D,wave}$ vs M | ±8% |
| `NormalForceModel` | Jorgensen TR R-474 (A06) | $C_N$ vs α, M | ±10% |
| `PitchingMomentModel` | Jorgensen, Barrowman | $C_m$ vs α | ±15% |
| `InducedDragModel` | Oswald efficiency theory | $C_{D,i}$ vs α | ±12% |
| `TransonicBlending` | Bachalo-Johnson (A09) | $C_D$ transonic peak | ±15% |
| `PlumeModel` | Avgoustinatos & Perkins | $C_{D,base,powered}$ vs NPR | ±20% |
| Integrated pipeline | Phase II launch data | Apogee, velocity profile | ±5% apogee, 80+ score |

---

## G. Final Judgment

### Preserve the Direction

The Pathline ROM architecture is fundamentally sound. The pipeline design (geometry → flow → regime → BL march → integrate → derive forces → confidence → blend) is the right approach for a semi-empirical rocket aero model. The tuning/validation infrastructure is excellent — the Phase II/III batch runners, NASA wind-tunnel corpus (105 sources), and benchmarks anchored to NACA/NASA technical reports represent serious engineering investment.

### What Must Change Before Release

1. **Fix the stub** (C1) — this is the single most impactful bug. The cache is a key performance optimization, and it's currently returning fake numbers.

2. **Fix the transonic discontinuities** (C3, C4) — M 0.8–1.2 is the most operationally important regime for model rockets, and the ROM has three inconsistent blending functions plus an 8× drag jump at M=1.0.

3. **Fix ForceIntegrator** (C2) — the magic constants mean the integrated forces are numerically calibrated to one rocket, not physics-derived. This makes the ROM unreliable for any other geometry.

4. **Fix PlumeModel altitude dependence** (C6) — hardcoded sea-level pressure invalidates all powered-flight predictions at altitude.

5. **Add Cm reference point clarification** (H1) — without knowing whether moments are about nose or CG, the stability analysis is meaningless.

### What Can Ship Later

- 6-DOF lateral models ($C_Y$, $C_n$, $C_l$) — the ROM can launch as 3-DOF with a clear "pitch/yaw plane only" limitation documented.
- CalibrationOverlay improvements — this is an optional enhancement layer.
- e^N transition — the Re-based transition works adequately for model rockets.
- RomCache thread safety — OpenRocket is single-threaded in simulation.

### Estimated Fix Effort (rough ordering)

| Priority | Fixes | Estimated Complexity |
|----------|-------|---------------------|
| P1 | E1, E2, E3, E4 | 4 focused changes, each < 50 lines |
| P2 | E5, E6, E7, E8, E9, E10, E11, E12 | 8 changes, E5 is largest (~100 lines) |
| P3 | E13, E14, E15 | 3 new files, ~200 lines each |
| P4 | E16–E24 | 9 small fixes, each < 20 lines |

### Bottom Line

The Pathline ROM is **70% of the way to a correct, validated semi-empirical aero model**. The architecture and validation infrastructure are already there. The remaining 30% is fixing the critical stub, eliminating magic constants in the force integrator, unifying the transonic treatment, and adding 6-DOF loads. No architectural redesign is needed — the fixes are localized and testable.

---

*End of audit report.*
