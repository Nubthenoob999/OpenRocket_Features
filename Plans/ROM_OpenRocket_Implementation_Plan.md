# ROM Aerodynamic Drag Prestep — OpenRocket Implementation Plan

## Purpose

This document is an executable specification for an AI coding agent implementing a
Reduced-Order Model (ROM) aerodynamic drag prestep tab in OpenRocket. The ROM
evaluates enhanced semi-empirical physics equations across a dense (M, Re, α) grid,
fits a smooth PCHIP interpolation surface, and supplies that surface to the trajectory
integrator — replacing the inline Barrowman calculations with a precomputed,
regime-blended, Reynolds-aware drag model.

**No CFD is required to use this system.** CFD is an optional offline validation step
described at the end of this document.

---

## Orientation: where to look in the codebase

Before writing any code, locate the following in the OpenRocket source tree. All paths
are relative to the repository root.

```
core/src/main/java/info/openrocket/core/
  aerodynamics/
    AerodynamicCalculator.java        ← interface to implement against
    BarrowmanCalculator.java          ← existing implementation to study
    AerodynamicForces.java            ← return type for force queries
    FlightConditions.java             ← inputs: M, Re, α, and derived quantities
  simulation/
    SimulationConditions.java         ← where to attach the precomputed surface
    FlightDataBranch.java             ← trajectory data storage
  rocketcomponent/
    RocketComponent.java              ← component tree root
    NoseCone.java                     ← nose shape enum and geometry
    BodyTube.java
    Transition.java                   ← boattail/shoulder
    FinSet.java                       ← fin geometry
  models/atmosphere/
    AtmosphericModel.java             ← ISA density, viscosity, speed of sound
swing/src/main/java/info/openrocket/swing/
  gui/
    simulation/                       ← existing simulation tabs — add new tab here
    plot/                             ← chart utilities for Cd(M) preview
```

Read `BarrowmanCalculator.java` in full before starting. Understand every method
signature and every field it reads from `FlightConditions`. Your new calculator must
satisfy the same interface contract.

---

## Phase 0 — Data structures (implement first, no logic yet)

### 0.1 — `DragSurface.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/DragSurface.java`

```java
package info.openrocket.core.aerodynamics.rom;

/**
 * Precomputed Cd(M, Re, α) surface stored as two 3D grids:
 * one for powered flight (plume-on) and one for coast (plume-off).
 *
 * Axes are stored as strictly monotonically increasing double arrays.
 * Grid values are Cd referenced to maximum cross-sectional area.
 */
public class DragSurface {

    // Axis arrays — must be monotonically increasing
    public final double[] machAxis;     // e.g. 50 points, 0.01 to 4.0
    public final double[] logReAxis;    // e.g. 20 points, log10(1e4) to log10(1e8)
    public final double[] alphaAxis;    // e.g. 10 points, 0.0 to 15.0 degrees

    // Cd grids — shape [machAxis.length][logReAxis.length][alphaAxis.length]
    public final double[][][] cdPlumeOff;   // coast phase
    public final double[][][] cdPlumeOn;    // powered phase

    // Metadata
    public final String geometryHash;       // SHA-256 of the geometry parameters
    public final double looRmsePercent;     // leave-one-out error, 0 if not computed
    public final long buildTimestampMs;

    public DragSurface(double[] machAxis, double[] logReAxis, double[] alphaAxis,
                       double[][][] cdPlumeOff, double[][][] cdPlumeOn,
                       String geometryHash, double looRmsePercent) {
        this.machAxis = machAxis;
        this.logReAxis = logReAxis;
        this.alphaAxis = alphaAxis;
        this.cdPlumeOff = cdPlumeOff;
        this.cdPlumeOn = cdPlumeOn;
        this.geometryHash = geometryHash;
        this.looRmsePercent = looRmsePercent;
        this.buildTimestampMs = System.currentTimeMillis();
    }
}
```

### 0.2 — `RomGeometryParameters.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/RomGeometryParameters.java`

This is a plain record of every geometric quantity the physics model needs. Populate
it once from the component tree; pass it everywhere.

```java
package info.openrocket.core.aerodynamics.rom;

public class RomGeometryParameters {

    // Body dimensions (SI units throughout)
    public double bodyLength;          // m — total length nose tip to base
    public double maxDiameter;         // m — maximum body diameter
    public double baseArea;            // m² — π(d/2)²
    public double wetArea;             // m² — total wetted surface area
    public double noseLength;          // m — nose cone length
    public NoseShape noseShape;        // enum: CONICAL, OGIVE, VON_KARMAN, PARABOLIC, ELLIPSOID
    public double finessRatio;         // bodyLength / maxDiameter

    // Boattail / transition (zero if absent)
    public double boattailLength;      // m
    public double boattailBaseDiameter;// m — aft diameter if boattailed

    // Fin geometry (aggregate for all fin sets)
    public int finCount;               // total number of fins
    public double finRootChord;        // m
    public double finTipChord;         // m
    public double finSpan;             // m — semi-span from body
    public double finThickness;        // m
    public double finSweepAngle;       // radians — leading edge sweep from perpendicular
    public double finWettedArea;       // m² — one fin, both sides

    // Motor exit geometry (for plume-on base drag)
    public double motorExitDiameter;   // m — 0 if no motor selected
    public double motorExitArea;       // m²

    // Surface roughness
    public double surfaceRoughness;    // m — equivalent sand-grain k_s, default 6.4e-6 (paint)

    // Derived reference quantities (compute in constructor or factory)
    public double referenceArea;       // m² = π(maxDiameter/2)²

    public enum NoseShape {
        CONICAL, OGIVE, VON_KARMAN, PARABOLIC, ELLIPSOID, HAACK
    }

    /** Factory: populate from the live OpenRocket component tree. */
    public static RomGeometryParameters fromRocket(
            info.openrocket.core.rocketcomponent.FlightConfiguration config) {
        // Implementation in Phase 2.
        throw new UnsupportedOperationException("Implement in Phase 2");
    }

    /** Stable SHA-256 hash of all geometry fields for cache invalidation. */
    public String geometryHash() {
        // Implementation in Phase 2.
        throw new UnsupportedOperationException("Implement in Phase 2");
    }
}
```

---

## Phase 1 — Physics model layer

Each class in this phase is a pure-Java static computation with no OpenRocket
dependencies. They can be unit-tested in isolation.

### 1.1 — `SkinFrictionModel.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/SkinFrictionModel.java`

Implements compressible skin friction using the Van Driest II transformation. The
key improvement over Barrowman is a continuous, Re-dependent transition model
rather than a binary switch.

