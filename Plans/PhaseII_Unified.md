# Phase II Finalized Buildout - Unified Runtime ROM Specification, Integration Plan, and Acceptance Gates

Version: 1.0  
Status: Execution Ready  
Date: 2026-04-18  
Audience: Implementation engineers  
Document role: Hybrid specification plus roadmap  
Release model: Hard acceptance gates  

This is the single authoritative Phase II document for the runtime ROM buildout. It supersedes the two prior Phase II planning documents for execution planning, implementation alignment, and acceptance decisions.

## 1. Purpose and Mission

Phase II turns the Phase I validated backbone into the operational runtime reduced-order aerodynamic solver for OpenRocket.

Mission statement:
- Build the real runtime ROM, not a lookup-table replacement with extra steps.
- Preserve numerical smoothness for the simulation stepper.
- Maintain runtime practicality through caching and interpolation.
- Keep solver limitations explicit through confidence scoring and warnings.
- Preserve fallback safety at all times.

## 2. Scope

### 2.1 Included in Phase II
- Full runtime aerodynamic ROM computation path.
- Regime dispatch across subsonic, transonic bridge, supersonic, and Mach 3 to 5 branch.
- Viscous marching and force/moment reconstruction for supported flight envelope.
- Base drag, boattail, and powered plume closures.
- Fin-body interference and high-angle body-lift augmentation.
- Runtime confidence scoring and fallback blending.
- User-facing ROM controls in OpenRocket UI.
- Runtime caching and coefficient reuse.
- Optional calibration overlay import and application.
- Diagnostics and export hooks for Phase III validation.

### 2.2 Explicitly Not Included in Phase II
- Final full validation campaign.
- Heavy coefficient tuning against CFD or flight data.
- Publication-grade benchmarking.
- Final extreme-performance hardening.
- Research extensions (neural corrections, unsteady ROMs).

## 3. Non-Negotiable Software Rules

1. No runtime CFD dependency. Solver must run without CFD data.
2. No hidden regression to giant lookup tables replacing physics.
3. Weak regimes must emit explicit warning and reduced confidence.
4. Preserve continuity across all regime transitions.
5. Preserve fallback path at all times.
6. Caching is a performance layer only, never a hidden change in physics.
7. Default constants are Phase II defaults and Phase III tunables unless stated fixed.
8. Keep method signatures in this file normative where declared.

## 4. Locked Phase II Decisions

The following decisions are locked for this execution baseline:

- Acceptance criteria are hard release gates.
- Balanced cache target is about 90 seconds; hard upper bound is under 3 minutes.
- Confidence model uses four levels: HIGH, MEDIUM, LOW, UNRELIABLE.
- Transonic policy:
  - No calibration: default LOW, may elevate to MEDIUM only if all health checks are clean.
  - Calibration loaded and clean: HIGH is allowed.
- Fallback blending is mandatory hysteretic sigmoid.
- Fallback enters at UNRELIABLE and exits only after one-level recovery to LOW.
- Global AoA confidence thresholds:
  - LOW when alpha > 8 degrees.
  - UNRELIABLE when alpha > 20 degrees.
- Calibration overlay is fully user-facing in Phase II, but optional at runtime.
- Grid policy includes presets plus adaptive refinement in Phase II.
- Naming normalization uses ManglersTransform class name while retaining Mangler terminology in explanatory text.

## 5. Runtime Architecture

Source: Synthesis (Full ROM Build plus Runtime Buildout)

### 5.1 Layered Solver Architecture

Layer 1 - Geometry and state:
- OpenRocket component tree as geometry authority.
- Preprocessed geometry features and surface patches.
- Flow state extracted from each simulation step.

Layer 2 - Analytic outer-flow reconstruction:
- Subsonic branch using Prandtl-Glauert and Karman-Tsien family.
- Transonic bounded bridge with drag-rise support.
- Supersonic branch using shock-expansion and Prandtl-Meyer.
- Cone sections via Taylor-Maccoll lookup.
- Blunt-nose high-Mach branch via modified Newtonian with Cp_max.

Layer 3 - Viscous and boundary-layer marching:
- Thwaites laminar baseline.
- Michel transition logic.
- Turbulent Head and/or Drela integral closure.
- Eckert reference-temperature compressibility baseline.
- Green lag-entrainment activation under strong non-equilibrium indicators.

Layer 4 - Force and moment assembly:
- Pressure integration.
- Viscous integration.
- Center-of-pressure extraction.
- Body-axis coefficients and derivative support where practical.

Layer 5 - Closures and guards:
- Base drag and boattail handling.
- Plume correction for powered state.
- Fin-body interference and high-angle body-lift augmentation.
- Confidence, warnings, and fallback blending.

### 5.2 Runtime Simulation Step Flow

The simulation stepper calls getAerodynamicForces() per simulation step:

1. Extract FlightConditions into RomInputState.
2. Query RomCache.query(mach, alpha, isPowered).
   - Cache hit: return interpolated AerodynamicForces in microseconds.
   - Cache miss: run full evaluation and store result when eligible.
3. Apply CalibrationOverlay if loaded.
4. Run ConfidenceScorer.
5. If fallback active, blend ROM and Barrowman with hysteretic sigmoid logic.
6. Emit warnings to WarningSet.
7. Return AerodynamicForces (or RomOutputBundle where configured).

### 5.3 Runtime Class Graph End State

Required classes and subsystems by Phase II completion:

Core runtime:
- PathlineROMCalculator
- FlowState
- FlowStateExtractor
- GeometryFeatures
- GeometryFeatureExtractor
- PathlineSeeder
- AdaptiveRefinementController
- SolverPreset

Outer-flow reconstruction:
- OuterFlowReconstructor
- FullOuterFlowDispatch
- KarmanTsienReconstructor
- ShockExpansionReconstructor
- TaylorMaccollTable
- ModifiedNewtonianReconstructor
- RegimeBlender
- PoweredOuterFlow

