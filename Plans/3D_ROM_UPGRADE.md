# ROM 3D Extension — OpenRocket Implementation Plan

## Purpose

This document is an executable specification for an AI coding agent extending the
existing ROM aerodynamic prestep (`ROM_OpenRocket_Implementation_Plan.md`) from a 3D
semi-empirical surface `Cd(M, Re, α)` to a 4D surface covering
`(M, Re, α, β)` with five output coefficients: `Cd_total`, `Cd_body`, `ΔCd_fin`,
`CN` (diagnostic), and `Cm` (diagnostic).

**This document is an extension, not a replacement.** Every class from the original plan
remains in place. The agent reads the original plan in full before writing a single line
of new code, then implements only what is specified here.

**Approach:** Pure semi-empirical physics. No Gaussian Process, no training data,
no external libraries beyond what the original plan already uses.

**No CFD is required to build the surface.** CFD data can be pasted into the
extended validation panel to benchmark ROM accuracy but is never a build-time input.

---

## Architecture: Two-Module Structure

The extension introduces a clean architectural split the original plan did not have.

```
rom-core/          ← new Maven module, zero OpenRocket imports
  pom.xml
  src/main/java/info/openrocket/core/aerodynamics/rom/core/
    geometry/
      RomGeometryInput.java        ← plain geometry record (replaces RomGeometryParameters dependency)
    physics/
      SkinFrictionModel.java       ← moved from original (same code, new package)
      BaseDragModel.java           ← moved
      WaveDragModel.java           ← moved
      TransonicBlendingModel.java  ← moved
      InducedDragModel.java        ← moved
      FinDragModel.java            ← new (Phase 2)
      SideslipModel.java           ← new (Phase 2)
      NormalForceModel.java        ← new (Phase 3)
      PitchingMomentModel.java     ← new (Phase 3)
    surface/
      AeroSurface4D.java           ← new primary data structure (Phase 1)
      AeroSurface4DInterpolator.java ← new (Phase 4)
      PchipInterpolator1D.java     ← moved from original (same code, new package)
    eval/
      AeroGridEvaluator4D.java     ← new grid builder (Phase 3)
    io/
      AeroSurfaceSerializer.java   ← new serializer for AeroSurface4D (Phase 6)
      CsvExporter.java             ← export surface as flat CSV for external tools (Phase 6)

rom-openrocket/    ← existing OpenRocket core module (or a new adapter module)
  pom.xml          ← adds rom-core as a dependency
  src/main/java/info/openrocket/core/aerodynamics/rom/
    RomGeometryParameters.java     ← existing (unchanged)
    DragSurface.java               ← existing (unchanged, still used for 3D mode)
    DragSurfaceInterpolator.java   ← existing (unchanged)
    DragGridEvaluator.java         ← existing (unchanged)
    ... all other original classes unchanged ...
    adapter/
      GeometryAdapter.java         ← new: converts RomGeometryParameters → RomGeometryInput
      SurfaceAdapter.java          ← new: wraps AeroSurface4D for RomAerodynamicCalculator
    RomAerodynamicCalculator.java  ← existing (minimally extended, Phase 5)
  src/main/java/info/openrocket/swing/gui/simulation/
    RomPrestepPanel.java           ← existing (extended with 3D mode toggle, Phase 5)
```

### Module boundary rule (enforced by Maven)

`rom-core` must have **zero imports** from `info.openrocket.*`. The agent must verify
this by checking that `rom-core/pom.xml` has no OpenRocket dependencies. Any geometry
information the core needs is passed in via `RomGeometryInput`, which is a plain Java
record with no framework dependencies.

### Maven configuration

**`rom-core/pom.xml`** — no OpenRocket dependency, no EJML, no Gson:
```xml
<groupId>info.openrocket</groupId>
<artifactId>rom-core</artifactId>
<version>${project.version}</version>
<!-- No dependencies beyond Java 11 standard library -->
```

**In the OpenRocket core module `pom.xml`**, add:
```xml
<dependency>
  <groupId>info.openrocket</groupId>
  <artifactId>rom-core</artifactId>
  <version>${project.version}</version>
</dependency>
```

---

## Coefficient Definitions and Runtime Usage

Before writing any code, the agent must understand exactly how each of the five output
coefficients is used at runtime. This determines which grids need to be stored and which
can be computed on the fly.

| Coefficient | Grid stored? | Runtime use | Reference |
|-------------|-------------|-------------|-----------|
| `Cd_total` | Yes — primary output | Drives `forces.setCD()` in trajectory integrator | πd²/4 frontal area |
| `Cd_body` | Yes — for decomposition | Logged as `FlightDataType.CD_BODY` diagnostic variable | same |
| `ΔCd_fin` | Derived at query time: `Cd_total − Cd_body` | Logged as `FlightDataType.CD_FIN` diagnostic variable | same |
| `CN` | Yes — diagnostic only | **Not** fed into trajectory forces; logged as `FlightDataType.CN_ROM`; Barrowman still drives actual CN | πd²/4 frontal area |
| `Cm` | Yes — diagnostic only | **Not** fed into trajectory forces; logged as `FlightDataType.CM_ROM`; Barrowman still drives actual Cm | Nose tip reference, body length normalisation |

**CN and Cm do not replace Barrowman.** They are stored in the surface so the user can
compare semi-empirical ROM predictions against Barrowman values in the validation panel.
The trajectory integrator's actual normal force and pitching moment come exclusively from
the existing `BarrowmanCalculator` delegation, unchanged from the original plan.

The `ΔCd_fin` decomposition is derived at query time (subtract, not stored separately
as a grid), which keeps the serialized file size manageable and avoids grid duplication.

---

## Orientation: what already exists and must not be touched

Read each of these in full before writing any new code:

```
Original plan classes (all in info.openrocket.core.aerodynamics.rom):
  DragSurface.java                  ← 3D grid — do not modify
  RomGeometryParameters.java        ← geometry record — fromRocket() is partially done
  SkinFrictionModel.java            ← do not modify (will be mirrored in rom-core)
  BaseDragModel.java                ← do not modify
  WaveDragModel.java                ← do not modify
  TransonicBlendingModel.java       ← do not modify
  InducedDragModel.java             ← do not modify
  DragGridEvaluator.java            ← do not modify
  PchipInterpolator1D.java          ← do not modify
  DragSurfaceInterpolator.java      ← do not modify
  RomAerodynamicCalculator.java     ← extend minimally (Phase 5 only)
  RomPrestepPanel.java              ← extend minimally (Phase 5 only)
  DragSurfaceSerializer.java        ← do not modify
```

**On `RomGeometryParameters.fromRocket()`:** The body geometry extraction is implemented
and working. The fin extraction (`finRootChord`, `finTipChord`, `finSpan`,
`finThickness`, `finSweepAngle`, `finWettedArea`) is incomplete — these fields exist but
are not correctly populated from the component tree. The agent must complete fin
extraction before any Phase 2 physics code is written. See Phase 0.

---

## Phase 0 — Complete fin geometry extraction (prerequisite)

**Do not start Phase 1 until the test in Phase 0.2 passes.**

### 0.1 — Complete `RomGeometryParameters.fromRocket()` fin section

Open `RomGeometryParameters.java`. The body geometry block is already implemented.
Find the section handling `FinSet` subclasses and replace the incomplete logic with
the following. Leave all body geometry extraction code exactly as it is.

```java
// Inside the existing component-tree walk, in the instanceof FinSet branch:
else if (c instanceof FinSet) {
    FinSet fs = (FinSet) c;
    finCount += fs.getFinCount();
    finFound  = true;

    if (fs instanceof TrapezoidalFinSet) {
        TrapezoidalFinSet tfs = (TrapezoidalFinSet) fs;
        // Take the first fin set found; accumulate count only for additional sets
        if (!finFound || finRoot == 0.0) {
            finRoot  = tfs.getRootChord();
            finTip   = tfs.getTipChord();
            finSpan  = tfs.getSpan();
            finThick = tfs.getThickness();
            finSweep = tfs.getSweepAngle(); // radians, leading-edge sweep from perpendicular
        }
    } else {
        // Generic FinSet fallback (EllipticalFinSet, FreeformFinSet, etc.)
        if (!finFound || finRoot == 0.0) {
            finRoot  = fs.getLength();
            finTip   = 0.0;           // treat as triangular
            finSpan  = fs.getSpan();
            finThick = 0.003;         // 3 mm default
            finSweep = 0.0;
        }
    }
    // Wetted area: both sides, one fin panel, using mean chord
    double cMean = (finRoot + finTip) / 2.0;
    finWetArea += cMean * finSpan * 2.0 * fs.getFinCount();
}
```