```java
package info.openrocket.core.aerodynamics.rom;

public class SkinFrictionModel {

    private static final double GAMMA = 1.4;
    private static final double PR_TURB = 0.9;     // turbulent Prandtl number
    private static final double RECOVERY_FACTOR = 0.88; // turbulent

    /**
     * Returns the incompressible average skin friction coefficient over
     * a flat plate of length L at Reynolds number Re_L.
     * Uses Prandtl-Schlichting with transition correction.
     *
     * Cf = 0.455 / (log10(Re_L))^2.58  − A/Re_L
     * where A = 1700 for Re_tr = 5e5 (default paint finish).
     */
    public static double cfIncompressible(double re_L, double re_tr) {
        if (re_L < 1e3) return 1.328 / Math.sqrt(re_L); // Blasius, fully laminar
        double cf_turb = 0.455 / Math.pow(Math.log10(re_L), 2.58);
        double A = re_tr * (0.455 / Math.pow(Math.log10(re_tr), 2.58)
                          - 1.328 / Math.sqrt(re_tr));
        return Math.max(cf_turb - A / re_L, 1.328 / Math.sqrt(re_L));
    }

    /**
     * Van Driest II compressibility transformation for turbulent flat-plate Cf.
     * Returns compressible Cf given incompressible Cf, freestream Mach M,
     * and wall temperature ratio T_w/T_e (use 1.0 for adiabatic wall).
     *
     * Reference: Hopkins & Inouye (1971), AIAA J.
     */
    public static double vanDriestII(double cf_incomp, double mach, double t_ratio) {
        double m = (GAMMA - 1.0) / 2.0 * mach * mach;
        double r = RECOVERY_FACTOR;
        double F = t_ratio;
        double rm = r * m;

        double A = Math.sqrt(rm / F);
        double B = (1.0 + rm - F) / F;
        double denom = Math.sqrt(4.0 * A * A + B * B);
        double alpha = (2.0 * A * A - B) / denom;
        double beta  = B / denom;

        // Clamp to valid arcsin range
        alpha = Math.max(-1.0, Math.min(1.0, alpha));
        beta  = Math.max(-1.0, Math.min(1.0, beta));

        double Fc = rm / Math.pow(Math.asin(alpha) + Math.asin(beta), 2.0);
        if (Fc <= 0 || Double.isNaN(Fc)) return cf_incomp;
        return cf_incomp / Fc;
    }

    /**
     * Roughness-limited skin friction (fully rough regime).
     * Cf_rough = [1.89 + 1.62 * log10(L/k_s)]^(-2.5)
     * Returns the larger of smooth-wall and roughness-limited values.
     */
    public static double cfWithRoughness(double cf_smooth, double bodyLength, double k_s) {
        if (k_s <= 0) return cf_smooth;
        double cf_rough = Math.pow(1.89 + 1.62 * Math.log10(bodyLength / k_s), -2.5);
        return Math.max(cf_smooth, cf_rough);
    }

    /**
     * Body form factor accounting for pressure gradient on body of revolution.
     * FF = 1 + 1.5*(d/L)^1.5 + 50*(d/L)^3
     */
    public static double bodyFormFactor(double diameter, double length) {
        double ratio = diameter / length;
        return 1.0 + 1.5 * Math.pow(ratio, 1.5) + 50.0 * Math.pow(ratio, 3.0);
    }

    /**
     * Complete friction Cd for the body, referenced to frontal area.
     * Applies Van Driest II, roughness correction, form factor, and
     * wetted-area-to-reference-area scaling.
     */
    public static double cdFriction(double mach, double re_L, RomGeometryParameters g) {
        double re_tr = transitionReynolds(re_L, g.surfaceRoughness, g.bodyLength);
        double cf_inc = cfIncompressible(re_L, re_tr);
        double cf_smooth = vanDriestII(cf_inc, mach, adiabaticWallRatio(mach));
        double cf = cfWithRoughness(cf_smooth, g.bodyLength, g.surfaceRoughness);
        double ff = bodyFormFactor(g.maxDiameter, g.bodyLength);
        return cf * ff * (g.wetArea / g.referenceArea);
    }

    /**
     * Effective transition Reynolds number as a function of Re and roughness.
     * Uses a blended model between clean (Re_tr = 5e5) and fully rough limits.
     */
    private static double transitionReynolds(double re_L, double k_s, double L) {
        double re_tr_clean = 5e5;
        if (k_s <= 0) return re_tr_clean;
        // Roughness Reynolds number at x = L
        // Transition trips at k+ ~ 5-10; approximate Re_tr reduction
        double k_plus_factor = k_s / L * re_L;
        if (k_plus_factor > 120) return 1e4; // fully rough, effectively immediate transition
        return Math.max(1e4, re_tr_clean * (1.0 - k_plus_factor / 120.0));
    }

    /** Adiabatic wall temperature ratio for turbulent boundary layer in air. */
    private static double adiabaticWallRatio(double mach) {
        return 1.0 + RECOVERY_FACTOR * (GAMMA - 1.0) / 2.0 * mach * mach;
    }
}
```

### 1.2 — `BaseDragModel.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/BaseDragModel.java`

Implements the Braeunig-modified Hoerner correlation with full transonic multiplier
and explicit plume-on/plume-off switching.

```java
package info.openrocket.core.aerodynamics.rom;

public class BaseDragModel {

    /**
     * Subsonic base drag (M < 0.6), referenced to BASE area.
     * Braeunig modification of Hoerner: Cd_base = Kb * (db/d)^n / sqrt(Cf)
     * where Kb and n depend on afterbody geometry (Lo = length aft of max diameter).
     *
     * For a simple cylinder (Lo = 0): Kb ≈ 0.0274*atan(0)+0.0116+1 = 1.0116
     */
    public static double cdBaseSubsonic(double cf_body, double loOverD) {
        double Kb = 0.0274 * Math.atan(loOverD) + 0.0116 + 1.0;
        double n  = 3.6542 * Math.pow(loOverD + 1e-9, -0.2733);
        n = Math.max(0.5, Math.min(n, 3.0)); // clamp to physical range
        return Kb / Math.sqrt(Math.max(cf_body, 1e-4));
    }

    /**
     * Transonic base drag multiplier (Braeunig empirical fit).
     *   0.6 ≤ M ≤ 1.0:  fb = 1.0 + 215.8*(M-0.6)^6
     *   1.0 < M ≤ 2.0:  cubic polynomial
     *   M > 2.0:         linear decay ~ 1/M
     */
    public static double transonicMultiplier(double mach) {
        if (mach < 0.6) return 1.0;
        if (mach <= 1.0) {
            double dm = mach - 0.6;
            return 1.0 + 215.8 * Math.pow(dm, 6.0);
        }
        if (mach <= 2.0) {
            double dm = mach - 1.0;
            return 2.0881*dm*dm*dm - 3.7938*dm*dm + 1.4618*dm + 1.8839;
        }
        // Decay for M > 2: match at M=2 then 1/M scaling
        double fb2 = transonicMultiplier(2.0);
        return fb2 * (2.0 / mach);
    }

    /**
     * Complete base Cd for coast phase (plume-off), referenced to FRONTAL area.
     * Handles boattail area reduction.
     */
    public static double cdBasePlumeOff(double mach, double cf_body,
                                         RomGeometryParameters g) {
        double loOverD = (g.boattailLength > 0)
                ? g.boattailLength / g.maxDiameter : 0.0;
        double cd_base_area = cdBaseSubsonic(cf_body, loOverD);
        double fb = transonicMultiplier(mach);
        cd_base_area *= fb;

        // Scale from base area to frontal reference area
        double baseD = (g.boattailBaseDiameter > 0) ? g.boattailBaseDiameter : g.maxDiameter;
        double baseArea = Math.PI * (baseD / 2.0) * (baseD / 2.0);
        return cd_base_area * (baseArea / g.referenceArea);
    }

    /**
     * Powered flight (plume-on) base drag.
     * Motor exit subtracts from effective base area: A_eff = A_base - A_exit
     * Residual drag from annular base region only.
     */
    public static double cdBasePlumeOn(double mach, double cf_body,
                                        RomGeometryParameters g) {
        if (g.motorExitArea <= 0) return cdBasePlumeOff(mach, cf_body, g);
        double baseD = (g.boattailBaseDiameter > 0) ? g.boattailBaseDiameter : g.maxDiameter;
        double baseArea = Math.PI * (baseD / 2.0) * (baseD / 2.0);
        double effectiveRatio = Math.max(0.0,
                (baseArea - g.motorExitArea) / baseArea);
        return cdBasePlumeOff(mach, cf_body, g) * effectiveRatio;
    }
}
```