Boundary-layer and marching:
- BoundaryLayerMarcher
- ThwaitesLaminar
- MichelTransition
- HeadTurbulent and/or DrelaTwoEquation
- EckertReferenceTemperature
- GreenLagEntrainment
- CKLFreeInteraction
- ChengEntropyLayer
- MagerCrossflow
- ManglersTransform

Closures and forces:
- ForceIntegrator
- BaseDragModel
- BoattailModel
- KorstChowNash
- PlumeBaseDragModel
- PlumeModel
- PlumeState
- PittsNielsenKaattari
- JorgensenCrossflow
- FinAerodynamics
- FinBLMarcher

Integration, confidence, and persistence:
- RomCache
- CalibrationOverlay
- AerodynamicConfidence / ConfidenceScorer
- FallbackBlender
- RomOutputBundle

UI:
- RomAdvancedPanel

### 5.4 Package Additions and Placement

```text
core/src/main/java/info/openrocket/core/aerodynamics/rom/
  math/
    GreenLagEntrainment.java
    CKLFreeInteraction.java
    ChengEntropyLayer.java
    ManglersTransform.java
    MagerCrossflow.java
    KorstChowNash.java
    PittsNielsenKaattari.java
    JorgensenCrossflow.java
  geometry/
    (existing adapter expanded; no required new class type)
  marching/
    FullOuterFlowDispatch.java
    PoweredOuterFlow.java
  plume/
    PlumeModel.java
    PlumeState.java
  fins/
    FinAerodynamics.java
    FinBLMarcher.java
  integration/
    RomCache.java
    CalibrationOverlay.java
    ConfidenceScorer.java
    RomOutputBundle.java

swing/src/main/java/info/openrocket/swing/gui/simulation/
  RomAdvancedPanel.java
```

## 6. Detailed Physics and Module Specifications

Source: Full ROM Build (normative signatures) plus locked policy updates

### 6.1 FullOuterFlowDispatch.java

This replaces OuterFlowDispatch with complete regime-correct handling.

#### 6.1.1 Complete Regime Map

| Regime | M range | Body method | Note |
|---|---|---|---|
| Deep subsonic | M < 0.60 | Prandtl-Glauert | Slender-body accurate |
| Subsonic-compressible | 0.60 <= M < 0.80 | Karman-Tsien | Nonlinear compressibility correction |
| Transonic | 0.80 <= M <= 1.20 | Quintic blend plus Gaussian drag hump | Conservative bridge |
| Low supersonic | 1.20 < M <= 1.60 | Shock-expansion from known shock origins | Moderate oblique shocks |
| Supersonic (cone) | 1.20 < M <= 5.0 (conical section) | Taylor-Maccoll lookup | Exact conical solution |
| Supersonic (curved) | 1.20 < M <= 3.0 (ogive/shoulder) | Second-order shock-expansion (Syvertson-Dennis) | Better than first-order |
| Near-hypersonic | 3.0 < M <= 5.0 (non-blunt) | First-order shock-expansion | Slender-body adequate |
| Blunt stagnation | M >= 1.5 (blunt nose) | Modified Newtonian plus Cp_max | With Cheng correction downstream |
| Cylindrical midbody | any M | du_e/ds = 0 and p_e = p_prior | No pressure gradient |
| Boattail attached | any M, beta_BT < beta_crit(M) | Prandtl-Meyer expansion | Continuous from cylinder |
| Boattail separated | any M, beta_BT >= beta_crit(M) | Empirical plateau Cp | Separation model |

#### 6.1.2 Transonic Patch

For 0.80 <= M <= 1.20:
1. Compute subsonic anchor Cp(x) at M = 0.80 via Karman-Tsien.
2. Compute supersonic anchor Cp(x) at M = 1.20 via shock-expansion.
3. Blend with quintic smoothstep:

- t = (M - 0.80) / 0.40
- w(t) = 6 t^5 - 15 t^4 + 10 t^3
- Cp_transonic(x, M) = (1 - w(t)) * Cp_sub(x) + w(t) * Cp_super(x)

4. Add drag-rise hump:

- Delta_Cd = A_transonic * exp(-((M - M_peak) / sigma_M)^2)
- Defaults: A_transonic = 0.06, M_peak = 0.97, sigma_M = 0.09

5. Enforce area-rule sanity bound at M = 1 using Sears-Haack ceiling.

#### 6.1.3 Second-Order Shock-Expansion

For curved surfaces at M > 1.2:
- Cp_SOSE = Cp_1stOrder + delta_Cp_reflected

Signature:

```java
public static double syvertsonDennisCorrection(double Cp_firstOrder, double mach,
                                                double localCurvature,
                                                double deltaTheta)
```

#### 6.1.4 Boattail Critical Angle

```java
public static double boattailCriticalAngle(double mach)
```

Policy default:
- beta_crit = 11 + 5 * (1 - |M - 1|) degrees

### 6.2 GreenLagEntrainment.java

Purpose:
- Extend Head two-equation system to three equations with shear-lag memory C_tau.
- Activated under strong non-equilibrium indicators.

Third ODE:
- theta * d(sqrt(C_tau))/ds = C1*(sqrt(C_tau_eq) - sqrt(C_tau)) + C2*theta*d(ln ue)/ds*sqrt(C_tau)

State vector:
- [theta, H, C_tau]

Signatures:

```java
public class GreenLagEntrainment {

    public GreenLagEntrainment(double C1, double C2)

    public double equilibriumCTau(double Cf, double H)

    public double[] rhs(double s, double theta, double H, double C_tau,
                         double ue, double due_ds, double Re_per_m,
                         double mach_e, double T_e, double T_w)

    public double[] step(double s, double[] state, double ds,
                          double ue, double due_ds, double Re_per_m,
                          double mach_e, double T_e, double T_w)
}
```

Activation policy:
- Default path remains Head baseline.
- Activate Green when edge Mach jump > 0.3 or H_k > 2.4.
- Controlled via RomConfiguration.useGreenLagEntrainment.

### 6.3 CKLFreeInteraction.java

Purpose:
- Chapman-Kuehn-Larson free-interaction model for shock-induced BL separation.