After the walk, ensure these lines exist to populate the record fields:
```java
g.finCount      = finCount;
g.finRootChord  = finRoot;
g.finTipChord   = finTip;
g.finSpan       = finSpan;
g.finThickness  = finThick;
g.finSweepAngle = finSweep;
g.finWettedArea = finWetArea;
```

### 0.2 — Unit test: verify fin extraction

Add the following assertions to the existing `RomGeometryParametersTest.java`.
Use the same Estes Alpha test rocket already loaded in that test class.

```
finCount      == 3
finSpan        > 0.0
finRootChord   > 0.0
finTipChord   >= 0.0
finThickness   > 0.0
finWettedArea  > 0.0

// A config with zero fins produces safe zero values:
finCount == 0 → finSpan == 0.0, finWettedArea == 0.0   (no NullPointerException)

// Geometry hash changes when fin span changes:
RomGeometryParameters g1 = fromRocket(configWithFins);
// Manually set g1.finSpan += 0.01; — or use a second rocket with different fin span
// assert !g1.geometryHash().equals(g2.geometryHash())
```

---

## Phase 1 — Core data structure: `AeroSurface4D`

Create in `rom-core`:
`rom-core/src/main/java/info/openrocket/core/aerodynamics/rom/core/surface/AeroSurface4D.java`

This is the primary data structure of the extension. It holds four 4D grids —
two for Cd (plume-off and plume-on), one for CN, one for Cm — plus their axes.
There is no separate `Cd_body` or `ΔCd_fin` grid; those are derived at query time.

```java
package info.openrocket.core.aerodynamics.rom.core.surface;

/**
 * Precomputed 4D aerodynamic coefficient surface.
 *
 * Stores Cd_total (two plume states), CN (diagnostic), and Cm (diagnostic)
 * over the parameter space (M, Re, α, β).
 *
 * All axes are strictly monotonically increasing double arrays.
 * All Cd values are referenced to maximum cross-sectional area πd²/4.
 * CN is referenced to the same area.
 * Cm is referenced to πd²/4 × body length (nose-tip moment reference).
 *
 * CN and Cm are diagnostic only — they are stored for validation display
 * and are NOT used by the trajectory integrator.
 */
public final class AeroSurface4D {

    // ── Axes ─────────────────────────────────────────────────────────────────

    /** Freestream Mach number axis. Strictly increasing. */
    public final double[] machAxis;

    /** log₁₀(Reynolds number) axis. Strictly increasing. */
    public final double[] logReAxis;

    /** Angle of attack axis, degrees. Strictly increasing, starts at 0. */
    public final double[] alphaAxis;

    /**
     * Sideslip angle axis, degrees. Strictly increasing, starts at 0.
     * Maximum is 45° for 4-fin configurations, 60° for 3-fin.
     */
    public final double[] betaAxis;

    // ── Cd grids ─────────────────────────────────────────────────────────────

    /**
     * Total Cd, coast phase (plume-off).
     * Shape: [machAxis.length][logReAxis.length][alphaAxis.length][betaAxis.length]
     * Index order: [im][ir][ia][ib]
     */
    public final double[][][][] cdPlumeOff;

    /**
     * Total Cd, powered phase (plume-on).
     * Same shape as cdPlumeOff.
     */
    public final double[][][][] cdPlumeOn;

    /**
     * Body-alone Cd (fins excluded), coast phase.
     * Same shape. cdPlumeOff[im][ir][ia][ib] - cdBody[im][ir][ia][ib] = ΔCd_fin.
     */
    public final double[][][][] cdBody;

    // ── Diagnostic coefficient grids ─────────────────────────────────────────

    /**
     * Normal force coefficient CN. Diagnostic only.
     * Same shape. Stored at AoA = alphaAxis[ia], sideslip = betaAxis[ib].
     * NOT used by trajectory integrator — Barrowman drives actual CN.
     */
    public final double[][][][] CN;

    /**
     * Pitching moment coefficient Cm. Diagnostic only.
     * Moment reference: nose tip. Length normalisation: body length.
     * Same shape. NOT used by trajectory integrator.
     */
    public final double[][][][] Cm;

    // ── Metadata ─────────────────────────────────────────────────────────────

    /** SHA-256 of the geometry parameters used to build this surface. */
    public final String geometryHash;

    /** Epoch milliseconds at build time. */
    public final long buildTimestampMs;

    /** Number of fins (used by query layer to enforce β symmetry). */
    public final int finCount;

    // ── Constructor ──────────────────────────────────────────────────────────

    public AeroSurface4D(double[] machAxis, double[] logReAxis,
                         double[] alphaAxis, double[] betaAxis,
                         double[][][][] cdPlumeOff, double[][][][] cdPlumeOn,
                         double[][][][] cdBody,
                         double[][][][] CN, double[][][][] Cm,
                         String geometryHash, int finCount) {
        this.machAxis       = machAxis;
        this.logReAxis      = logReAxis;
        this.alphaAxis      = alphaAxis;
        this.betaAxis       = betaAxis;
        this.cdPlumeOff     = cdPlumeOff;
        this.cdPlumeOn      = cdPlumeOn;
        this.cdBody         = cdBody;
        this.CN             = CN;
        this.Cm             = Cm;
        this.geometryHash   = geometryHash;
        this.buildTimestampMs = System.currentTimeMillis();
        this.finCount       = finCount;
        validateAxes();
    }

    /** Derive ΔCd_fin at a specific grid point (not stored — computed on the fly). */
    public double getDeltaCdFin(int im, int ir, int ia, int ib) {
        return cdPlumeOff[im][ir][ia][ib] - cdBody[im][ir][ia][ib];
    }

    /** Sanity check: all axes strictly increasing, all grids non-null and positive. */
    private void validateAxes() {
        assertStrictlyIncreasing(machAxis,  "machAxis");
        assertStrictlyIncreasing(logReAxis, "logReAxis");
        assertStrictlyIncreasing(alphaAxis, "alphaAxis");
        assertStrictlyIncreasing(betaAxis,  "betaAxis");
        if (alphaAxis[0] != 0.0)
            throw new IllegalArgumentException("alphaAxis must start at 0.0");
        if (betaAxis[0] != 0.0)
            throw new IllegalArgumentException("betaAxis must start at 0.0");
    }

    private static void assertStrictlyIncreasing(double[] axis, String name) {
        for (int i = 1; i < axis.length; i++) {
            if (axis[i] <= axis[i - 1])
                throw new IllegalArgumentException(name + " is not strictly increasing at i=" + i);
        }
    }
}
```

---

## Phase 2 — New physics models (rom-core, no OpenRocket imports)

Create all classes in `rom-core`. They mirror the style of the existing physics model
classes exactly: pure-Java statics, one public method per computation, Javadoc on every
method, no side effects.

### 2.1 — Mirror existing physics classes into rom-core

The existing `SkinFrictionModel`, `BaseDragModel`, `WaveDragModel`,
`TransonicBlendingModel`, and `InducedDragModel` are in the OpenRocket module. The
rom-core physics models must replicate their computations without importing them.

Create the following in `rom-core/.../rom/core/physics/` with identical method
signatures and logic to their originals, substituting `RomGeometryParameters` with
`RomGeometryInput` (defined below):

```
SkinFrictionModel.java     ← copy logic from original, substitute RomGeometryInput
BaseDragModel.java         ← copy logic from original
WaveDragModel.java         ← copy logic from original
TransonicBlendingModel.java ← copy logic from original
InducedDragModel.java      ← copy logic from original
```

### 2.2 — `RomGeometryInput.java`

Create in `rom-core`:
`rom-core/src/main/java/info/openrocket/core/aerodynamics/rom/core/geometry/RomGeometryInput.java`

This is a plain Java record — no enums from OpenRocket, no framework types. It carries
exactly the fields that the physics models need.