### 1.3 — `WaveDragModel.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/WaveDragModel.java`

```java
package info.openrocket.core.aerodynamics.rom;

public class WaveDragModel {

    /**
     * Drag divergence Mach number from Braeunig correlation.
     * M_DD = -0.0156*(L_N/d)^2 + 0.136*(L_N/d) + 0.6817
     * Valid for L_N/d < 0.6 (nose length to effective body diameter ratio).
     */
    public static double dragDivergenceMach(RomGeometryParameters g) {
        double ratio = g.noseLength / g.maxDiameter;
        if (ratio > 6.0) ratio = 6.0; // clamp to correlation range
        return -0.0156 * ratio * ratio + 0.136 * ratio + 0.6817;
    }

    /**
     * Critical Mach number: onset of local supersonic flow.
     * Approximate: M_cr ≈ M_DD - 0.10 for slender rockets.
     */
    public static double criticalMach(RomGeometryParameters g) {
        return dragDivergenceMach(g) - 0.10;
    }

    /**
     * Nose cone wave drag coefficient at supersonic speeds.
     * Uses nose-shape-specific correlations referenced to frontal area.
     *
     * Von Kármán ogive: lowest wave drag (Kármán-Moore integral result)
     * Conical: Cd_wave ≈ (θ_half)^2 * 4/γ for M >> 1 (θ in radians)
     * Others: interpolated empirical fits
     */
    public static double cdNoseWaveSupersonic(double mach, RomGeometryParameters g) {
        double lnOverD = g.noseLength / g.maxDiameter;
        switch (g.noseShape) {
            case VON_KARMAN:
                // Minimum wave drag: approaches Sears-Haack scaling
                // Cd_wave ≈ 0.083 / (lnOverD^2) at M=1.5, decays with M
                return 0.083 / (lnOverD * lnOverD) * searsHaackDecay(mach);
            case OGIVE:
                return 0.10 / (lnOverD * lnOverD) * searsHaackDecay(mach);
            case PARABOLIC:
                return 0.11 / (lnOverD * lnOverD) * searsHaackDecay(mach);
            case CONICAL: {
                double theta = Math.atan(0.5 / lnOverD); // half-angle
                return (4.0 / (GAMMA * mach * mach))
                       * Math.pow(theta, 2.0)
                       * (1.0 + 0.5 * (GAMMA + 1) * Math.pow(theta, 2.0));
            }
            case ELLIPSOID:
                return 0.13 / (lnOverD * lnOverD) * searsHaackDecay(mach);
            default:
                return 0.12 / (lnOverD * lnOverD) * searsHaackDecay(mach);
        }
    }

    /** Mach decay factor for wave drag: 1/sqrt(M^2-1) dependence. */
    private static double searsHaackDecay(double mach) {
        if (mach <= 1.0) return 1.0;
        return 1.0 / Math.sqrt(mach * mach - 1.0 + 0.01); // +0.01 prevents singularity at M=1
    }

    private static final double GAMMA = 1.4;

    /**
     * Fin wave drag at supersonic speeds using Ackeret thin-airfoil theory.
     * Cd_fin_wave = 4*(t/c)^2 / sqrt(M^2-1) per fin, summed and scaled.
     */
    public static double cdFinWaveSupersonic(double mach, RomGeometryParameters g) {
        if (mach <= 1.0) return 0.0;
        double tc = g.finThickness / ((g.finRootChord + g.finTipChord) / 2.0);
        double beta = Math.sqrt(mach * mach - 1.0);
        double cdPerFin = 4.0 * tc * tc / beta;
        // Scale fin wave drag to frontal reference area
        double finPlanform = 0.5 * (g.finRootChord + g.finTipChord) * g.finSpan;
        return cdPerFin * g.finCount * finPlanform / g.referenceArea;
    }
}
```

### 1.4 — `TransonicBlendingModel.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/TransonicBlendingModel.java`

This is the structural fix that OpenRocket currently lacks: a C¹ continuous blend
across the transonic boundaries so no trajectory integrator ever sees a discontinuous
`dCd/dM`.

```java
package info.openrocket.core.aerodynamics.rom;

public class TransonicBlendingModel {

    // Sigmoid sharpness — higher k = sharper boundary transition
    private static final double K = 30.0;

    /**
     * Subsonic sigmoid weight: 1 at low M, 0 at high M.
     * Centered at M1 = 0.80.
     */
    public static double sigmaSubsonic(double mach) {
        return 1.0 / (1.0 + Math.exp(K * (mach - 0.80)));
    }

    /**
     * Supersonic sigmoid weight: 0 at low M, 1 at high M.
     * Centered at M2 = 1.20.
     */
    public static double sigmaSupersonic(double mach) {
        return 1.0 / (1.0 + Math.exp(-K * (mach - 1.20)));
    }

    /** Transonic weight: complement of the other two. */
    public static double sigmaTransonic(double mach) {
        return 1.0 - sigmaSubsonic(mach) - sigmaSupersonic(mach);
    }

    /**
     * Blend three regime Cd values into a single smooth value.
     * cd_sub: subsonic physics (no wave drag)
     * cd_trans: transonic peak estimate (wave drag + base drag peak)
     * cd_sup: supersonic physics (wave drag + Ackeret fins)
     *
     * The result is C-infinity continuous — suitable for any ODE integrator.
     */
    public static double blend(double mach,
                               double cd_sub, double cd_trans, double cd_sup) {
        return sigmaSubsonic(mach)  * cd_sub
             + sigmaTransonic(mach) * cd_trans
             + sigmaSupersonic(mach)* cd_sup;
    }

    /**
     * Transonic peak Cd estimate.
     * Scales the subsonic value by the drag rise factor at peak (M≈1.0).
     * Drag rise factor ≈ 1.5–2.5× subsonic for typical slender rockets.
     * Uses nose-shape-specific empirical peak multipliers.
     */
    public static double transonicPeakCd(double cd_subsonic,
                                          RomGeometryParameters g) {
        double peakFactor;
        switch (g.noseShape) {
            case VON_KARMAN: peakFactor = 1.6; break;
            case OGIVE:      peakFactor = 1.8; break;
            case PARABOLIC:  peakFactor = 1.9; break;
            case CONICAL:    peakFactor = 2.2; break;
            default:         peakFactor = 2.0; break;
        }
        // Reduce peak factor for high fineness ratio (slender bodies have lower transonic rise)
        double lOverD = g.finessRatio;
        double finenessCorrection = Math.min(1.0, 10.0 / lOverD);
        return cd_subsonic * peakFactor * finenessCorrection;
    }
}
```