Key relation:
- p_plateau / p_1 = 1 + 3 * gamma * M_1^2 * sqrt(2 * Cf_1 / sqrt(M_1^2 - 1))

Signatures:

```java
public final class CKLFreeInteraction {

    public static double plateauPressureRatio(double mach1, double Cf1, double gamma)

    public static boolean turbulentSeparationOnset(double mach1, double pressureRatio)

    public static double reattachmentPressureRatio(double mach1)

    public static void applyToMarch(BLState[] states, int shockStationIndex,
                                     double mach1, double gamma)
}
```

### 6.4 ChengEntropyLayer.java

Purpose:
- Blunt-nose entropy-layer swallowing correction.

Core relations:
- Bow-shock standoff: delta_s / R_nose ~= 0.78 * (rho_inf / rho_2)
- Swallowing distance: x_sw ~= f(gamma) * Re_inf(R_nose) * R_nose
- Corrected edge Mach and Cf multiplier evolve with x / x_sw.

Signatures:

```java
public final class ChengEntropyLayer {

    public static boolean isApplicable(ComponentSegment noseSeg)

    public static double bowShockStandoff(double R_nose, double mach_inf, double gamma)

    public static double swallowingDistance(double R_nose, double Re_inf_per_m, double gamma)

    public static double correctedEdgeMach(double x, double x_sw,
                                            double M_e_sharp, double M_e_stagnation)

    public static double cfMultiplier(double x, double x_sw, double M_e_sharp,
                                       double M_e_corrected, double gamma)
}
```

### 6.5 MagerCrossflow.java

Purpose:
- Add 3D crossflow term for off-axis flight.

Signatures:

```java
public final class MagerCrossflow {

    public static double crossflowAngle(double alpha, double phi, double s,
                                         double totalLength, double mach)

    public static double augmentedWallShear(double Cf_streamwise, double beta_w)

    public static double crossflowMomentumTerm(double theta, double beta_w,
                                                double Hk, double ue, double due_ds)

    public static boolean isValid(double alpha, double noseHalfAngle)
}
```

### 6.6 KorstChowNash.java

Purpose:
- Analytical base drag alternative for supersonic coast.

Signatures:

```java
public final class KorstChowNash {

    public static double mixingCoefficient(double mach_exit)

    public static double basePressureCoeff(double mach, double Re_theta_sep,
                                            double gamma, double sigma)

    public static double nashFactor()

    public static double fullBaseCp(double mach, double Re_theta_sep, double gamma)
}
```

### 6.7 PlumeState.java and PlumeModel.java

Purpose:
- Powered-flight base pressure and boattail modification.

PlumeState signature:

```java
public class PlumeState {
    public final double pe;
    public final double Te;
    public final double Me;
    public final double Ae;
    public final double gamma_j;
    public final boolean powered;
    public final double thrust;

    public static PlumeState fromMotorState(MotorConfiguration motor,
                                             AtmosphericConditions atm)
}
```

PlumeModel signatures:

```java
public final class PlumeModel {

    public static double plumeExpansionAngle(double pe, double p_inf, double Me,
                                              double gamma_j)

    public static double effectiveAfterbodyAngle(double boattailAngle, double delta_p)

    public static double addyFactor(double R_body_base, double R_jet_exit)

    public static double jetOnBaseCp(double mach_inf, double Cp_base_jet_off,
                                      double pe_ratio, double Me, double Ae_Ab,
                                      double Ra_Rj, double gamma_j)

    public static double brazzelBoattailModifier(double mach_inf, double D_base,
                                                   double D_max, double delta_p)

    public static double poweredBaseCp(PlumeState plume, double mach_inf,
                                        double Cp_base_coast, double D_base,
                                        double D_max, double R_body_base)
}
```

### 6.8 PittsNielsenKaattari.java

Purpose:
- Fin-body interference carryover factors.

Signatures:

```java
public final class PittsNielsenKaattari {

    public static double KWB(double sOverA, double mach)

    public static double KBW(double sOverA, double mach)

    public static double kWB(double sOverA)
    public static double kBW(double sOverA)

    public static double finNormalForce(FinPlanform fin, double alpha, double mach)

    public static double finCP(FinPlanform fin, double mach)

    public static double finWaveDrag(FinPlanform fin, double mach, double alpha, double aRef)

    public static double finPitchingMoment(FinPlanform fin, double alpha, double mach,
                                            double xRef, double lRef, double aRef)
}
```

### 6.9 JorgensenCrossflow.java

Purpose:
- Nonlinear body-lift increment at higher alpha.

Signatures:

```java
public final class JorgensenCrossflow {

    public static double crossflowDragCoeff(double mach_crossflow)

    public static double deltaCN(double alpha, double mach_inf,
                                  double A_planform, double A_ref, double eta)

    public static double planformArea(MeridianProfile profile)

    public static boolean isApplicable(double alpha, double noseHalfAngle)

    public static boolean selfCheck(double alpha, double mach)
}
```

Global policy note:
- Confidence thresholds in Section 8 are global authority for fallback behavior.
- Module-local applicability checks remain advisory diagnostics for model validity.

### 6.10 FinAerodynamics.java and FinBLMarcher.java

Purpose:
- Fin BL march and force assembly across all fin sets.

Signatures:

```java
public class FinAerodynamics {

    public double[] computeFinContributions(
        FinPlanform[] fins,
        double alpha,
        double mach,
        double Re_per_m,
        double T_e, double T_w,
        double qInf,
        double lRef, double aRef, double xRef,
        DrelaTwoEquation blModel,
        PittsNielsenKaattari pnk
    )
}

public class FinBLMarcher {

    public BLState[] marchFinBL(
        FinPlanform fin,
        double chordStation,
        double mach,
        double alpha_local,
        double Re_per_m,
        double T_e, double T_w,
        boolean upperSurface
    )
}
```

### 6.11 RomCache.java

Purpose:
- Sparse precomputed grid over Mach, alpha, and powered-state.

Signatures:

```java
public class RomCache {

    public static final int N_MACH_DEFAULT = 50;
    public static final int N_ALPHA_DEFAULT = 20;

    private double[][][] CA;
    private double[][][] CN;
    private double[][][] CY;
    private double[][][] Cm;
    private double[][][] xcp;
    private double[][][] CA_base;

    public long estimatedBuildTimeMs()

    public void buildCache(PathlineRomCalculator rom,
                           RomGeometryProvider geometry,
                           RomConfiguration config,
                           ProgressMonitor monitor)

    public AerodynamicForces query(double mach, double alpha, boolean isPowered)

    public boolean covers(double mach, double alpha)

    public boolean isValid(RomGeometryProvider geometry,
                           MotorConfiguration motor,
                           RomConfiguration config)

    public void invalidate()

    public byte[] serialize()
    public static RomCache deserialize(byte[] data)
}
```

### 6.12 CalibrationOverlay.java

Purpose:
- Optional CFD residual correction layer (never required for baseline runtime use).

Signatures:

```java
public class CalibrationOverlay {

    public boolean isLoaded()

    public void loadFromCSV(File csvFile) throws IOException

    public void saveToCSV(File csvFile) throws IOException

    public double applyResidualCp(double cp_physics, double mach, double alpha, double x)

    public double applyResidualCA(double CA_physics, double mach, double alpha)

    public double applyResidualCdBase(double Cd_base_physics, double mach)

    public void importOpenFOAMSurface(File ofPostProc, double mach, double alpha)

    public ValidationReport compareROMtoCFD(PathlineRomCalculator rom,
                                             RomGeometryProvider geometry,
                                             double[] mach_cases,
                                             double[] alpha_cases)

    public static class ValidationReport {
        public final double rmsErrorCA;
        public final double maxErrorCA;
        public final double rmsErrorCp_surface;
        public final double[] errorPerCondition;
        public final String summary;

        public void printToLog()
    }
}
```

CSV format:

```text
# PathlineROM Calibration Overlay v1.0
# Fields: mach, alpha_deg, x_norm (x/L_body), cp_cfd
0.95, 0.0, 0.05, 0.421
0.95, 0.0, 0.10, 0.312
...
```

### 6.13 ConfidenceScorer.java

Purpose:
- Compute confidence level, warnings, and fallback decision.

Signatures:

```java
public class ConfidenceScorer {

    public enum ConfidenceLevel {
        HIGH,
        MEDIUM,
        LOW,
        UNRELIABLE
    }

    public ConfidenceLevel score(double mach, double alpha, double noseHalfAngle,
                                  boolean calibrationActive,
                                  double[] separationFractions)

    public String[] generateWarnings(double mach, double alpha, double noseHalfAngle,
                                      double[] separationFractions)

    private String checkTransonicWithoutCalibration(double mach, boolean calibrationActive)
    private String checkLeeSideSeparation(double alpha, double noseHalfAngle)
    private String checkHighSeparationFraction(double[] separationFractions)
    private String checkEdgeMachJump(double maxEdgeMachJump)
    private String checkSelfConsistency(double CA_rom, double CA_barrowman_estimate)

    public boolean shouldFallback(ConfidenceLevel level, RomConfiguration config)
}
```

### 6.14 PathlineRomCalculator Integration Upgrades

1. Full Mach regime dispatch via FullOuterFlowDispatch.
2. Green lag-entrainment activation when non-equilibrium indicators fire.
3. CKL SWBLI at detected shock stations.
4. Cheng entropy-layer correction for blunt noses.
5. Mager crossflow term for off-axis marching.
6. PlumeModel integration in powered state.
7. PittsNielsenKaattari for fin interference.
8. JorgensenCrossflow for higher alpha nonlinear body lift.
9. Cache-first integration with direct-eval fallback.
10. CalibrationOverlay application if loaded.
11. ConfidenceScorer execution after each evaluation.
12. Barrowman fallback blending via hysteretic sigmoid.

## 7. Runtime Cache, Refinement, and Performance Policy

Source: Synthesis

### 7.1 Preset Grid Policy

Default presets:
- Fast: 20 x 10
- Balanced: 50 x 20 (default)
- Accurate: 100 x 40

Estimated build targets (16 GB reference machine):
- Fast: about 15 seconds
- Balanced: about 90 seconds
- Accurate: about 6 minutes (opt-in only)

Locked SLA:
- Balanced nominal target: about 90 seconds.
- Balanced hard upper bound: under 3 minutes.

### 7.2 Adaptive Refinement in Phase II

Adaptive refinement is in scope in Phase II and applies to:
- Near Mach 1 regions.
- Geometry discontinuities.
- Transition and separation onset regions.
- Meaningful lee-side resolution at moderate alpha.

### 7.3 Cache Invalidation Triggers

Invalidate cache on:
- Geometry change.
- Motor configuration change.
- ROM configuration change.
- Internal ROM version bump.

### 7.4 Runtime Query Requirements

- Cache query target is microsecond-class for nominal hit path.
- 1000 sequential cache queries must complete in under 100 ms total.
- Query path should avoid new object allocations.

## 8. Confidence, Warning, and Fallback Policy (Locked)

Source: Synthesis and decision lock

### 8.1 Confidence Levels

- HIGH: expected low error and model assumptions valid.
- MEDIUM: moderate uncertainty but acceptable runtime prediction.
- LOW: significant uncertainty; warnings required.
- UNRELIABLE: assumptions violated; fallback required.

### 8.2 Trigger Baselines

| Condition | Baseline level | Notes |
|---|---|---|
| 0.85 < M < 1.15, no calibration | LOW | May elevate to MEDIUM only when all checks are clean |
| 0.85 < M < 1.15, calibration loaded and checks clean | HIGH allowed | No other severe warnings active |
| alpha > 8 deg | LOW | Global confidence threshold |
| alpha > 20 deg | UNRELIABLE | Mandatory fallback entry |
| >10% separated streamline fraction | MEDIUM | Separation warning |
| Strong shock indicators (edge Mach jump high) | MEDIUM | SWBLI active warning |
| CA self-consistency failure vs sanity estimate | UNRELIABLE | Mandatory fallback entry |
| M > 5.0 | UNRELIABLE | Out-of-validity warning |