```java
package info.openrocket.core.aerodynamics.rom.core.geometry;

/**
 * Geometry parameters required by the rom-core physics models.
 * All fields in SI units. No OpenRocket imports permitted in this class.
 */
public final class RomGeometryInput {

    // Body
    public final double bodyLength;          // m
    public final double maxDiameter;         // m
    public final double baseArea;            // m² = π(d/2)²
    public final double wetArea;             // m² total wetted
    public final double noseLength;          // m
    public final NoseShape noseShape;        // see enum below
    public final double finenessRatio;       // bodyLength / maxDiameter
    public final double referenceArea;       // m² = π(d/2)²

    // Boattail (zero if absent)
    public final double boattailLength;      // m
    public final double boattailBaseDiameter;// m

    // Fins
    public final int    finCount;
    public final double finRootChord;        // m
    public final double finTipChord;         // m
    public final double finSpan;             // m — semi-span from body
    public final double finThickness;        // m
    public final double finSweepAngle;       // rad — leading-edge sweep
    public final double finWettedArea;       // m² — one fin, both sides

    // Motor exit (for plume-on base drag)
    public final double motorExitArea;       // m²

    // Surface
    public final double surfaceRoughness;    // m — equivalent sand-grain k_s

    public enum NoseShape {
        CONICAL, OGIVE, VON_KARMAN, PARABOLIC, ELLIPSOID, HAACK
    }

    // All-args constructor — use a builder or static factory in the adapter
    public RomGeometryInput(
            double bodyLength, double maxDiameter, double baseArea,
            double wetArea, double noseLength, NoseShape noseShape,
            double finenessRatio, double referenceArea,
            double boattailLength, double boattailBaseDiameter,
            int finCount, double finRootChord, double finTipChord,
            double finSpan, double finThickness, double finSweepAngle,
            double finWettedArea, double motorExitArea, double surfaceRoughness) {
        this.bodyLength           = bodyLength;
        this.maxDiameter          = maxDiameter;
        this.baseArea             = baseArea;
        this.wetArea              = wetArea;
        this.noseLength           = noseLength;
        this.noseShape            = noseShape;
        this.finenessRatio        = finenessRatio;
        this.referenceArea        = referenceArea;
        this.boattailLength       = boattailLength;
        this.boattailBaseDiameter = boattailBaseDiameter;
        this.finCount             = finCount;
        this.finRootChord         = finRootChord;
        this.finTipChord          = finTipChord;
        this.finSpan              = finSpan;
        this.finThickness         = finThickness;
        this.finSweepAngle        = finSweepAngle;
        this.finWettedArea        = finWettedArea;
        this.motorExitArea        = motorExitArea;
        this.surfaceRoughness     = surfaceRoughness;
    }
}
```

### 2.3 — `FinDragModel.java`

Create in `rom-core/.../rom/core/physics/FinDragModel.java`

```java
package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public final class FinDragModel {

    private FinDragModel() {}

    /**
     * Fin friction drag — all fins, both surfaces — referenced to body frontal area.
     *
     * Uses Van Driest II at the fin local Reynolds number:
     *   Re_fin = Re_body × (c_mean / L_body)
     * Form factor: FF_fin = 1 + 2*(t/c)  (Hoerner thin-plate)
     *
     * Returns 0 if finCount == 0.
     */
    public static double cdFinFriction(double mach, double reLBody,
                                        RomGeometryInput g) {
        if (g.finCount == 0) return 0.0;
        double cMean  = (g.finRootChord + g.finTipChord) / 2.0;
        if (cMean < 1e-6) return 0.0;
        double reFin  = reLBody * (cMean / g.bodyLength);
        double cfInc  = SkinFrictionModel.cfIncompressible(reFin, 5e5);
        double cfComp = SkinFrictionModel.vanDriestII(cfInc, mach, 1.0);
        double tc     = g.finThickness / cMean;
        double ffFin  = 1.0 + 2.0 * tc;
        return cfComp * ffFin * g.finCount * g.finWettedArea / g.referenceArea;
    }

    /**
     * Fin wave drag at supersonic speeds (Ackeret thin-airfoil theory).
     *
     * For a symmetric double-wedge fin:
     *   Cd_wave = 4(t/c)² / √(M²−1) per fin, per unit planform area
     *
     * Returns 0 for M ≤ 1.2 (Ackeret theory not valid in transonic).
     * Returns 0 if finCount == 0.
     */
    public static double cdFinWaveSupersonic(double mach, RomGeometryInput g) {
        if (g.finCount == 0 || mach <= 1.2) return 0.0;
        double cMean   = (g.finRootChord + g.finTipChord) / 2.0;
        if (cMean < 1e-6) return 0.0;
        double tc      = g.finThickness / cMean;
        double beta_m  = Math.sqrt(mach * mach - 1.0);
        double planform = cMean * g.finSpan;  // one fin, one side
        return 4.0 * tc * tc / beta_m * g.finCount * planform / g.referenceArea;
    }

    /**
     * Fin-body junction interference increment.
     *
     * Hoerner: total fin drag multiplied by (Kf − 1) where Kf = 1.04.
     * Applied to the sum of fin friction and fin wave drag.
     */
    public static double cdFinInterference(double cdFinFrictionPlusWave,
                                            RomGeometryInput g) {
        if (g.finCount == 0) return 0.0;
        return cdFinFrictionPlusWave * 0.04;  // (Kf - 1) = 0.04
    }

    /**
     * Fin-induced drag at combined AoA and sideslip.
     *
     * Lifting-line normal force slope per fin panel (rad⁻¹):
     *   CNα_fin = 2π / (1 + 2/AR)
     * where AR = 2 × finSpan / c_mean (exposed panel aspect ratio).
     *
     * Effective angle: α_eff = √(α² + β²) — conservative estimate for a cruciform
     * rocket where some fins see α, others see β.
     *
     * Prandtl-Glauert correction for M < 0.8.
     *
     * Drag projection: ΔCd = CL_fin × sin(α_eff)
     *
     * @param alphaRad AoA in radians
     * @param betaRad  sideslip in radians
     */
    public static double cdFinInducedDrag(double alphaRad, double betaRad,
                                           double mach, RomGeometryInput g) {
        if (g.finCount == 0) return 0.0;
        double alphaEff = Math.sqrt(alphaRad * alphaRad + betaRad * betaRad);
        if (alphaEff < 1e-8) return 0.0;

        double cMean = (g.finRootChord + g.finTipChord) / 2.0;
        if (cMean < 1e-6) return 0.0;
        double ar    = 2.0 * g.finSpan / cMean;
        double cnAlpha = 2.0 * Math.PI / (1.0 + 2.0 / Math.max(ar, 0.5));

        double pgFactor = (mach < 0.8) ? 1.0 / Math.sqrt(1.0 - mach * mach) : 1.0;
        double finPlanform = cMean * g.finSpan;
        double cl = cnAlpha * alphaEff * pgFactor
                  * (g.finCount * finPlanform / g.referenceArea);

        return cl * Math.sin(alphaEff);
    }
}
```

### 2.4 — `SideslipModel.java`

Create in `rom-core/.../rom/core/physics/SideslipModel.java`

```java
package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public final class SideslipModel {

    private SideslipModel() {}

    /**
     * Body cross-flow drag from the lateral velocity component V·sin(β).
     *
     * Cross-flow force on a cylinder: F_cf = q × Cd_cf × d × L × sin²(β)
     * Normalized to frontal area (πd²/4):
     *   ΔCd = Cd_cf × (4/π) × (L/d) × sin²(β)
     *
     * Cd_cf = 1.2 (cylinder in cross-flow, moderate Re).
     * Returns 0 for |β| < 1e-6 rad.
     *
     * @param betaRad sideslip angle in radians
     */
    public static double cdBodyCrossflow(double betaRad, RomGeometryInput g) {
        if (Math.abs(betaRad) < 1e-6) return 0.0;
        double Cd_cf    = 1.2;
        double sinBeta  = Math.sin(betaRad);
        double diam     = Math.max(g.maxDiameter, 1e-4);
        double areaRatio = 4.0 * g.bodyLength / (Math.PI * diam);
        return Cd_cf * areaRatio * sinBeta * sinBeta;
    }

    /**
     * Net sideslip drag increment beyond what is already captured at β=0.
     *
     * ΔCd_β = body_crossflow(β) + fin_induced(α, β) − fin_induced(α, 0)
     *
     * The subtraction removes the α-only fin induced contribution that is
     * already present in the β=0 grid, preventing double-counting.
     *
     * @param alphaRad AoA in radians
     * @param betaRad  sideslip in radians
     */
    public static double cdSideslipIncrement(double alphaRad, double betaRad,
                                              double mach, RomGeometryInput g) {
        if (Math.abs(betaRad) < 1e-6) return 0.0;
        double dCdBody = cdBodyCrossflow(betaRad, g);
        double dCdFinBeta  = FinDragModel.cdFinInducedDrag(alphaRad, betaRad, mach, g);
        double dCdFinAlpha = FinDragModel.cdFinInducedDrag(alphaRad, 0.0,     mach, g);
        return dCdBody + Math.max(0.0, dCdFinBeta - dCdFinAlpha);
    }
}
```

### 2.5 — `NormalForceModel.java` (diagnostic CN)

Create in `rom-core/.../rom/core/physics/NormalForceModel.java`