### 1.5 — `InducedDragModel.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/InducedDragModel.java`

```java
package info.openrocket.core.aerodynamics.rom;

public class InducedDragModel {

    /**
     * AoA-dependent drag increment.
     * OpenRocket scaling: Cd(α) ≈ Cd0 * (1 + k*(α/αref)^2)
     * where k and αref are empirical from Barrowman body lift data.
     *
     * For fin-dominated stability: uses simplified induced drag from
     * lift-induced mechanism: ΔCd = Cl^2 / (π * AR * e)
     * where AR is fin aspect ratio and e ≈ 0.9 span efficiency.
     *
     * @param alphaRad angle of attack in radians
     */
    public static double cdInduced(double alphaRad, double mach,
                                    RomGeometryParameters g) {
        if (alphaRad < 1e-6) return 0.0;
        // Fin aspect ratio (per fin panel)
        double chord_mean = (g.finRootChord + g.finTipChord) / 2.0;
        double ar = 2.0 * g.finSpan / chord_mean; // exposed semi-span, both sides
        double e = 0.9; // span efficiency

        // Normal force slope per fin: CNα ≈ 2π / (1 + 2/AR) per radian
        double cNa_fin = 2.0 * Math.PI / (1.0 + 2.0 / ar);
        double cl = cNa_fin * alphaRad * g.finCount
                   * (g.finSpan * chord_mean) / g.referenceArea;

        // Compressibility correction: Prandtl-Glauert below transonic
        if (mach < 0.8) cl /= Math.sqrt(1.0 - mach * mach);

        double cd_induced = cl * cl / (Math.PI * ar * e);

        // Add body AoA drag (sin^2 approximation for cross-flow)
        double cd_body_aoa = 0.1 * Math.sin(alphaRad) * Math.sin(alphaRad);

        return cd_induced + cd_body_aoa;
    }

    /**
     * Protuberance correction factor (rail buttons, camera mounts, etc.).
     * Applied as a multiplier: Cd_total *= protuberanceFactor().
     * Default Kf = 1.04 (4% increment, Barrowman standard).
     * Can be parameterized from user input in Phase 4.
     */
    public static double protuberanceFactor() {
        return 1.04;
    }
}
```

---

## Phase 2 — Geometry extraction from OpenRocket component tree

### 2.1 — Complete `RomGeometryParameters.fromRocket()`

Open `RomGeometryParameters.java` and implement the factory method.

Key implementation notes:

- Walk `FlightConfiguration.getAllComponents()` and dispatch on component type.
- Use `component.getLength()`, `component.getForeRadius()`, `component.getAftRadius()` for all axisymmetric bodies.
- For `NoseCone`: read `NoseCone.getType()` and map to `RomGeometryParameters.NoseShape`. Read `NoseCone.getLength()` and `NoseCone.getForeRadius()` (which is 0 for nose) and `NoseCone.getAftRadius()` (which is body radius).
- For `BodyTube`: accumulate `getLength()` and compute wetted area as `π * diameter * length`.
- For `Transition` (boattail): read `getForeRadius()` and `getAftRadius()`. If `getAftRadius() < getForeRadius()`, this is a boattail — record its length and aft diameter.
- For `FinSet` subclasses: read fin geometry. `TrapezoidalFinSet` exposes `getRootChord()`, `getTipChord()`, `getSpan()`, `getThickness()`, `getSweep()`. Accumulate across all fin sets.
- For surface roughness: read from `RocketComponent.getFinish()` — map `Finish` enum values to `k_s` in meters: `ROUGH` → 500e-6, `UNFINISHED` → 60e-6, `NORMAL` → 6.4e-6 (default paint), `SMOOTH` → 2e-6, `POLISHED` → 0.5e-6.
- Motor exit: walk `FlightConfiguration.getActiveMotors()`, read `MotorMount.getMotorOverhang()` and nozzle exit diameter from the motor configuration. If multiple motors, sum exit areas.

Implement `geometryHash()` by concatenating all double fields as formatted strings and computing SHA-256 using `java.security.MessageDigest`.

### 2.2 — Verify geometry extraction with a unit test

Create:
`core/src/test/java/info/openrocket/core/aerodynamics/rom/RomGeometryParametersTest.java`

Load the built-in "Estes Alpha" example rocket from OpenRocket's test resources. Assert:
- `bodyLength` is approximately 0.305 m
- `maxDiameter` is approximately 0.024 m
- `finessRatio` is approximately 12.7
- `finCount` is 3
- `noseShape` is `OGIVE`
- `geometryHash()` returns a 64-character hex string
- Calling `fromRocket()` twice on the same config returns identical geometry hashes

---

## Phase 3 — Grid evaluation engine

### 3.1 — `DragGridEvaluator.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/DragGridEvaluator.java`

This is the core of the prestep computation. It evaluates the physics model at every
grid point and returns a fully populated `DragSurface`.