### 8.3 Fallback Blending and Hysteresis

- Fallback blend function: hysteretic sigmoid.
- Enter fallback at UNRELIABLE.
- Exit fallback only after recovery to LOW (one-level recovery hysteresis).
- Blend must remain continuous on both entry and exit.

## 9. UI Specification - RomAdvancedPanel

Source: Full ROM Build plus Runtime Buildout

### 9.1 Required Primary Controls

- ROM enable or disable selector.
- Preset selector (Fast, Balanced, Accurate).
- Confidence indicator.
- Runtime estimate.
- Warning display.
- Build cache action and progress.

### 9.2 Required Advanced Controls

- Pathline count tuning.
- Convergence tolerance tuning.
- Fallback toggle/policy view.
- Optional higher-fidelity compressibility toggle if supported.
- Debug visualization and export hooks.

### 9.3 Required Detailed UI Sections

Cache management section:

```text
[Build Cache]  [Estimated time: ~90 s]  [Progress: ...]
Grid: [Fast 20x10] [Balanced 50x20] [Accurate 100x40]
Cache status: Valid/Invalid  [Invalidate]
```

Regime status indicators:
- Component-level regime state.
- Component-level confidence state.
- Explicit transonic calibration status.

CFD validation section:

```text
CFD Overlay: [Not loaded/Loaded]
[Import CSV] [Import OpenFOAM]
[Run Validation Report]
Last report summary metrics
```

Per-pathline visualization:
- Cp(x) and Cf(x) for each meridian.
- Color coding for attached, marginal, separated conditions.

Preset configuration panel:

```text
Performance Preset: Fast / Balanced / Accurate
N_crit presets and custom input
Wall BC controls
Fallback behavior display and override options by config
```

### 9.4 UI Behavior Requirements

- Beginner path remains simple with safe defaults.
- Advanced path available but not mandatory.
- All low-confidence conditions visible during simulation.
- Layout must render correctly on 1920x1080 and 1366x768.

## 10. Implementation Pass Plan with Dependencies, Duration, Owners, and Checklists

Source: Runtime Buildout (expanded)

### Pass 2.1 - Finalize runtime class graph

Depends on: none  
Estimated duration: 1 to 2 weeks  
Suggested owner: Core runtime lead

Goal:
- Move from placeholders to operational runtime class layout.

Deliverables:
- Real implementations for skeleton interfaces.
- Stable settings schema.
- Reachable branch wiring for all core physics paths.

Done checklist:
- [ ] Core class graph compiles and integrates.
- [ ] Placeholders removed from supported branches.
- [ ] Settings serialization path implemented.

Verify checklist:
- [ ] Integration smoke test reaches all major branch entrypoints.
- [ ] Settings round-trip persists without loss.

### Pass 2.2 - Subsonic and low-supersonic production branch

Depends on: Pass 2.1  
Estimated duration: 2 weeks  
Suggested owner: Outer-flow plus BL pair

Goal:
- Deliver trustworthy low-Mach branch first.

Deliverables:
- Subsonic pressure reconstruction.
- Viscous drag and low-angle force/moment integration.
- Low-Mach base-drag support.

Done checklist:
- [ ] C_A, C_N, C_m smooth in low Mach sweeps.
- [ ] Small-angle consistency with Barrowman in expected envelope.

Verify checklist:
- [ ] No low-Mach chatter.
- [ ] CP trends align with expected OpenRocket behavior.

### Pass 2.3 - Supersonic branch (Mach 1.2 to 5)

Depends on: Pass 2.1 (can partially overlap with 2.2 after core contracts lock)  
Estimated duration: 2 to 3 weeks  
Suggested owner: Supersonic physics owner

Goal:
- Implement full analytic supersonic branch.

Deliverables:
- Shock-expansion and Prandtl-Meyer turning.
- Taylor-Maccoll cone support.
- Modified Newtonian blunt support.
- Supersonic fin pressure and wave-drag contributions.

Done checklist:
- [ ] Branch is analytic and lightweight.
- [ ] Outputs bounded and deterministic through Mach sweeps.

Verify checklist:
- [ ] No singular behavior at ordinary local turning angles.
- [ ] Continuity at supersonic branch entry.

### Pass 2.4 - Transonic bridge

Depends on: Passes 2.2 and 2.3  
Estimated duration: 1 to 2 weeks  
Suggested owner: Regime blending owner

Goal:
- Conservative transonic branch with explicit uncertainty handling.

Deliverables:
- RegimeBlender with monotone interpolation.
- Smooth Cp and drag-rise bridge.
- Warning and confidence behavior for transonic uncertainty.

Done checklist:
- [ ] No negative drag.
- [ ] No derivative spikes across 0.80 and 1.20 boundaries.

Verify checklist:
- [ ] Mach passage tests show no chatter.
- [ ] Confidence behavior follows Section 8 policy.

### Pass 2.5 - 3D off-axis support in supported envelope

Depends on: Passes 2.2 to 2.4  
Estimated duration: 2 weeks  
Suggested owner: Off-axis and force integration owner

Goal:
- Support practical weathercocking and moderate off-axis behavior.

Deliverables:
- Windward/leeward asymmetry.
- Mager crossflow hooks.
- Jorgensen augmentation.
- Fin-body interference integration.

Done checklist:
- [ ] Sensible C_N, C_m trends across moderate alpha.
- [ ] CP migration is smooth and believable.

Verify checklist:
- [ ] Confidence decreases appropriately beyond envelope.
- [ ] Fallback engages above intended envelope.

### Pass 2.6 - Base, boattail, and plume modules

Depends on: Passes 2.3 and 2.5 (can start earlier with stubs)  
Estimated duration: 2 weeks  
Suggested owner: Closures owner

Goal:
- Implement semi-empirical closures as first-class modules.