```java
package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

/**
 * Semi-empirical normal force coefficient — DIAGNOSTIC ONLY.
 * NOT used by the trajectory integrator. Barrowman drives actual CN.
 */
public final class NormalForceModel {

    private static final double TWO_PI = 2.0 * Math.PI;

    private NormalForceModel() {}

    /**
     * Total CN from body lift and fin lift at small-to-moderate AoA.
     *
     * Body lift (slender body theory, Allen-Perkins):
     *   CN_body = 2 × α  (radians, for L/d > 10 in the linear regime)
     *
     * Fin normal force (lifting line, all fins):
     *   CNα_fin = 2π / (1 + 2/AR)  per radian
     *   CN_fin  = CNα_fin × α × fin_planform_total / A_ref
     *   Prandtl-Glauert correction for M < 0.8.
     *
     * Combined: CN = CN_body + CN_fin
     * Valid for α ≤ 15° (surface axis limit). Degrades above 12°.
     *
     * @param alphaRad AoA in radians (positive nose-up)
     * @param betaRad  sideslip in radians (contribution via combined angle)
     */
    public static double CN(double alphaRad, double betaRad,
                             double mach, RomGeometryInput g) {
        if (Math.abs(alphaRad) < 1e-8 && Math.abs(betaRad) < 1e-8) return 0.0;

        // Body lift — slender body theory
        // CN_body ≈ 2α (per radian), referenced to frontal area
        double CN_body = 2.0 * alphaRad;

        // Fin contribution
        double CN_fin = 0.0;
        if (g.finCount > 0) {
            double cMean = (g.finRootChord + g.finTipChord) / 2.0;
            double ar    = (cMean > 1e-6) ? 2.0 * g.finSpan / cMean : 1.0;
            double cnAlpha = TWO_PI / (1.0 + 2.0 / Math.max(ar, 0.5));
            double pgFactor = (mach < 0.8) ? 1.0 / Math.sqrt(1.0 - mach * mach) : 1.0;
            double planformTotal = cMean * g.finSpan * g.finCount;
            CN_fin = cnAlpha * alphaRad * pgFactor * planformTotal / g.referenceArea;
        }

        return CN_body + CN_fin;
    }
}
```

### 2.6 — `PitchingMomentModel.java` (diagnostic Cm)

Create in `rom-core/.../rom/core/physics/PitchingMomentModel.java`

```java
package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

/**
 * Semi-empirical pitching moment coefficient — DIAGNOSTIC ONLY.
 * NOT used by the trajectory integrator. Barrowman drives actual Cm.
 *
 * Moment reference: nose tip.
 * Length normalisation: body length.
 * Sign convention: nose-up positive (OpenRocket native).
 */
public final class PitchingMomentModel {

    private PitchingMomentModel() {}

    /**
     * Pitching moment Cm = CN × (x_cp − x_ref) / L_body
     *
     * where x_cp is the semi-empirical center of pressure location
     * and x_ref is the nose tip (x_ref = 0 in OpenRocket convention).
     *
     * Center of pressure estimate:
     *   x_cp ≈ 0.5 × noseLength  (nose contribution, centroid of pressure)
     *         + finArmFraction × bodyLength  (fin contribution)
     *
     * finArmFraction ≈ 0.85 for typical aft-mounted fins
     * (body length fraction to fin centroid from nose tip).
     *
     * The CN value passed in is the output of NormalForceModel.CN().
     *
     * @param CN       total normal force coefficient at this (M, Re, α, β)
     * @param alphaRad AoA in radians — used only to gate near-zero regime
     */
    public static double Cm(double CN, double alphaRad, RomGeometryInput g) {
        if (Math.abs(alphaRad) < 1e-8) return 0.0;
        if (g.bodyLength < 1e-6) return 0.0;

        // Semi-empirical CP location from nose tip
        double xCpNose = 0.5 * g.noseLength;
        double xCpFins = 0.85 * g.bodyLength;  // typical aft-fin moment arm
        double finFraction = (g.finCount > 0) ? 0.6 : 0.0;  // fin share of total CN

        double xCp = (1.0 - finFraction) * xCpNose + finFraction * xCpFins;

        // Cm = CN × (x_cp / L_body) — nose tip reference, positive nose-up
        // With nose-tip as reference, and CP aft of nose, Cm is positive when CN > 0.
        // For a statically stable rocket, CP is aft of CG → Cm is restoring (negative
        // in the sign sense that nose-up moment acts to reduce AoA).
        // Store as Cm = −CN × (x_cp / L_body) for aerodynamic convention (restoring negative).
        return -CN * (xCp / g.bodyLength);
    }
}
```

---

## Phase 3 — 4D grid evaluation engine

### 3.1 — Grid specification (final)

| Axis    | Points | Range                  | Notes |
|---------|--------|------------------------|-------|
| M       | 60     | 0.01–4.0               | Clustered transonic — reuse `DragGridEvaluator.buildMachAxis()` via adapter |
| log₁₀Re | 20     | log₁₀(1×10⁴)–log₁₀(1×10⁸) | Uniform — reuse `DragGridEvaluator.buildLogReAxis()` |
| α       | 10     | 0°–15°                 | Uniform — reuse `DragGridEvaluator.buildAlphaAxis()` |
| β       | 5      | 0°–45° or 0°–60°       | Determined by finCount |

**Total evaluations per build:** 60 × 20 × 10 × 5 = 60,000 (plume-off Cd, plume-on Cd,
Cd_body, CN, Cm) = 5 × 60,000 = 300,000 scalar evaluations.
**Target build time:** ≤ 20 seconds on a modern JVM.

Build runs on a `SwingWorker` background thread (same pattern as the existing 3D build).
The inner loops are sequential — no parallel streams, to keep thread model simple and
avoid unexpected interactions with OpenRocket's simulation engine.

### 3.2 — `AeroGridEvaluator4D.java`

Create in `rom-core`:
`rom-core/src/main/java/info/openrocket/core/aerodynamics/rom/core/eval/AeroGridEvaluator4D.java`