```java
package info.openrocket.core.aerodynamics.rom;

import info.openrocket.core.models.atmosphere.AtmosphericConditions;

public class DragGridEvaluator {

    // Default grid resolution — balance accuracy vs build time
    // Full: ~10 000 evaluations, completes < 500 ms on any modern JVM
    public static final int N_MACH  = 60;   // 0.01 to 4.0
    public static final int N_RE    = 20;   // log10(1e4) to log10(1e8)
    public static final int N_ALPHA = 10;   // 0 to 15 degrees

    /**
     * Build the complete DragSurface for the given geometry.
     * Progress is reported via the listener (0.0 to 1.0).
     */
    public static DragSurface evaluate(RomGeometryParameters g,
                                        ProgressListener progress) {
        double[] machAxis  = buildMachAxis();
        double[] logReAxis = buildLogReAxis();
        double[] alphaAxis = buildAlphaAxis();

        double[][][] cdOff = new double[N_MACH][N_RE][N_ALPHA];
        double[][][] cdOn  = new double[N_MACH][N_RE][N_ALPHA];

        int total = N_MACH * N_RE * N_ALPHA;
        int done  = 0;

        for (int im = 0; im < N_MACH; im++) {
            double mach = machAxis[im];
            for (int ir = 0; ir < N_RE; ir++) {
                double re_L = Math.pow(10.0, logReAxis[ir]);
                for (int ia = 0; ia < N_ALPHA; ia++) {
                    double alphaRad = Math.toRadians(alphaAxis[ia]);

                    cdOff[im][ir][ia] = computeCdPlumeOff(mach, re_L, alphaRad, g);
                    cdOn[im][ir][ia]  = computeCdPlumeOn (mach, re_L, alphaRad, g);

                    done++;
                    if (progress != null && done % 50 == 0) {
                        progress.onProgress((double) done / total);
                    }
                }
            }
        }

        if (progress != null) progress.onProgress(1.0);
        return new DragSurface(machAxis, logReAxis, alphaAxis,
                               cdOff, cdOn, g.geometryHash(), 0.0);
    }

    // -------------------------------------------------------------------------
    // Core per-point evaluation
    // -------------------------------------------------------------------------

    static double computeCdPlumeOff(double mach, double re_L,
                                     double alphaRad, RomGeometryParameters g) {
        // 1. Skin friction
        double cd_friction = SkinFrictionModel.cdFriction(mach, re_L, g);

        // 2. Fin friction
        double cd_fin_friction = finFriction(mach, re_L, g);

        // 3. Base drag — compute regime-appropriate value
        double cf_body = SkinFrictionModel.cfWithRoughness(
                SkinFrictionModel.vanDriestII(
                        SkinFrictionModel.cfIncompressible(re_L, 5e5), mach, 1.0),
                g.bodyLength, g.surfaceRoughness);

        double cd_base_subsonic = BaseDragModel.cdBasePlumeOff(
                Math.min(mach, 0.59), cf_body, g);
        double cd_base_transonic_peak = BaseDragModel.cdBasePlumeOff(
                1.0, cf_body, g);

        // 4. Wave drag (supersonic)
        double cd_wave_nose = WaveDragModel.cdNoseWaveSupersonic(mach, g);
        double cd_wave_fins = WaveDragModel.cdFinWaveSupersonic(mach, g);

        // 5. Subsonic total (no wave drag)
        double cd_sub = cd_friction + cd_fin_friction + cd_base_subsonic;

        // 6. Supersonic total
        double cd_sup = cd_friction + cd_fin_friction
                      + BaseDragModel.cdBasePlumeOff(mach, cf_body, g)
                      + cd_wave_nose + cd_wave_fins;

        // 7. Transonic peak estimate
        double cd_trans = TransonicBlendingModel.transonicPeakCd(cd_sub, g)
                        + cd_base_transonic_peak;

        // 8. Smooth blend
        double cd_zero_aoa = TransonicBlendingModel.blend(mach, cd_sub, cd_trans, cd_sup);

        // 9. AoA increment + protuberances
        double cd_aoa = InducedDragModel.cdInduced(alphaRad, mach, g);
        double kf     = InducedDragModel.protuberanceFactor();

        return Math.max(0.001, (cd_zero_aoa + cd_aoa) * kf);
    }

    static double computeCdPlumeOn(double mach, double re_L,
                                    double alphaRad, RomGeometryParameters g) {
        // Same as plume-off but substitute plume-on base drag
        double cf_body = SkinFrictionModel.cfWithRoughness(
                SkinFrictionModel.vanDriestII(
                        SkinFrictionModel.cfIncompressible(re_L, 5e5), mach, 1.0),
                g.bodyLength, g.surfaceRoughness);

        double cd_friction    = SkinFrictionModel.cdFriction(mach, re_L, g);
        double cd_fin_friction = finFriction(mach, re_L, g);
        double cd_base_on     = BaseDragModel.cdBasePlumeOn(mach, cf_body, g);
        double cd_wave_nose   = WaveDragModel.cdNoseWaveSupersonic(mach, g);
        double cd_wave_fins   = WaveDragModel.cdFinWaveSupersonic(mach, g);

        double cd_sub  = cd_friction + cd_fin_friction + cd_base_on;
        double cd_sup  = cd_friction + cd_fin_friction + cd_base_on
                       + cd_wave_nose + cd_wave_fins;
        double cd_trans = TransonicBlendingModel.transonicPeakCd(cd_sub, g);

        double cd_zero_aoa = TransonicBlendingModel.blend(mach, cd_sub, cd_trans, cd_sup);
        double cd_aoa = InducedDragModel.cdInduced(alphaRad, mach, g);
        return Math.max(0.001, (cd_zero_aoa + cd_aoa) * InducedDragModel.protuberanceFactor());
    }

    // Fin friction contribution (both sides, all fins, referenced to frontal area)
    private static double finFriction(double mach, double re_L, RomGeometryParameters g) {
        if (g.finCount == 0) return 0.0;
        double tc = g.finThickness / ((g.finRootChord + g.finTipChord) / 2.0);
        double re_fin = re_L * g.finRootChord / g.bodyLength;
        double cf_fin = SkinFrictionModel.vanDriestII(
                SkinFrictionModel.cfIncompressible(re_fin, 5e5), mach, 1.0);
        // Fin form factor: (1 + 2*t/c)
        double ff_fin = 1.0 + 2.0 * tc;
        return cf_fin * ff_fin * g.finCount * g.finWettedArea / g.referenceArea;
    }

    // -------------------------------------------------------------------------
    // Axis construction
    // -------------------------------------------------------------------------

    static double[] buildMachAxis() {
        // Cluster points in transonic region for accuracy
        double[] axis = new double[N_MACH];
        // 0–0.7: uniform, 15 points
        // 0.7–1.3: dense, 20 points
        // 1.3–4.0: uniform, 25 points
        int i = 0;
        for (; i < 15; i++) axis[i] = 0.01 + i * (0.70 - 0.01) / 14.0;
        for (; i < 35; i++) axis[i] = 0.70 + (i - 15) * (1.30 - 0.70) / 19.0;
        for (; i < N_MACH; i++) axis[i] = 1.30 + (i - 35) * (4.00 - 1.30) / 24.0;
        return axis;
    }

    static double[] buildLogReAxis() {
        double[] axis = new double[N_RE];
        double lo = Math.log10(1e4), hi = Math.log10(1e8);
        for (int i = 0; i < N_RE; i++) axis[i] = lo + i * (hi - lo) / (N_RE - 1);
        return axis;
    }

    static double[] buildAlphaAxis() {
        double[] axis = new double[N_ALPHA];
        for (int i = 0; i < N_ALPHA; i++) axis[i] = i * 15.0 / (N_ALPHA - 1);
        return axis;
    }

    public interface ProgressListener {
        void onProgress(double fraction); // 0.0 to 1.0
    }
}
```

---

## Phase 4 — PCHIP interpolation

### 4.1 — `PchipInterpolator1D.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/PchipInterpolator1D.java`

Implement the Fritsch-Carlson (1980) monotone piecewise cubic Hermite algorithm.
This is the core numerical piece — it guarantees no spurious oscillations across the
transonic Cd peak.