Deliverables:
- Coast base drag across regimes.
- Boattail attached/separated handling.
- Powered plume modifier with smooth state transitions.

Done checklist:
- [ ] Base drag positive and bounded.
- [ ] Ignition and burnout transitions smooth.

Verify checklist:
- [ ] No discontinuous drag jumps at motor-state transitions.
- [ ] Missing plume data degrades gracefully.

### Pass 2.7 - Runtime caching and coefficient reuse

Depends on: Passes 2.2 to 2.6 for stable coefficients  
Estimated duration: 1 to 2 weeks  
Suggested owner: Performance owner

Goal:
- Make runtime practical for full trajectories.

Deliverables:
- Geometry-keyed cache.
- Sparse state cache and interpolation.
- Invalidation and rebuild policies.

Done checklist:
- [ ] Warm calls significantly faster than cold calls.
- [ ] Cache-off mode returns same physics.

Verify checklist:
- [ ] Cache interpolation introduces no chatter.
- [ ] Query path passes allocation audit.

### Pass 2.8 - Adaptive refinement and solver presets

Depends on: Pass 2.7 baseline cache path  
Estimated duration: 1 week  
Suggested owner: Performance owner with physics review

Goal:
- Expose runtime/accuracy controls with safe defaults.

Deliverables:
- AdaptiveRefinementController.
- Preset definitions and runtime estimates.

Done checklist:
- [ ] Presets produce distinct behavior.
- [ ] Balanced remains default and practical.

Verify checklist:
- [ ] Fast mode remains simulation-safe.
- [ ] Accurate mode remains usable on normal hardware.

### Pass 2.9 - OpenRocket UI integration

Depends on: Passes 2.7 and 2.8 (and minimally stable physics output)  
Estimated duration: 1 to 2 weeks  
Suggested owner: UI owner

Goal:
- Surface ROM controls without overwhelming users.

Deliverables:
- Enable toggle, presets, confidence indicator, warnings.
- Advanced controls and debug/export hooks.

Done checklist:
- [ ] Primary controls function in simulation workflow.
- [ ] Advanced controls hidden by default.

Verify checklist:
- [ ] UI legible and stable at required screen sizes.
- [ ] Low-confidence states clearly visible.

### Pass 2.10 - Data export, diagnostics, introspection

Depends on: Passes 2.4 to 2.9  
Estimated duration: 1 week  
Suggested owner: Validation tooling owner

Goal:
- Make Phase III validation and debugging straightforward.

Deliverables:
- Coefficient summaries.
- Pathline export.
- Debug fields (Cp, Cf, theta, H, transition, separation).
- Regime/fallback/confidence logs.

Done checklist:
- [ ] Debug exports reproducible per run.
- [ ] Closure path visibility in logs is explicit.

Verify checklist:
- [ ] Exported data is sufficient for validation plotting and comparison.

## 11. Hard Acceptance Gates (Numbering Preserved)

Source: Full ROM Build (with locked policy harmonization)

### 11.1 Physics Correctness

1. Transonic drag rise present: CA(M=1.0) > CA(M=0.7) for any standard ogive-cylinder rocket. No negative transonic CA.  
Verification: Sweep M from 0.7 to 1.1 at fixed alpha and verify positive hump behavior.

2. Mach continuity: CA(M) curve has no step discontinuities > 2% across any regime boundary (subsonic/transonic, transonic/supersonic). Check at M = 0.80 and M = 1.20.  
Verification: Boundary-near finite-difference checks at both transition boundaries.

3. Base drag non-zero: Cd_base > 0 at all Mach numbers for coast flight.  
Verification: Coast-mode Mach sweep from 0.01 to 5.0.

4. Plume effect directionally correct: With motor burning at M=2.0, CA < CA_coast (plume shields base, reducing drag).  
Verification: Same state, powered versus coast comparison.

5. Fin contribution present: CN(alpha=5 deg) > 0. |CN_fins / CN_total| is between 30% and 80% for a typical finned rocket.  
Verification: Standard finned geometry benchmark case.

6. Jorgensen activation: At alpha=15 deg, CN growth is super-linear compared to alpha=5 deg result.  
Verification: Alpha sweep with nonlinear trend check.

7. CKL fires correctly: On a boattail with beta_BT > beta_crit, a separation warning is emitted and Cp_BT reflects plateau pressure.  
Verification: Controlled boattail shock test case.

8. Cheng entropy layer: On a blunt-nose rocket at M=3, Cf near the nose is lower than the sharp-nose prediction.  
Verification: Blunt versus sharp nose pair comparison.

9. Green lag-entrainment: Downstream of a shock, shape factor H recovery takes at least 5 stations, not an instantaneous reset.  
Verification: Shocked marching sequence station-by-station profile.

10. Barrowman sanity check: At M=0.5, alpha=2 deg, ROM CA within +/-5% of Barrowman. ROM CN_alpha within +/-5% of Barrowman.  
Verification: Baseline low-angle consistency test.

### 11.2 Performance

11. Cache build time: Balanced preset (50x20) builds in under 3 minutes on a 16 GB RAM machine.  
Verification: Timed build benchmark. Report nominal target near 90 seconds.

12. Cache query latency: 1000 sequential cache queries complete in under 100 ms total.  
Verification: Batched query microbenchmark.

13. Memory footprint: Full cache plus ROM state plus geometry adapter use under 500 MB heap.  
Verification: Heap usage profile under representative run.

14. No GC pressure in query path: Zero new object allocations during RomCache.query().  
Verification: Allocation profiling on hot query loop.

### 11.3 Confidence Scoring

15. Correct LOW flag: M=0.95 without calibration overlay implies ConfidenceLevel.LOW baseline.  
Verification: Transonic case without overlay; allow MEDIUM only if clean checks are explicitly satisfied.

16. Correct HIGH flag: M=2.0, alpha=2 deg, standard ogive-cylinder implies ConfidenceLevel.HIGH.  
Verification: Supersonic clean-case confidence test.

17. Correct UNRELIABLE flag: alpha=20 deg, theta_nose=5 deg implies ConfidenceLevel.UNRELIABLE.  
Verification: High-alpha threshold test.