```java
package info.openrocket.core.aerodynamics.rom.core.eval;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.physics.*;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public final class AeroGridEvaluator4D {

    public static final int N_MACH  = 60;
    public static final int N_RE    = 20;
    public static final int N_ALPHA = 10;
    public static final int N_BETA  = 5;

    /** Functional interface for progress reporting (0.0 to 1.0). */
    @FunctionalInterface
    public interface ProgressListener { void onProgress(double fraction); }

    /**
     * Build the complete AeroSurface4D.
     *
     * At each (M, Re, α, β) grid point, evaluates:
     *   - Cd_total_plume_off
     *   - Cd_total_plume_on
     *   - Cd_body (body-alone, used to derive ΔCd_fin at query time)
     *   - CN (diagnostic)
     *   - Cm (diagnostic)
     *
     * @param g        validated geometry input (all fields populated)
     * @param progress listener for UI progress bar (may be null)
     */
    public static AeroSurface4D evaluate(RomGeometryInput g,
                                          ProgressListener progress) {
        double[] machAxis  = buildMachAxis();
        double[] logReAxis = buildLogReAxis();
        double[] alphaAxis = buildAlphaAxis();
        double[] betaAxis  = buildBetaAxis(g);

        double[][][][] cdOff  = new double[N_MACH][N_RE][N_ALPHA][N_BETA];
        double[][][][] cdOn   = new double[N_MACH][N_RE][N_ALPHA][N_BETA];
        double[][][][] cdBody = new double[N_MACH][N_RE][N_ALPHA][N_BETA];
        double[][][][] CN     = new double[N_MACH][N_RE][N_ALPHA][N_BETA];
        double[][][][] Cm     = new double[N_MACH][N_RE][N_ALPHA][N_BETA];

        int total = N_MACH * N_RE * N_ALPHA * N_BETA;
        int done  = 0;

        for (int im = 0; im < N_MACH; im++) {
            double mach = machAxis[im];
            for (int ir = 0; ir < N_RE; ir++) {
                double re_L = Math.pow(10.0, logReAxis[ir]);
                for (int ia = 0; ia < N_ALPHA; ia++) {
                    double alphaRad = Math.toRadians(alphaAxis[ia]);
                    for (int ib = 0; ib < N_BETA; ib++) {
                        double betaRad = Math.toRadians(betaAxis[ib]);

                        PointResult r = computePoint(mach, re_L, alphaRad, betaRad, g);
                        cdOff[im][ir][ia][ib]  = r.cdPlumeOff;
                        cdOn[im][ir][ia][ib]   = r.cdPlumeOn;
                        cdBody[im][ir][ia][ib] = r.cdBody;
                        CN[im][ir][ia][ib]     = r.CN;
                        Cm[im][ir][ia][ib]     = r.Cm;

                        done++;
                        if (progress != null && done % 500 == 0) {
                            progress.onProgress((double) done / total);
                        }
                    }
                }
            }
        }

        if (progress != null) progress.onProgress(1.0);
        return new AeroSurface4D(machAxis, logReAxis, alphaAxis, betaAxis,
                                  cdOff, cdOn, cdBody, CN, Cm,
                                  computeHash(g), g.finCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-point evaluation
    // ─────────────────────────────────────────────────────────────────────────

    /** Internal value object — one per grid point, not stored long-term. */
    static final class PointResult {
        final double cdPlumeOff, cdPlumeOn, cdBody, CN, Cm;
        PointResult(double cdOff, double cdOn, double cdBody, double CN, double Cm) {
            this.cdPlumeOff = cdOff; this.cdPlumeOn = cdOn;
            this.cdBody = cdBody; this.CN = CN; this.Cm = Cm;
        }
    }

    /**
     * Evaluate all five coefficients at a single grid point.
     *
     * Structure follows DragGridEvaluator.computeCdPlumeOff() exactly,
     * extended with fin models and sideslip.
     *
     * The β=0 case must reproduce the value from DragGridEvaluator.computeCdPlumeOff()
     * to within 1% (verified in unit tests).
     */
    static PointResult computePoint(double mach, double re_L,
                                     double alphaRad, double betaRad,
                                     RomGeometryInput g) {

        // ── Body skin friction ────────────────────────────────────────────────
        double cdFriction = SkinFrictionModel.cdFriction(mach, re_L, g);

        // Cf for base drag computation
        double cf_body = SkinFrictionModel.cfWithRoughness(
                SkinFrictionModel.vanDriestII(
                        SkinFrictionModel.cfIncompressible(re_L, 5e5), mach, 1.0),
                g.bodyLength, g.surfaceRoughness);

        // ── Base drag ─────────────────────────────────────────────────────────
        double cdBaseSubsonic    = BaseDragModel.cdBasePlumeOff(Math.min(mach, 0.59), cf_body, g);
        double cdBaseTransPeak   = BaseDragModel.cdBasePlumeOff(1.0, cf_body, g);
        double cdBasePlumeOff_m  = BaseDragModel.cdBasePlumeOff(mach, cf_body, g);
        double cdBasePlumeOn_m   = BaseDragModel.cdBasePlumeOn(mach, cf_body, g);

        // ── Nose wave drag ────────────────────────────────────────────────────
        double cdWaveNose = WaveDragModel.cdNoseWaveSupersonic(mach, g);

        // ── Fin drag ──────────────────────────────────────────────────────────
        double cdFinFric   = FinDragModel.cdFinFriction(mach, re_L, g);
        double cdFinWave   = FinDragModel.cdFinWaveSupersonic(mach, g);
        double cdFinJunct  = FinDragModel.cdFinInterference(cdFinFric + cdFinWave, g);
        double cdFinTotal  = cdFinFric + cdFinWave + cdFinJunct;

        // ── Assemble regime totals ────────────────────────────────────────────

        // Body-alone (for Cd_body grid — no fins, at β=0, α=0 equivalent)
        double cdBodySub   = cdFriction + cdBaseSubsonic;
        double cdBodyTrans = TransonicBlendingModel.transonicPeakCd(cdBodySub, g) + cdBaseTransPeak;
        double cdBodySup   = cdFriction + cdBasePlumeOff_m + cdWaveNose;
        double cdBodyValue = Math.max(0.001,
                TransonicBlendingModel.blend(mach, cdBodySub, cdBodyTrans, cdBodySup));

        // Full Cd, plume-off (body + fins + AoA + sideslip)
        double cdSub   = cdFriction + cdFinTotal + cdBaseSubsonic;
        double cdTrans = TransonicBlendingModel.transonicPeakCd(cdSub, g) + cdBaseTransPeak;
        double cdSup   = cdFriction + cdFinTotal + cdBasePlumeOff_m + cdWaveNose;

        double cdZeroAoA = TransonicBlendingModel.blend(mach, cdSub, cdTrans, cdSup);

        // AoA increments
        double cdBodyAoA     = InducedDragModel.cdInduced(alphaRad, mach, g);
        double cdFinInduced  = FinDragModel.cdFinInducedDrag(alphaRad, betaRad, mach, g);
        double cdSideslip    = SideslipModel.cdSideslipIncrement(alphaRad, betaRad, mach, g);

        double kf = InducedDragModel.protuberanceFactor();

        double cdPlumeOff = Math.max(0.001,
                (cdZeroAoA + cdBodyAoA + cdSideslip) * kf);

        // Plume-on: substitute plume-on base drag
        double cdSubOn  = cdFriction + cdFinTotal + cdBasePlumeOn_m;
        double cdTransOn = TransonicBlendingModel.transonicPeakCd(cdSubOn, g);
        double cdSupOn  = cdFriction + cdFinTotal + cdBasePlumeOn_m + cdWaveNose;
        double cdZeroAoAOn = TransonicBlendingModel.blend(mach, cdSubOn, cdTransOn, cdSupOn);
        double cdPlumeOn = Math.max(0.001,
                (cdZeroAoAOn + cdBodyAoA + cdSideslip) * kf);

        // ── Diagnostic CN and Cm ──────────────────────────────────────────────
        double cn = NormalForceModel.CN(alphaRad, betaRad, mach, g);
        double cm = PitchingMomentModel.Cm(cn, alphaRad, g);

        return new PointResult(cdPlumeOff, cdPlumeOn, cdBodyValue, cn, cm);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Axis builders
    // ─────────────────────────────────────────────────────────────────────────

    /** Mach axis — clustered in transonic, matches DragGridEvaluator layout. */
    public static double[] buildMachAxis() {
        double[] axis = new double[N_MACH];
        int i = 0;
        for (; i < 15; i++) axis[i] = 0.01 + i * (0.70 - 0.01) / 14.0;
        for (; i < 35; i++) axis[i] = 0.70 + (i - 15) * (1.30 - 0.70) / 19.0;
        for (; i < N_MACH; i++) axis[i] = 1.30 + (i - 35) * (4.00 - 1.30) / 24.0;
        return axis;
    }

    /** log₁₀Re axis — uniform from log₁₀(1e4) to log₁₀(1e8). */
    public static double[] buildLogReAxis() {
        double[] axis = new double[N_RE];
        double lo = 4.0, hi = 8.0;
        for (int i = 0; i < N_RE; i++) axis[i] = lo + i * (hi - lo) / (N_RE - 1);
        return axis;
    }

    /** α axis — uniform 0° to 15°. */
    public static double[] buildAlphaAxis() {
        double[] axis = new double[N_ALPHA];
        for (int i = 0; i < N_ALPHA; i++) axis[i] = i * 15.0 / (N_ALPHA - 1);
        return axis;
    }

    /**
     * β axis — uniform 0° to symmetry limit.
     * 4-fin cruciform: 45° (90° rotational period → 0–45° captures full variation).
     * 3-fin:           60° (120° period → 0–60°).
     * 0 fins:          single point at 0°.
     */
    public static double[] buildBetaAxis(RomGeometryInput g) {
        double maxBeta = switch (g.finCount) {
            case 0, 1, 2 -> 0.0;
            case 3        -> 60.0;
            default       -> 45.0;   // 4+ fins → cruciform symmetry
        };
        if (maxBeta < 1e-6) {
            return new double[]{0.0};  // degenerate single-point axis
        }
        double[] axis = new double[N_BETA];
        for (int i = 0; i < N_BETA; i++) axis[i] = i * maxBeta / (N_BETA - 1);
        return axis;
    }

    /** Geometry hash passed through to AeroSurface4D. */
    private static String computeHash(RomGeometryInput g) {
        // Delegate to GeometryAdapter which has access to SHA-256
        // For rom-core (no external deps), implement inline:
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            String data = String.format(
                    "%.6f%.6f%.6f%s%d%.6f%.6f%.6f%.6f%.6f%.6f%.6f%.6f",
                    g.bodyLength, g.maxDiameter, g.noseLength, g.noseShape.name(),
                    g.finCount, g.finRootChord, g.finTipChord, g.finSpan,
                    g.finThickness, g.finSweepAngle, g.boattailLength,
                    g.surfaceRoughness, g.motorExitArea);
            byte[] digest = md.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
```

---

## Phase 4 — 4D PCHIP interpolator

### 4.1 — `AeroSurface4DInterpolator.java`

Create in `rom-core`:
`rom-core/src/main/java/info/openrocket/core/aerodynamics/rom/core/surface/AeroSurface4DInterpolator.java`

Mirrors the structure of `DragSurfaceInterpolator`. PCHIP along M at every
`(Re, α, β)` corner; trilinear across the `(Re, α, β)` cube.
One interpolator bank per coefficient (5 total).