```java
package info.openrocket.core.aerodynamics.rom;

/**
 * Fritsch-Carlson monotone piecewise cubic Hermite interpolant.
 * Guarantees monotonicity preservation on each interval.
 * C¹ continuous (not C², unlike standard cubic splines).
 *
 * Reference: Fritsch & Carlson, SIAM J. Numer. Anal. 17(2), 1980, pp 238–246.
 */
public class PchipInterpolator1D {

    private final double[] x;   // knot positions, strictly increasing
    private final double[] y;   // function values at knots
    private final double[] d;   // derivative estimates at knots (Fritsch-Carlson)

    public PchipInterpolator1D(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("Need at least 2 matching points");
        }
        this.x = x.clone();
        this.y = y.clone();
        this.d = computeDerivatives(x, y);
    }

    /** Evaluate the interpolant at point xi (clamps to boundary outside range). */
    public double evaluate(double xi) {
        int n = x.length;
        // Clamp to range
        if (xi <= x[0])   return y[0];
        if (xi >= x[n-1]) return y[n-1];

        // Binary search for interval
        int lo = 0, hi = n - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (x[mid] <= xi) lo = mid; else hi = mid - 1;
        }

        // Hermite basis on interval [x[lo], x[lo+1]]
        double h  = x[lo+1] - x[lo];
        double t  = (xi - x[lo]) / h;
        double t2 = t * t, t3 = t2 * t;

        double h00 =  2*t3 - 3*t2 + 1;
        double h10 =    t3 - 2*t2 + t;
        double h01 = -2*t3 + 3*t2;
        double h11 =    t3 -   t2;

        return h00 * y[lo] + h10 * h * d[lo]
             + h01 * y[lo+1] + h11 * h * d[lo+1];
    }

    private static double[] computeDerivatives(double[] x, double[] y) {
        int n = x.length;
        double[] d = new double[n];
        double[] delta = new double[n-1]; // secant slopes
        double[] h     = new double[n-1]; // interval widths

        for (int i = 0; i < n-1; i++) {
            h[i]     = x[i+1] - x[i];
            delta[i] = (y[i+1] - y[i]) / h[i];
        }

        // Endpoint derivatives (one-sided)
        d[0]   = endpointDerivative(delta[0], delta[1]);
        d[n-1] = endpointDerivative(delta[n-2], delta[n-3 < 0 ? n-2 : n-3]);

        // Interior derivatives using Fritsch-Carlson weighted harmonic mean
        for (int i = 1; i < n-1; i++) {
            if (delta[i-1] * delta[i] <= 0) {
                d[i] = 0.0; // monotone at local extremum
            } else {
                double w1 = 2*h[i] + h[i-1];
                double w2 = h[i] + 2*h[i-1];
                d[i] = (w1 + w2) / (w1/delta[i-1] + w2/delta[i]);
            }
        }

        // Enforce monotonicity (Fritsch-Carlson algorithm step 5)
        for (int i = 0; i < n-1; i++) {
            if (Math.abs(delta[i]) < 1e-14) {
                d[i] = d[i+1] = 0.0;
                continue;
            }
            double alpha = d[i]   / delta[i];
            double beta  = d[i+1] / delta[i];
            double r = Math.sqrt(alpha*alpha + beta*beta);
            if (r > 3.0) {
                double tau = 3.0 / r;
                d[i]   = tau * alpha * delta[i];
                d[i+1] = tau * beta  * delta[i];
            }
        }
        return d;
    }

    private static double endpointDerivative(double d1, double d2) {
        double d = 1.5*d1 - 0.5*d2;
        if (d1 * d <= 0) return 0.0;
        if (d1 * d2 <= 0 && Math.abs(d) > 3*Math.abs(d1)) return 3*d1;
        return d;
    }
}
```

### 4.2 — `DragSurfaceInterpolator.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/DragSurfaceInterpolator.java`

Wraps the 3D surface with fast trilinear-with-PCHIP query. The query cost is
dominated by binary search (O(log n)) and is well under 1 μs.

```java
package info.openrocket.core.aerodynamics.rom;

public class DragSurfaceInterpolator {

    private final DragSurface surface;

    // Precomputed 1D PCHIP slices along the Mach axis at every (Re, α) point
    // Shape: [N_RE][N_ALPHA] — each entry is a PchipInterpolator1D over Mach
    private final PchipInterpolator1D[][] slicesOff;
    private final PchipInterpolator1D[][] slicesOn;

    public DragSurfaceInterpolator(DragSurface surface) {
        this.surface = surface;
        int nM = surface.machAxis.length;
        int nR = surface.logReAxis.length;
        int nA = surface.alphaAxis.length;

        slicesOff = new PchipInterpolator1D[nR][nA];
        slicesOn  = new PchipInterpolator1D[nR][nA];

        for (int ir = 0; ir < nR; ir++) {
            for (int ia = 0; ia < nA; ia++) {
                double[] cdOff = new double[nM];
                double[] cdOn  = new double[nM];
                for (int im = 0; im < nM; im++) {
                    cdOff[im] = surface.cdPlumeOff[im][ir][ia];
                    cdOn[im]  = surface.cdPlumeOn[im][ir][ia];
                }
                slicesOff[ir][ia] = new PchipInterpolator1D(surface.machAxis, cdOff);
                slicesOn[ir][ia]  = new PchipInterpolator1D(surface.machAxis, cdOn);
            }
        }
    }

    /**
     * Query Cd for coast phase (plume-off).
     *
     * @param mach      freestream Mach number
     * @param re_L      body-length Reynolds number
     * @param alphaDeg  angle of attack in degrees
     */
    public double queryCdPlumeOff(double mach, double re_L, double alphaDeg) {
        return query(mach, re_L, alphaDeg, slicesOff);
    }

    /**
     * Query Cd for powered phase (plume-on).
     */
    public double queryCdPlumeOn(double mach, double re_L, double alphaDeg) {
        return query(mach, re_L, alphaDeg, slicesOn);
    }

    private double query(double mach, double re_L, double alphaDeg,
                         PchipInterpolator1D[][] slices) {
        double logRe = Math.log10(Math.max(re_L, 1e4));
        alphaDeg = Math.abs(alphaDeg); // symmetric in AoA

        // Find bounding Re indices
        int ir0 = binarySearchFloor(surface.logReAxis, logRe);
        int ia0 = binarySearchFloor(surface.alphaAxis, alphaDeg);

        // Interpolate Cd(M) at the four surrounding (Re, α) corners
        double cd00 = slices[ir0  ][ia0  ].evaluate(mach);
        double cd10 = slices[ir0+1][ia0  ].evaluate(mach);
        double cd01 = slices[ir0  ][ia0+1].evaluate(mach);
        double cd11 = slices[ir0+1][ia0+1].evaluate(mach);

        // Bilinear in Re and α
        double tr = fractional(surface.logReAxis, ir0, logRe);
        double ta = fractional(surface.alphaAxis,  ia0, alphaDeg);

        double cd = (1-tr)*(1-ta)*cd00 + tr*(1-ta)*cd10
                  + (1-tr)*  ta *cd01 + tr*  ta *cd11;
        return Math.max(0.001, cd);
    }

    // Returns index i such that axis[i] <= value < axis[i+1], clamped
    private static int binarySearchFloor(double[] axis, double value) {
        int lo = 0, hi = axis.length - 2;
        if (value <= axis[0])         return 0;
        if (value >= axis[axis.length-1]) return axis.length - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (axis[mid] <= value) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    private static double fractional(double[] axis, int i, double value) {
        double denom = axis[i+1] - axis[i];
        if (denom < 1e-14) return 0.0;
        return Math.max(0.0, Math.min(1.0, (value - axis[i]) / denom));
    }
}
```

---

## Phase 5 — Aerodynamic calculator integration

### 5.1 — `RomAerodynamicCalculator.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/RomAerodynamicCalculator.java`

This class implements `AerodynamicCalculator` using the precomputed surface. Study
`BarrowmanCalculator` carefully — match every method signature exactly.