18. Fallback triggers correctly: At UNRELIABLE, AerodynamicForces is blended toward Barrowman result, and CA does not jump discontinuously on entry/exit.  
Verification: Enter/exit hysteresis sweep with continuity assertions.

### 11.4 Integration

19. Null safety: No NullPointerException for boattail-less rocket, rocket with no fins, zero AoA, M=0.01.  
Verification: Four null/edge safety scenario tests.

20. Motor switch: Coast-to-powered transition produces smooth CA transition, and CA_powered <= CA_coast at same M.  
Verification: In-flight ignition scenario replay test.

21. Geometry change invalidates cache: Resizing a body tube marks cache invalid and next simulation rebuilds.  
Verification: Geometry mutation followed by cache status check and rebuild.

22. CFD import round-trip: Import CSV overlay, save .ork, reopen, and overlay remains present.  
Verification: Persistence round-trip test.

23. OR simulation dialog: ROM tab shows all new controls without layout errors on 1920x1080 and 1366x768 displays.  
Verification: UI layout test at required resolutions.

## 12. Required Acceptance Test Matrix

Source: Runtime Buildout

Functional tests:
- ROM enable/disable from UI.
- Preset selection changes behavior.
- Fallback path works.
- Cache invalidates on geometry change.
- Powered/coast transition remains smooth.

Physics tests:
- Low-Mach small-angle consistency.
- Transonic passage with no chatter.
- Supersonic continuity.
- Moderate alpha trend checks.
- Base and plume trend checks.

Numerical tests:
- No NaN/Inf in supported regimes.
- Bounded coefficient derivatives across Mach transitions.
- Bounded confidence output.
- Stable repeated-call behavior.

Performance tests:
- Cold-call timing.
- Warm-cache timing.
- Balanced trajectory practicality on 16 GB machine.
- Allocation audit in inner loops.

## 13. Risk Register with Mitigation and Verification Hooks

Source: Runtime Buildout (expanded)

Risk 1 - Transonic chatter:
- Mitigation: monotone interpolation, smoothing, explicit low-confidence behavior.
- Verification hook: Mach passage test for chatter and derivative spikes.

Risk 2 - Off-axis overclaim:
- Mitigation: strict confidence penalties and fallback beyond envelope; clear UI warnings.
- Verification hook: alpha sweeps confirm confidence drop and fallback transition.

Risk 3 - Runtime too slow:
- Mitigation: caching, presets, adaptive refinement where useful, low allocation strategy.
- Verification hook: cold/warm benchmarks plus allocation profile.

Risk 4 - Base/plume discontinuities:
- Mitigation: event smoothing and bounded modifiers with clean coast/powered separation.
- Verification hook: ignition and burnout continuity tests.

Risk 5 - UI complexity:
- Mitigation: beginner defaults, advanced controls hidden, confidence/warnings for guidance.
- Verification hook: workflow usability checks and layout checks.

## 14. Exit Criteria for Phase II

Phase II is complete when all are true:

1. ROM serves as selectable runtime aerodynamic solver.
2. Main Mach-regime branches are implemented and stable.
3. Solver returns supported coefficients through OpenRocket simulation path.
4. Base, plume, fallback, confidence, and caching systems are operational.
5. UI exposes controls cleanly for normal users.
6. Diagnostics are sufficient for Phase III validation and tuning campaign.

## 15. Recommended Developer Sequence

1. Finalize runtime class graph.
2. Finish trustworthy subsonic branch.
3. Finish supersonic branch.
4. Add conservative transonic bridge.
5. Add off-axis logic in supported envelope.
6. Add base and plume models.
7. Add caching and adaptive refinement.
8. Add UI.
9. Add diagnostics and export.
10. Freeze architecture before heavy tuning.

## Appendix A - Conflict Resolution and Final Decisions

1. Cache SLA conflict resolved:
- Balanced nominal target: about 90 seconds.
- Hard release gate: under 3 minutes.

2. Transonic confidence resolved:
- Without calibration: default LOW, MEDIUM only when all checks are clean.
- With calibration and clean checks: HIGH allowed.

3. Fallback model resolved:
- Hysteretic sigmoid is mandatory.
- Enter at UNRELIABLE, exit at LOW.

4. AoA threshold policy resolved:
- LOW above 8 degrees.
- UNRELIABLE above 20 degrees.

5. Calibration scope resolved:
- Fully user-facing in Phase II, optional at runtime.

6. Signature strictness resolved:
- Module signatures in Section 6 are normative.

7. Naming normalization resolved:
- ManglersTransform class naming is canonical in this baseline.

8. Traceability style resolved:
- Source tags are at major section level.

## Appendix B - Deferred to Phase III

Deferred items:
- Heavy coefficient tuning using large CFD/flight datasets.
- Final publication-grade benchmarking.
- Final hardening for extreme performance envelopes.
- Research extensions beyond Phase II practicality scope.

## Appendix C - Source Traceability by Major Section

- Sections 1 to 4: Synthesis of both source documents.
- Section 5: Runtime Buildout core architecture, expanded with Full ROM Build implementation detail.
- Section 6: Full ROM Build primary source, with policy harmonization from locked decisions.
- Sections 7 and 8: Synthesis and locked decision set.
- Section 9: Full ROM Build UI details plus Runtime Buildout UX constraints.
- Section 10: Runtime Buildout pass structure expanded into execution checklists.
- Section 11: Full ROM Build acceptance criteria, harmonized with locked policy decisions.
- Sections 12 to 15: Runtime Buildout matrix, risk, exits, and sequence, expanded for execution.
- Appendices: Synthesis and decision lock outputs.
# Phase II Finalized Buildout - Unified Runtime ROM Specification, Integration Plan, and Acceptance Gates

Requirement alignment is complete, and I now have enough decisions to produce a single execution-ready unified markdown with no unresolved scope assumptions.

This plan merges both source documents into one comprehensive engineer-facing master spec and roadmap, preserving strict module signatures, hard acceptance gates, detailed UI behavior, risk controls, and implementation sequencing.