```java
package info.openrocket.core.aerodynamics.rom.core.surface;

import info.openrocket.core.aerodynamics.rom.core.physics.PchipInterpolator1D;

public final class AeroSurface4DInterpolator {

    private final AeroSurface4D surface;

    // PCHIP slices: [iRe][iAlpha][iBeta] → PchipInterpolator1D along M
    private final PchipInterpolator1D[][][] slicesCdOff;
    private final PchipInterpolator1D[][][] slicesCdOn;
    private final PchipInterpolator1D[][][] slicesCdBody;
    private final PchipInterpolator1D[][][] slicesCN;
    private final PchipInterpolator1D[][][] slicesCm;

    public AeroSurface4DInterpolator(AeroSurface4D surface) {
        this.surface = surface;
        int nM = surface.machAxis.length;
        int nR = surface.logReAxis.length;
        int nA = surface.alphaAxis.length;
        int nB = surface.betaAxis.length;

        slicesCdOff  = buildSlices(surface.cdPlumeOff,  nM, nR, nA, nB, surface.machAxis);
        slicesCdOn   = buildSlices(surface.cdPlumeOn,   nM, nR, nA, nB, surface.machAxis);
        slicesCdBody = buildSlices(surface.cdBody,       nM, nR, nA, nB, surface.machAxis);
        slicesCN     = buildSlices(surface.CN,           nM, nR, nA, nB, surface.machAxis);
        slicesCm     = buildSlices(surface.Cm,           nM, nR, nA, nB, surface.machAxis);
    }

    // ── Public query methods ──────────────────────────────────────────────────

    /** Query result: all five coefficients at one flight condition. */
    public static final class QueryResult {
        public final double cdPlumeOff, cdPlumeOn, cdBody, dCdFin, CN, Cm;
        QueryResult(double cdOff, double cdOn, double cdBody,
                    double CN, double Cm) {
            this.cdPlumeOff = cdOff;
            this.cdPlumeOn  = cdOn;
            this.cdBody     = cdBody;
            this.dCdFin     = cdOff - cdBody;  // derived, not stored
            this.CN         = CN;
            this.Cm         = Cm;
        }
    }

    /**
     * Query all coefficients at (M, Re, α_deg, β_deg).
     *
     * @param mach      freestream Mach number
     * @param re_L      body-length Reynolds number
     * @param alphaDeg  angle of attack in degrees (clamped to axis max)
     * @param betaDeg   sideslip angle in degrees (clamped to axis max, symmetry applied)
     */
    public QueryResult query(double mach, double re_L,
                              double alphaDeg, double betaDeg) {
        double logRe = Math.log10(Math.max(re_L, 1e4));
        alphaDeg = Math.abs(alphaDeg);  // symmetric in AoA
        betaDeg  = Math.abs(betaDeg);   // symmetric in sideslip

        // Enforce β symmetry: for cruciform (N≥4), β and β+90° are equivalent;
        // clamp to betaAxis max instead of folding (folding would require extra logic).
        double betaMax = surface.betaAxis[surface.betaAxis.length - 1];
        betaDeg = Math.min(betaDeg, betaMax);

        int ir0 = floor(surface.logReAxis, logRe);
        int ia0 = floor(surface.alphaAxis,  alphaDeg);
        int ib0 = floor(surface.betaAxis,   betaDeg);

        double tr = frac(surface.logReAxis, ir0, logRe);
        double ta = frac(surface.alphaAxis,  ia0, alphaDeg);
        double tb = frac(surface.betaAxis,   ib0, betaDeg);

        return new QueryResult(
                trilinear(slicesCdOff,  mach, ir0, ia0, ib0, tr, ta, tb),
                trilinear(slicesCdOn,   mach, ir0, ia0, ib0, tr, ta, tb),
                trilinear(slicesCdBody, mach, ir0, ia0, ib0, tr, ta, tb),
                trilinear(slicesCN,     mach, ir0, ia0, ib0, tr, ta, tb),
                trilinear(slicesCm,     mach, ir0, ia0, ib0, tr, ta, tb));
    }

    // ── Convenience single-coefficient queries ────────────────────────────────

    public double queryCdPlumeOff(double mach, double re_L,
                                   double alphaDeg, double betaDeg) {
        return query(mach, re_L, alphaDeg, betaDeg).cdPlumeOff;
    }

    public double queryCdPlumeOn(double mach, double re_L,
                                  double alphaDeg, double betaDeg) {
        return query(mach, re_L, alphaDeg, betaDeg).cdPlumeOn;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static PchipInterpolator1D[][][] buildSlices(
            double[][][][] grid, int nM, int nR, int nA, int nB, double[] machAxis) {
        PchipInterpolator1D[][][] s = new PchipInterpolator1D[nR][nA][nB];
        for (int ir = 0; ir < nR; ir++)
            for (int ia = 0; ia < nA; ia++)
                for (int ib = 0; ib < nB; ib++) {
                    double[] col = new double[nM];
                    for (int im = 0; im < nM; im++) col[im] = grid[im][ir][ia][ib];
                    s[ir][ia][ib] = new PchipInterpolator1D(machAxis, col);
                }
        return s;
    }

    private static double trilinear(PchipInterpolator1D[][][] slices,
                                     double mach,
                                     int ir0, int ia0, int ib0,
                                     double tr, double ta, double tb) {
        // Guard: if β axis has only 1 point (finCount 0–2), no β interpolation needed
        boolean hasBeta = slices[ir0][ia0].length > 1;
        if (!hasBeta) {
            // Bilinear in (Re, α) only
            double c00 = slices[ir0  ][ia0  ][0].evaluate(mach);
            double c10 = slices[ir0+1][ia0  ][0].evaluate(mach);
            double c01 = slices[ir0  ][ia0+1][0].evaluate(mach);
            double c11 = slices[ir0+1][ia0+1][0].evaluate(mach);
            return (1-tr)*(1-ta)*c00 + tr*(1-ta)*c10
                 + (1-tr)*  ta *c01 + tr*  ta *c11;
        }
        double c000 = slices[ir0  ][ia0  ][ib0  ].evaluate(mach);
        double c100 = slices[ir0+1][ia0  ][ib0  ].evaluate(mach);
        double c010 = slices[ir0  ][ia0+1][ib0  ].evaluate(mach);
        double c110 = slices[ir0+1][ia0+1][ib0  ].evaluate(mach);
        double c001 = slices[ir0  ][ia0  ][ib0+1].evaluate(mach);
        double c101 = slices[ir0+1][ia0  ][ib0+1].evaluate(mach);
        double c011 = slices[ir0  ][ia0+1][ib0+1].evaluate(mach);
        double c111 = slices[ir0+1][ia0+1][ib0+1].evaluate(mach);
        return (1-tr)*(1-ta)*(1-tb)*c000 + tr*(1-ta)*(1-tb)*c100
             + (1-tr)*  ta *(1-tb)*c010 + tr*  ta *(1-tb)*c110
             + (1-tr)*(1-ta)*  tb *c001 + tr*(1-ta)*  tb *c101
             + (1-tr)*  ta *  tb *c011 + tr*  ta *  tb *c111;
    }

    private static int floor(double[] axis, double v) {
        if (v <= axis[0]) return 0;
        if (v >= axis[axis.length-1]) return axis.length - 2;
        int lo = 0, hi = axis.length - 2;
        while (lo < hi) { int m = (lo+hi+1)>>>1; if (axis[m]<=v) lo=m; else hi=m-1; }
        return lo;
    }

    private static double frac(double[] axis, int i, double v) {
        double d = axis[i+1] - axis[i];
        return d < 1e-14 ? 0.0 : Math.max(0.0, Math.min(1.0, (v - axis[i]) / d));
    }
}
```

---

## Phase 5 — OpenRocket adapter and calculator extension

### 5.1 — `GeometryAdapter.java`

Create in the OpenRocket module at:
`rom-openrocket/src/main/java/info/openrocket/core/aerodynamics/rom/adapter/GeometryAdapter.java`

```java
package info.openrocket.core.aerodynamics.rom.adapter;

import info.openrocket.core.aerodynamics.rom.RomGeometryParameters;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

/**
 * Converts a fully-populated RomGeometryParameters (OpenRocket-aware)
 * into a RomGeometryInput (rom-core, no OpenRocket imports).
 */
public final class GeometryAdapter {

    private GeometryAdapter() {}

    public static RomGeometryInput toInput(RomGeometryParameters g) {
        return new RomGeometryInput(
                g.bodyLength, g.maxDiameter, g.baseArea,
                g.wetArea, g.noseLength,
                mapNoseShape(g.noseShape),
                g.finessRatio, g.referenceArea,
                g.boattailLength, g.boattailBaseDiameter,
                g.finCount, g.finRootChord, g.finTipChord,
                g.finSpan, g.finThickness, g.finSweepAngle,
                g.finWettedArea, g.motorExitArea, g.surfaceRoughness);
    }

    private static RomGeometryInput.NoseShape mapNoseShape(
            RomGeometryParameters.NoseShape s) {
        return switch (s) {
            case CONICAL    -> RomGeometryInput.NoseShape.CONICAL;
            case OGIVE      -> RomGeometryInput.NoseShape.OGIVE;
            case VON_KARMAN -> RomGeometryInput.NoseShape.VON_KARMAN;
            case PARABOLIC  -> RomGeometryInput.NoseShape.PARABOLIC;
            case ELLIPSOID  -> RomGeometryInput.NoseShape.ELLIPSOID;
            case HAACK      -> RomGeometryInput.NoseShape.HAACK;
        };
    }
}
```