Key implementation notes:

- Hold a `DragSurfaceInterpolator` as a field. If it is null (surface not yet computed),
  delegate to `BarrowmanCalculator` as a fallback.
- In `getAerodynamicForces()`: compute `M`, `Re`, `α` from `FlightConditions`. Determine
  whether the motor is burning (`conditions.getThrust() > 0`) to select plume-on or
  plume-off. Query the interpolator. Set `AerodynamicForces.CD` from the result.
- Reynolds number computation: `Re = ρ * V * L / μ` where `L` is body length, `μ` is
  dynamic viscosity from the atmospheric model. OpenRocket provides all of these through
  `FlightConditions.getAtmosphericConditions()`.
- Angle of attack: `FlightConditions.getAOA()` returns AoA in radians. Convert to degrees
  for the surface query.
- Plume-off/on transition: use an exponential smoothing at burnout rather than a hard step.
  Maintain a `plumeDecayState` field that decays from 1.0 (plume-on) toward 0.0 (plume-off)
  with time constant τ ≈ 0.3 s after burnout is detected.
- All other coefficients (`CN`, `Cm`, `CP` location): delegate to `BarrowmanCalculator` for
  these — the ROM only improves drag, not stability derivatives. Construct a private
  `BarrowmanCalculator` field for this delegation.

```java
// Skeleton — fill in per the notes above
public class RomAerodynamicCalculator implements AerodynamicCalculator {

    private final BarrowmanCalculator barrowman;
    private DragSurfaceInterpolator interpolator;  // null until prestep runs
    private double plumeDecayState = 0.0;          // 0=off, 1=on

    public RomAerodynamicCalculator() {
        this.barrowman = new BarrowmanCalculator();
    }

    public void installSurface(DragSurface surface) {
        this.interpolator = new DragSurfaceInterpolator(surface);
    }

    public boolean hasSurface() {
        return interpolator != null;
    }

    @Override
    public AerodynamicForces getAerodynamicForces(FlightConfiguration config,
            FlightConditions conditions, WarningSet warnings) {

        AerodynamicForces forces = barrowman.getAerodynamicForces(
                config, conditions, warnings);

        if (interpolator == null) return forces; // fallback

        double mach  = conditions.getMach();
        double vel   = conditions.getVelocity();
        AtmosphericConditions atm = conditions.getAtmosphericConditions();
        double rho   = atm.getDensity();
        double mu    = atm.getDynamicViscosity(); // Pa·s
        double L     = /* g.bodyLength — store from last prestep */;
        double re_L  = rho * vel * L / mu;
        double alphaDeg = Math.toDegrees(conditions.getAOA());

        // Determine plume state
        boolean burning = (conditions.getThrust() > 0);
        // (plume decay logic here — update plumeDecayState per timestep)

        double cdPlumeOff = interpolator.queryCdPlumeOff(mach, re_L, alphaDeg);
        double cdPlumeOn  = interpolator.queryCdPlumeOn(mach, re_L, alphaDeg);
        double cdBlended  = cdPlumeOff + plumeDecayState * (cdPlumeOn - cdPlumeOff);

        forces.setCD(cdBlended);
        return forces;
    }

    // Delegate all other interface methods to barrowman
    @Override
    public double getCP(...) { return barrowman.getCP(...); }
    // etc.
}
```

### 5.2 — Wire into `SimulationConditions`

In `SimulationConditions.java`, add a field:
```java
private RomAerodynamicCalculator romCalculator;
```

Add getter/setter. In `SimulationEngine` (wherever `AerodynamicCalculator` is resolved),
prefer `romCalculator` when it `hasSurface()`, otherwise use the default `BarrowmanCalculator`.

---

## Phase 6 — Prestep UI tab

### 6.1 — `RomPrestepPanel.java`

Create at:
`swing/src/main/java/info/openrocket/swing/gui/simulation/RomPrestepPanel.java`

This is a `JPanel` that plugs into OpenRocket's existing simulation configuration dialog.
Study how existing tabs (`SimulationOptionsPanel`, `FlightDataTab`) are added to the
`SimulationEditDialog` to understand the extension point.

The panel contains the following UI elements, laid out top to bottom:

**Status row**
- Label: "Aerodynamic surface" — value: "Not computed" (gray) or "Ready — built MM/DD HH:MM" (green)
- Label: "Geometry" — value: SHA-256 prefix (8 chars) of current geometry hash
- Warning label (red, hidden by default): "Geometry has changed — rebuild required"

**Parameter display (read-only, populated from `RomGeometryParameters`)**
- Body length, max diameter, fineness ratio
- Nose shape, nose length
- Fin count, fin span, fin thickness
- Surface roughness (with dropdown to override: Polished / Smooth / Paint / Unfinished / Rough)
- Protuberance factor (spinner, default 1.04, range 1.00–1.20, step 0.01)

**Build controls**
- "Build aerodynamic surface" button (runs prestep)
- Progress bar (0–100%, hidden when idle)
- Estimated time label: "~0.3 s" (update from last build time)

**Cd(M) preview chart**
- Line chart showing Cd vs Mach at Re = 1e6 (representative)
- Two series: plume-on (dashed) and plume-off (solid)
- X-axis: Mach 0 to 4, Y-axis: Cd 0 to 1.5
- Vertical dashed lines at M = 0.8 and M = 1.2 marking transonic band
- Use existing OpenRocket chart utilities or `XChartPanel` / plain `Graphics2D`

**Validation section (collapsed by default)**
- Text area: paste CFD Cd values as `M,Re,Cd` CSV rows
- "Compare" button: computes ROM vs input error, displays table
- Table columns: M, Re, Cd_CFD, Cd_ROM, % error
- Summary label: "Mean absolute error: X.X%"

### 6.2 — Build action

When the "Build aerodynamic surface" button is pressed:

1. Extract `RomGeometryParameters` from the current rocket design.
2. Check if the geometry hash matches an already-installed surface — if yes, skip and show "Already current".
3. Disable button, show progress bar.
4. Run `DragGridEvaluator.evaluate()` on a background `SwingWorker` thread, reporting progress to the progress bar via `publish()`.
5. On completion: call `simConditions.getRomCalculator().installSurface(surface)`.
6. Update status label to green "Ready".
7. Redraw the Cd(M) preview chart.
8. Re-enable button.

### 6.3 — Register the tab

In `SimulationEditDialog.java` (or wherever simulation tabs are assembled), add:

```java
RomPrestepPanel romPanel = new RomPrestepPanel(simulationConditions);
tabbedPane.insertTab("Aerodynamics", null, romPanel, 
        "Precompute ROM drag surface", 1); // insert after "Options", before "Plot"
```

---

## Phase 7 — Serialization

The computed `DragSurface` must survive file save/load so users don't have to rebuild
on every OpenRocket session. OpenRocket uses a custom XML file format (`.ork`).

### 7.1 — `DragSurfaceSerializer.java`

Create at:
`core/src/main/java/info/openrocket/core/aerodynamics/rom/DragSurfaceSerializer.java`

Serialize the surface as a Base64-encoded gzip-compressed binary block embedded
in the `.ork` XML. Format:

```
<romdragsurface version="1"
                geometryhash="[64 hex chars]"
                builttimestamp="[epoch ms]"
                looRmse="[double]">
    [Base64(gzip(binary blob))]
</romdragsurface>
```

Binary blob layout (little-endian doubles):
```
int32: N_MACH
int32: N_RE
int32: N_ALPHA
double[N_MACH]:  machAxis
double[N_RE]:    logReAxis
double[N_ALPHA]: alphaAxis
double[N_MACH * N_RE * N_ALPHA]: cdPlumeOff (M-major order)
double[N_MACH * N_RE * N_ALPHA]: cdPlumeOn
```

This produces a file size of approximately:
`(60*20*10)*2 * 8 bytes = ~192 KB` uncompressed, ~40–60 KB after gzip.

### 7.2 — Hook into OpenRocket file I/O

OpenRocket's XML file handler is in `core/src/main/java/info/openrocket/core/file/`.
Find the `RocketSaver` and `RocketLoader` classes. Add a save hook that, if a
`DragSurface` is installed on `SimulationConditions`, writes the
`<romdragsurface>` block. Add a load hook that reads it back and re-installs it,
checking the geometry hash matches the loaded rocket geometry.

---

## Phase 8 — Unit tests

Create the following test classes. All tests must pass before integration.

### 8.1 — Physics model tests

`SkinFrictionModelTest.java`
- `cfIncompressible(1e7, 5e5)` ≈ 0.00285 ± 0.0002 (Schlichting table reference)
- `cfIncompressible(1e4, 5e5)` returns Blasius value 1.328/sqrt(1e4) = 0.01328
- `vanDriestII(0.003, 2.0, 1.0)` < 0.003 (compressibility reduces Cf)
- `cdFriction(0.5, 1e6, g)` is between 0.01 and 0.10 for a standard HPR geometry

`BaseDragModelTest.java`
- `transonicMultiplier(0.6)` == 1.0
- `transonicMultiplier(1.0)` ≈ 1.88 ± 0.1
- `cdBasePlumeOff(0.3, 0.003, g)` > `cdBasePlumeOn(0.3, 0.003, g)` (plume-on reduces base drag)
- `cdBasePlumeOn(0.3, 0.003, g)` >= 0 (never negative)

`TransonicBlendingModelTest.java`
- `sigmaSubsonic(0.0)` ≈ 1.0
- `sigmaSupersonic(0.0)` ≈ 0.0
- `sigmaSubsonic(M) + sigmaTransonic(M) + sigmaSupersonic(M)` == 1.0 for all M
- `blend()` is monotonically non-decreasing from M=0.5 to M=1.0 for a typical geometry

`PchipInterpolator1DTest.java`
- Exact reproduction at knot points
- Monotone on [0,1] with data [0, 0.5, 0.8, 1.0]
- No overshoot on step-like input (simulate transonic Cd rise)
- `evaluate()` clamps correctly below first and above last knot

### 8.2 — Grid evaluator test

`DragGridEvaluatorTest.java`
- `computeCdPlumeOff(0.3, 1e6, 0, g)` returns value in [0.15, 0.70] for a typical HPR
- `computeCdPlumeOff(2.0, 1e6, 0, g)` < `computeCdPlumeOff(0.9, 1e6, 0, g)` (Cd drops past transonic peak)
- `computeCdPlumeOn` < `computeCdPlumeOff` at same conditions (plume reduces drag)
- Full `evaluate()` completes in under 2 000 ms (performance gate)

### 8.3 — Interpolator accuracy test

`DragSurfaceInterpolatorTest.java`
- Build a `DragSurface` from `DragGridEvaluator`
- Query interpolator at every grid point — error vs direct physics model < 0.5%
- Query at midpoints between grid points — error < 2%
- Query outside grid bounds — clamps gracefully, no exception

---

## Phase 9 — Integration smoke test

After all phases are complete, run the following manual verification:

1. Open OpenRocket with a standard 4" HPR design (Estes style: ogive nose, 3 fins).
2. Navigate to the simulation configuration dialog. Verify "Aerodynamics" tab appears.
3. Click "Build aerodynamic surface". Verify progress bar advances and completes in under 2 seconds.
4. Verify the Cd(M) preview chart shows:
   - Subsonic plateau Cd ≈ 0.3–0.55
   - Transonic hump peaking near M = 1.0–1.1
   - Supersonic decay toward Cd ≈ 0.2–0.35 at M = 3
   - Plume-on curve lower than plume-off in base drag region
5. Run a simulation. Verify it completes without exception and reports an apogee altitude.
6. Disable the ROM (set interpolator to null or add a toggle), run the same simulation with Barrowman. Compare apogee values — expect ROM to give a lower (higher drag) apogee, especially for fast rockets that transit the transonic regime.
7. Modify the rocket geometry (change fin span). Verify the status label changes to "Geometry has changed — rebuild required".
8. Save the `.ork` file. Close OpenRocket. Reopen. Verify the surface is restored and the status label shows "Ready".

---

## Appendix A — CFD validation (optional, not required for deployment)

If you have access to OpenFOAM, use the following spot-check procedure to quantify
the ROM's accuracy relative to RANS CFD for a specific rocket geometry.

1. Export rocket geometry as an `(x, r)` profile CSV from `RomGeometryParameters`.
2. Generate a 2D axisymmetric OpenFOAM case using `rhoCentralFoam` with `k-ω SST`.
3. Run cases at 10–15 points: three Mach values (0.5, 0.9, 1.5) × three Re values (1e5, 1e6, 1e7) plus supplementary transonic points (M = 0.8, 1.0, 1.2 at Re = 1e6).
4. Extract Cd from each converged case via `postProcess -func forceCoeffs`.
5. Paste the `M,Re,Cd` pairs into the Validation section of the prestep tab.
6. Click "Compare". Expected results: subsonic error < 8%, transonic error < 20%, supersonic error < 10%.
7. If a systematic offset appears in a particular regime, apply a scalar correction to `DragGridEvaluator.computeCdPlumeOff()` for that Mach band.

---

## Appendix B — Known accuracy limitations

Document these in the user-facing help text of the prestep tab:

- **Base drag (30–60% uncertainty):** All semi-empirical base drag models, including
  Braeunig, have large scatter relative to flight data. This is the dominant remaining
  error source after all other improvements.
- **Transitional Reynolds numbers (Re < 5×10⁵):** Standard turbulent correlations
  overestimate skin friction in the laminar/transitional regime. The transition model
  in `SkinFrictionModel` partially corrects this but is still approximate.
- **Transonic regime (M 0.8–1.2):** The transonic peak Cd estimate uses empirical
  shape-specific multipliers. Without geometry-specific wind tunnel data, uncertainty
  is ±15–25% in this region.
- **Non-zero AoA above 12°:** The induced drag model degrades above 12° AoA. The
  surface clamps at `alphaAxis.max` — a warning should be displayed if the trajectory
  exceeds this.
- **Protuberance factor (default 1.04):** A blanket 4% increment. Actual increment
  depends on specific protuberance count and size. Encourage users to increase this
  to 1.08–1.12 for rockets with multiple rail buttons and camera mounts.
 