**Steps**
1. Build the unified document shell and governance metadata.  
Depends on nothing.  
Output:
1. Final H1 title selected by me (per your preference).
2. Version/status/date block set to Execution Ready.
3. Major-section source tags included (Full ROM Build, Runtime Buildout, or Synthesis).
4. Included/excluded scope explicitly stated, with Phase III boundaries preserved.

2. Merge architecture and runtime flow into one normative backbone.  
Depends on step 1.  
Output:
1. Mission statement and software rules become normative guardrails.
2. 5-layer runtime architecture is retained.
3. End-to-end stepper flow is codified, including cache-first query path, overlay application, confidence scoring, and mandatory fallback behavior.

3. Integrate full physics/module specification with strict signature preservation.  
Depends on step 2.  
Output:
1. All module/class/method signatures from the Full ROM document remain normative.
2. Naming normalized to ManglersTransform class naming while keeping Mangler terminology in explanatory text.
3. Regime map and transonic patch behavior are included with explicit confidence policy constraints.

4. Reconcile and codify confidence, AoA, and fallback policies.  
Depends on step 3.  
Output:
1. Fixed four-level confidence model retained (HIGH, MEDIUM, LOW, UNRELIABLE).
2. AoA thresholds locked as:
1. LOW when alpha exceeds 8 degrees.
2. UNRELIABLE when alpha exceeds 20 degrees.
3. Transonic policy locked:
1. Without calibration: default LOW; MEDIUM only when checks are clean.
2. With calibration: HIGH allowed when no additional warning triggers fire.
4. Hysteretic sigmoid fallback locked:
1. Enter on UNRELIABLE.
2. Exit only after recovery to LOW (one-level hysteresis behavior).

5. Merge roadmap execution passes with explicit implementation dependencies, parallelism, timeline, ownership, and done/verify checklists.  
Depends on steps 2-4.  
Output:
1. Passes 2.1 through 2.10 are retained and expanded into actionable sub-steps.
2. Rough duration estimates added per pass.
3. Suggested owners added per pass (core solver, UI, validation, performance).
4. Each pass includes completion checklist and verification checklist.

6. Merge runtime performance and caching strategy as dual-layer policy.  
Depends on step 5.  
Output:
1. Preset grids retained (Fast, Balanced, Accurate).
2. Adaptive refinement retained in Phase II for:
1. Near Mach 1 regions.
2. Geometry discontinuities.
3. Transition/separation onset areas.
3. Cache SLA codified as:
1. Balanced target about 90 seconds.
2. Hard upper limit less than 3 minutes.

7. Preserve hard acceptance gates and add executable test procedures.  
Depends on steps 4-6.  
Output:
1. Original acceptance numbering 1-23 preserved exactly.
2. Criteria remain hard gates.
3. Each criterion gets concrete verification procedure guidance (functional, physics, numerical, performance).

8. Finalize detailed user-facing UI specification and optional calibration behavior.  
Depends on steps 5-7.  
Output:
1. Detailed control-level ROM panel spec retained.
2. Calibration overlay remains fully user-facing in Phase II.
3. Release is allowed when overlay is unconfigured (runtime optional behavior preserved).
4. Warning and confidence visibility requirements made explicit.

9. Add comprehensive appendices and finalize consistency pass.  
Depends on steps 1-8.  
Output:
1. Full conflict-resolution appendix with selected decisions.
2. Deferred-to-Phase-III appendix.
3. Source traceability appendix.
4. Terminology and threshold consistency pass across entire unified document.
5. Final formatting and readability pass for implementation teams.

**Relevant files**
- [Plans/other/Phase_2_Full_ROM_Build.md](Plans/other/Phase_2_Full_ROM_Build.md) — Primary detailed physics/module/API source.
- [Plans/Phase_II_ROM_Runtime_Buildout.md](Plans/Phase_II_ROM_Runtime_Buildout.md) — Primary architecture/passes/rules/risk source.
- Confirmed output target: sibling unified file in the same folder, with filename Phase_II_Finalized_Buildout.md.

**Verification**
1. Confirm every required decision from your answers is represented once and only once.
2. Confirm all major sections include source tags at major-section level.
3. Confirm all class and method signatures remain exact where specified.
4. Confirm confidence table, AoA thresholds, transonic policy, and fallback hysteresis match your locked choices.
5. Confirm acceptance criteria numbering remains 1-23 exactly.
6. Confirm each implementation pass includes:
1. Dependencies and parallel notes.
2. Rough duration.
3. Suggested owner.
4. Done and Verify checklist.
7. Confirm appendices include:
1. Conflict resolutions.
2. Deferred scope.
3. Traceability mapping.

**Decisions**
- Document type: Hybrid spec plus roadmap.
- Audience: Implementation engineers.
- Acceptance criteria: Hard gates.
- Cache SLA: Balanced target about 90 seconds; hard upper bound less than 3 minutes.
- Confidence model: Four fixed levels with explicit triggers.
- Transonic confidence:
1. No calibration: default LOW; MEDIUM only if clean.
2. Calibration loaded and clean: HIGH allowed.
- Calibration overlay: Fully implemented user-facing Phase II feature, runtime optional.
- Fallback: Mandatory hysteretic sigmoid blending.
- AoA envelope policy: LOW above 8 degrees; UNRELIABLE above 20 degrees.
- Grid/refinement policy: Preset grids plus adaptive refinement in Phase II.
- Constants policy: Keep defaults now, mark tunable in Phase III.
- UI depth: Detailed control-level spec.
- Traceability: Major-section source tags.
- Appendices: Full conflict/deferred/traceability coverage.
- Signature policy: Preserve exact signatures as normative.
- Status header: Execution Ready.
- Output mode: Single unified markdown only.

**Further considerations**
1. Session memory persistence is currently blocked because no workspace is open, so I could not write the required plan file at the session memory path.
2. Once a workspace is opened, this exact plan can be persisted immediately and then executed to generate the unified markdown file in the confirmed destination.