### 5.2 — Extend `RomAerodynamicCalculator.java`

Open the existing file. Make only the following targeted additions — do not restructure
the class.

**Add field:**
```java
// Alongside existing DragSurfaceInterpolator field:
private AeroSurface4DInterpolator interpolator4D = null;
private RomGeometryInput geomInput4D = null; // stored for body length in Re computation
```

**Extend `installSurface()`:**
```java
// This overload accepts the 4D surface from the new build path
public void installSurface4D(AeroSurface4D surface4D, RomGeometryInput geom) {
    this.interpolator4D = new AeroSurface4DInterpolator(surface4D);
    this.geomInput4D    = geom;
    // Also install the β=0 slice into the 3D interpolator for fallback:
    // (construct a DragSurface from the 4D β=0 slice — keep existing behavior intact)
}

public boolean has4DSurface() { return interpolator4D != null; }
```

**Extend `getAerodynamicForces()`:**

After the existing `if (interpolator == null) return forces;` check, add:

```java
if (interpolator4D != null) {
    // Extract β — check FlightConditions API; use getTheta() or equivalent
    // for the sideslip angle in the wind frame. If unavailable, use 0.
    double betaDeg = 0.0;
    // TODO: replace with actual FlightConditions.getSideslipAngle() or equivalent
    // once the correct API method is identified from FlightConditions.java

    AeroSurface4DInterpolator.QueryResult q = interpolator4D.query(
            mach, re_L, alphaDeg, betaDeg);

    double cdBlended = q.cdPlumeOff
            + plumeDecayState * (q.cdPlumeOn - q.cdPlumeOff);
    forces.setCD(cdBlended);

    // Log decomposed drag values as custom flight data
    // (only if the FlightDataBranch API supports custom keys — check before calling)
    // branch.addValue(RomFlightDataTypes.CD_BODY, q.cdBody);
    // branch.addValue(RomFlightDataTypes.CD_FIN,  q.dCdFin);
    // branch.addValue(RomFlightDataTypes.CN_ROM,  q.CN);
    // branch.addValue(RomFlightDataTypes.CM_ROM,  q.Cm);

    return forces;
}
// else: fall through to existing 3D interpolator path
```

**Implementation note on β extraction:** Locate the correct method in
`FlightConditions.java` that gives the sideslip angle (the angle between the velocity
vector and the rocket's pitch plane). Check `getTheta()`, `getAOA()`, and any wind angle
methods. If no direct sideslip getter exists, approximate β = 0 and leave a `// TODO`
comment with the correct derivation for a future pass.

### 5.3 — Extend `RomPrestepPanel.java`

Open the existing panel. Make only these targeted changes:

**Add 3D mode checkbox** (below the existing build button):
```java
JCheckBox chk3D = new JCheckBox("3D mode (adds β sideslip axis, ~20 s build)");
chk3D.setSelected(false);
chk3D.setToolTipText(
    "Extends surface from Cd(M,Re,α) to Cd(M,Re,α,β). " +
    "Enables sideslip drag contribution for crosswind simulations. " +
    "CN and Cm diagnostic outputs also stored (validation panel only).");
```

**Modify build action step 4** to branch on the checkbox:
```java
DragGridEvaluator.ProgressListener listener =
        fraction -> publish((int)(fraction * 100));

if (chk3D.isSelected()) {
    RomGeometryParameters gParams = RomGeometryParameters.fromRocket(config);
    RomGeometryInput gInput = GeometryAdapter.toInput(gParams);
    AeroSurface4D surface4D = AeroGridEvaluator4D.evaluate(gInput, listener);
    simConditions.getRomCalculator().installSurface4D(surface4D, gInput);
} else {
    DragSurface surface = DragGridEvaluator.evaluate(g, listener);
    simConditions.getRomCalculator().installSurface(surface);
}
```

**Add third Cd(M) chart series** when 4D mode is active:
After the chart is redrawn, if `chk3D.isSelected()` and `interpolator4D != null`,
add a third series "β = 45°" (dashed orange) by querying
`interpolator4D.queryCdPlumeOff(M, 1e6, 0.0, 45.0)` across the M axis.

**Extend validation panel** to accept optional 5-column CSV:
```
M, Re, alpha_deg, beta_deg, Cd   ← 5 columns (beta_deg optional, defaults to 0)
```
Parse the optional fifth column. If `chk3D.isSelected()` and beta is provided,
query `interpolator4D.queryCdPlumeOff(M, Re, alpha, beta)`. The comparison table
adds a "β" column when 5-column data is detected.

Also add a **CN/Cm comparison sub-table** that appears when the 4D surface is
active and the user has pasted CN or Cm data. The table columns are:
`M | Re | α° | CN_ROM | CN_CFD | CN_err% | Cm_ROM | Cm_CFD | Cm_err%`
with separate rows for each pasted data point. This is the diagnostic output
path — it does not feed the trajectory integrator.

---

## Phase 6 — Serialization and CSV export

### 6.1 — `AeroSurfaceSerializer.java` (rom-core)

Create in `rom-core`:
`rom-core/src/main/java/info/openrocket/core/aerodynamics/rom/core/io/AeroSurfaceSerializer.java`

Binary format (version="3" to distinguish from the original plan's v1 and v2):

```
XML tag: <romdragsurface4d version="3"
                            geometryhash="[64 hex]"
                            builttimestamp="[epoch ms]"
                            fincount="[int]">
    [Base64(gzip(binary blob v3))]
</romdragsurface4d>
```

Binary blob v3 layout (little-endian, Java `DataOutputStream`):
```
int32:   N_MACH
int32:   N_RE
int32:   N_ALPHA
int32:   N_BETA
double[N_MACH]:   machAxis
double[N_RE]:     logReAxis
double[N_ALPHA]:  alphaAxis
double[N_BETA]:   betaAxis
double[N_MACH*N_RE*N_ALPHA*N_BETA]: cdPlumeOff  (M-major order: im innermost → ib outermost)
double[N_MACH*N_RE*N_ALPHA*N_BETA]: cdPlumeOn
double[N_MACH*N_RE*N_ALPHA*N_BETA]: cdBody
double[N_MACH*N_RE*N_ALPHA*N_BETA]: CN
double[N_MACH*N_RE*N_ALPHA*N_BETA]: Cm
```

**Index order for flat array:** `index = im + N_MACH*(ir + N_RE*(ia + N_ALPHA*ib))`.
Use M as the fastest-varying index because PCHIP builds slices along M — this allows
sequential reads when constructing interpolator slices.

**Estimated file size:** `5 × (60×20×10×5) × 8 bytes = 2.4 MB` uncompressed,
approximately 200–300 KB after gzip. Acceptable for embedding in `.ork` XML.

```java
public final class AeroSurfaceSerializer {

    public static byte[] serialize(AeroSurface4D surface) throws IOException;
    // Compresses to gzip, Base64-encodes

    public static AeroSurface4D deserialize(byte[] base64Gzip) throws IOException;
    // Decodes Base64, decompresses gzip, reads binary blob
    // Validates axis monotonicity after reading
}
```

### 6.2 — `CsvExporter.java` (rom-core)

Create in `rom-core`:
`rom-core/src/main/java/info/openrocket/core/aerodynamics/rom/core/io/CsvExporter.java`

Exports the full surface as a flat CSV. This is the interoperability path for
RocketPy or any other tool that needs to consume the surface.

```java
public final class CsvExporter {

    /**
     * Exports all grid points to CSV.
     * Header: mach,log10Re,alpha_deg,beta_deg,Cd_plume_off,Cd_plume_on,Cd_body,dCd_fin,CN,Cm
     *
     * Row count: N_MACH × N_RE × N_ALPHA × N_BETA (up to 60,000 rows).
     * File size estimate: ~8 MB uncompressed, ~1 MB gzipped.
     */
    public static void exportToCsv(AeroSurface4D surface,
                                    java.io.OutputStream out) throws IOException;
}
```

The UI exposes this as an "Export surface CSV" button in the validation panel.
Wire the button in `RomPrestepPanel` to open a `JFileChooser`, then call
`CsvExporter.exportToCsv(surface4D, new FileOutputStream(selectedFile))`.

### 6.3 — Hook into OpenRocket file I/O

In the existing OpenRocket `RocketSaver`/`RocketLoader` classes (original plan
Phase 7.2), add a parallel save/load hook for the v3 tag alongside the existing
v1 tag handler. A file can contain both tags simultaneously if the user has
built both a 3D (v1) and 4D (v3) surface — load both and install into the
`RomAerodynamicCalculator` fields accordingly.

---

## Phase 7 — Unit tests

All tests in `rom-core` have zero OpenRocket dependencies and can run as plain JUnit 5.

### 7.1 — `FinDragModelTest.java` (rom-core)

```
cdFinFriction(0.5, 1e6, g_typical)  ∈ [0.005, 0.05]
cdFinFriction(0.5, 1e6, g_no_fins) == 0.0
cdFinWaveSupersonic(1.5, g_typical) > 0.0
cdFinWaveSupersonic(0.9, g_typical) == 0.0     (M ≤ 1.2 returns 0)
cdFinWaveSupersonic(1.5, g_no_fins) == 0.0
cdFinInducedDrag(0.0, 0.0, 0.5, g) == 0.0
cdFinInducedDrag(0.087, 0.0, 0.5, g) > 0.0   (5° AoA)
cdFinInducedDrag(0.0, 0.087, 0.5, g) > 0.0   (5° sideslip)
cdFinInducedDrag(0.087, 0.087, 0.5, g) >
    cdFinInducedDrag(0.087, 0.0, 0.5, g)      (combined > AoA alone)
cdFinInterference(0.05, g_typical) ≈ 0.002 ± 0.0002
```

### 7.2 — `SideslipModelTest.java` (rom-core)

```
cdBodyCrossflow(0.0, g)    == 0.0
cdBodyCrossflow(π/2, g)    > cdBodyCrossflow(π/4, g)   (monotone in β)
cdSideslipIncrement(0.0, 0.0, 0.5, g) ≈ 0.0
cdSideslipIncrement(0.0, π/4, 0.5, g) > 0.0
cdSideslipIncrement(0.087, π/4, 0.5, g) >
    cdSideslipIncrement(0.087, 0.0, 0.5, g)
```

### 7.3 — `NormalForceModelTest.java` (rom-core)

```
CN(0.0, 0.0, 0.5, g)        == 0.0
CN(0.087, 0.0, 0.5, g)      ∈ [0.1, 1.5]   (5° AoA, plausible range)
CN(0.174, 0.0, 0.5, g)      > CN(0.087, 0.0, 0.5, g)   (monotone in α, small angle)
CN(0.0, 0.087, 0.5, g)      == 0.0          (β only, α=0 → CN = 0 by convention)
CN(0.087, 0.0, 0.5, g_no_fins) < CN(0.087, 0.0, 0.5, g_with_fins)   (fins add lift)
```

### 7.4 — `PitchingMomentModelTest.java` (rom-core)

```
Cm(0.0, 0.0, g)  == 0.0
Cm(0.5, 0.087, g) < 0.0     (restoring moment — negative Cm convention)
|Cm(1.0, 0.087, g)| > |Cm(0.5, 0.087, g)|  // larger CN → larger |Cm|
Cm(0.5, 0.087, g_longer_body) < Cm(0.5, 0.087, g_shorter_body)
    // larger moment arm → more negative Cm (for same CN)
```

### 7.5 — `AeroGridEvaluator4DTest.java` (rom-core)

```
computePoint(0.3, 1e6, 0, 0, g).cdPlumeOff  ∈ [0.15, 0.70]   (typical HPR range)
computePoint(2.0, 1e6, 0, 0, g).cdPlumeOff <
    computePoint(0.9, 1e6, 0, 0, g).cdPlumeOff   (Cd drops past transonic peak)
computePoint(0.3, 1e6, 0, 0, g).cdPlumeOn <
    computePoint(0.3, 1e6, 0, 0, g).cdPlumeOff   (plume reduces base drag)
computePoint(0.3, 1e6, 0, π/4, g).cdPlumeOff >
    computePoint(0.3, 1e6, 0, 0,   g).cdPlumeOff  (sideslip increases drag)
computePoint(0.3, 1e6, 0, 0, g).CN    > 0.0 iff α > 0 (not reached here; α=0 → CN=0)
computePoint(0.3, 1e6, 0.087, 0, g).CN > 0.0
computePoint(0.3, 1e6, 0.087, 0, g).Cm < 0.0   (stable rocket → restoring moment)

// β=0 parity with OpenRocket-module DragGridEvaluator (< 1% tolerance):
// Run DragGridEvaluator.computeCdPlumeOff(0.3, 1e6, 0, g_or) where g_or is the
// equivalent RomGeometryParameters, and compare to computePoint result.
// Assert |difference| / reference < 0.01.

// Performance gate:
// Full evaluate() with N_MACH=60, N_RE=20, N_ALPHA=10, N_BETA=5 completes ≤ 20 000 ms
buildBetaAxis(g_4fin) → length=5, last element=45.0
buildBetaAxis(g_3fin) → length=5, last element=60.0
buildBetaAxis(g_no_fins) → length=1, only element=0.0
```

### 7.6 — `AeroSurface4DInterpolatorTest.java` (rom-core)

```
Build surface via AeroGridEvaluator4D.evaluate()
Query at every grid point — max|interpolated - grid_value| < 0.5%
Query at grid midpoints — max error < 2.0%
queryCdPlumeOff at β=0 matches 3D surface β=0 slice to within 0.5%
queryCdPlumeOff at β=betaMax+5° clamps gracefully — no exception, returns value at betaMax
queryCdPlumeOff(0.5, 1e6, 0°, 45°) > queryCdPlumeOff(0.5, 1e6, 0°, 0°)  (sideslip > zero)
query().dCdFin == query().cdPlumeOff - query().cdBody  (derived identity)
query().dCdFin >= -0.005  (fins should not subtract more than noise from body Cd)
```

### 7.7 — `AeroSurfaceSerializerTest.java` (rom-core)

```
Build surface → serialize → deserialize
Deserialized axes match original element-by-element (within 1e-12)
Deserialized cdPlumeOff[0][0][0][0] matches original within 1e-12
Deserialized cdBody[3][5][2][1] matches original within 1e-12
Deserialized CN[10][8][4][2] matches original within 1e-12
Round-trip for a 1-beta-point surface (no-fins case) completes without exception
```

---

## Phase 8 — Integration smoke test

Run original plan Phase 9 steps 1–8 first. Then add:

**Step 9:** Check "3D mode" checkbox. Click "Build aerodynamic surface". Verify
progress bar advances and completes within 25 seconds.

**Step 10:** Verify three Cd(M) series appear in the preview chart:
- "Plume-off" (solid, existing)
- "Plume-on" (dashed, existing)
- "β = 45°" (dashed orange, new)
The β = 45° line must be visibly above the plume-off line at subsonic speeds.

**Step 11:** Run a simulation. Verify it completes without exception and produces
an apogee altitude. The value may differ slightly from the 3D-only result due to
the improved fin drag model.

**Step 12:** Open the validation panel. Paste 5-column CSV data including CN and Cm
columns. Click "Compare". Verify the CN/Cm sub-table appears with ROM vs CFD columns.

**Step 13:** Click "Export surface CSV". Choose a save location. Verify the file is
written, opens in a spreadsheet, and has the correct column headers.

**Step 14:** Save the `.ork` file. Close and reopen. Verify the v3 surface is restored,
the 3D mode checkbox is checked, the β = 45° chart series is present, and the previous
simulation results are reproduced.

---

## Appendix A — Scope boundary: what this plan does not include

The following are explicitly out of scope. Do not generate code for them:

- Replacing Barrowman for CN/Cm in the trajectory integrator (CN and Cm are diagnostic
  outputs only — Barrowman drives the actual simulation forces and moments)
- OpenFOAM case setup, meshing, or any CFD tooling
- RocketPy Python integration (the CSV export in Phase 6 is the data handoff; Python
  consumption is out of scope)
- Any GUI beyond the extensions specified in Phase 5.3
- Standalone trajectory simulator (the `AeroSurface4DInterpolator` is the query API
  for any future standalone consumer, but no standalone simulator is built here)

---

## Appendix B — Known accuracy limitations (additions to original Appendix B)

Add to the user-facing help text alongside the original limitations:

- **Sideslip model accuracy (±20–40%):** Cross-flow cylinder approximation (Cd_cf = 1.2).
  At typical weathercocking angles (β < 5°), the β contribution to total drag is under 2%
  — well within total model uncertainty.

- **CN diagnostic accuracy (±10–25%):** Slender-body theory + lifting-line fins is valid
  for AoA < 10°. Above 10°, leeward vortex formation is not modeled. Use Barrowman for
  all flight-critical CN/Cm values.

- **Cm reference sensitivity:** Cm uses a fixed 85% body-length fin moment arm. For
  rockets with non-standard fin placement, error can reach ±30%. The diagnostic value
  is most useful for trend comparison, not absolute accuracy.

- **Fin-body junction (±15%):** Kf = 1.04 blanket factor. Geometry-specific validation
  recommended for unconventional fin mounting.