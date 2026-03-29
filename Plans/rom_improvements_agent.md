# ROM Improvement Agent Instructions
## OpenRocket — `rom.core` 4D Aerodynamic Lookup Table Hardening

---

## Purpose and scope

This document drives an AI coding agent to completion of all required fixes and improvements
to the OpenRocket reduced-order aerodynamics model (ROM). The goal is a fully correct,
well-tested, production-quality 4D lookup table for `Cd`, `CN`, and `Cm` over
`(Mach, Re, alpha, beta)` in the `rom.core` package, with the legacy 3D `rom` system
cleanly deprecated.

**Read this entire document before touching any file.** The tasks are ordered by dependency —
later tasks may reference types or methods introduced in earlier ones. Completing them
out of order will cause compile failures.

---

## Repository layout (relevant paths only)

```
src/main/java/info/openrocket/core/aerodynamics/
├── rom/                                         ← LEGACY — deprecate, do not extend
│   ├── BaseDragModel.java
│   ├── DragGridEvaluator.java
│   ├── DragSurface.java
│   ├── DragSurfaceInterpolator.java
│   ├── DragSurfaceSerializer.java
│   ├── InducedDragModel.java
│   ├── PchipInterpolator1D.java                 ← shared interpolator; move to rom.core.surface
│   ├── RomGeometryParameters.java               ← legacy geometry type; has OpenRocket deps
│   ├── SkinFrictionModel.java                   ← legacy copy; rom.core.physics has clean copy
│   ├── TransonicBlendingModel.java              ← legacy copy
│   └── WaveDragModel.java                       ← legacy copy
│
└── rom/core/                                    ← CURRENT — all work goes here
    ├── eval/
    │   └── AeroGridEvaluator4D.java
    ├── geometry/
    │   └── RomGeometryInput.java                ← clean, no OR deps
    ├── io/
    │   ├── AeroSurfaceSerializer.java
    │   └── CsvExporter.java
    ├── physics/
    │   ├── BaseDragModel.java
    │   ├── FinDragModel.java
    │   ├── InducedDragModel.java
    │   ├── NormalForceModel.java
    │   ├── PitchingMomentModel.java
    │   ├── SideslipModel.java
    │   ├── SkinFrictionModel.java
    │   ├── TransonicBlendingModel.java
    │   └── WaveDragModel.java
    └── surface/
        ├── AeroSurface4D.java
        ├── AeroSurface4DInterpolator.java        ← BUG: missing PchipInterpolator1D import
        └── PchipInterpolator1D.java              ← DOES NOT EXIST YET — must be created here
```

---

## Mandatory pre-flight checks

Before making any change, verify each of the following. If any check fails, resolve it
before proceeding to the tasks.

1. **Compile check**: The project must compile cleanly (or fail only for the known
   `PchipInterpolator1D` missing-package issue). Run `./gradlew compileJava` (or your
   build tool equivalent). Record the exact errors.

2. **Package declaration audit**: Read the first line of every `.java` file listed above.
   Confirm that files under `rom/core/surface/` declare
   `package info.openrocket.core.aerodynamics.rom.core.surface;` and so on for each
   subdirectory. Flag any mismatches.

3. **`PchipInterpolator1D` reference audit**: Search the codebase for all usages of
   `PchipInterpolator1D`. Confirm:
   - `AeroSurface4DInterpolator.java` references it without a qualifying import (because
     the class does not yet exist in `rom.core.surface`).
   - `DragSurfaceInterpolator.java` references the one in `rom` (correct for legacy).
   - No other files in `rom.core.*` reference the `rom` package's copy.

---

## Task 1 — Copy `PchipInterpolator1D` into `rom.core.surface`

**Priority: CRITICAL — blocks compilation of the entire 4D system.**

### What to do

Create a new file:
```
src/main/java/info/openrocket/core/aerodynamics/rom/core/surface/PchipInterpolator1D.java
```

Copy the complete body of the existing file at:
```
src/main/java/info/openrocket/core/aerodynamics/rom/PchipInterpolator1D.java
```

Change **only** the package declaration on line 1:

```java
// BEFORE (in the legacy file — leave it untouched):
package info.openrocket.core.aerodynamics.rom;

// AFTER (the new file in rom.core.surface):
package info.openrocket.core.aerodynamics.rom.core.surface;
```

All class body content, Javadoc, and method signatures must be byte-for-byte identical to
the source file. Do not "improve" the interpolator in this task — that would mix concerns.

### What NOT to do

- Do **not** delete or modify the original `rom/PchipInterpolator1D.java`. The legacy
  `DragSurfaceInterpolator` depends on it and Task 7 handles the legacy cleanup.
- Do **not** add an import to `AeroSurface4DInterpolator.java` pointing at the `rom`
  package. The new file in `rom.core.surface` will be in the same package and requires
  no import.

### Verification

After creating the file:

1. Run `./gradlew compileJava`. The compile error about `PchipInterpolator1D` in
   `AeroSurface4DInterpolator` must be gone.
2. Confirm `AeroSurface4DInterpolator.java` compiles without any import statement for
   `PchipInterpolator1D` (same-package resolution).
3. Confirm `DragSurfaceInterpolator.java` still compiles (it uses the `rom` package copy
   which remains untouched).

---

## Task 2 — Fix `NormalForceModel.CN` supersonic compressibility

**Priority: CRITICAL — produces wrong CN for all M > 0.8, affecting every supersonic
trajectory point.**

### File to modify

```
src/main/java/info/openrocket/core/aerodynamics/rom/core/physics/NormalForceModel.java
```

### What is wrong

The Prandtl-Glauert compressibility factor inside the fin contribution is:

```java
// CURRENT — WRONG for M > 0.8:
double pgFactor = (mach < 0.8) ? 1.0 / Math.sqrt(1.0 - mach * mach) : 1.0;
```

For M > 1, the correct factor is `1/sqrt(M²-1)` (Ackeret linearized supersonic theory).
Setting it to 1.0 at all transonic/supersonic speeds under-predicts CN for M ∈ (0.8, 1.0)
and is wrong for M > 1.2.

### Replacement code

Replace **only** the `pgFactor` computation block inside the `CN` method. Locate the
existing single line and replace it with the following three-regime block:

```java
// Three-regime compressibility factor for fin CN slope.
// Subsonic:     Prandtl-Glauert  1/sqrt(1 - M²)
// Transonic:    smooth hermite blend (avoids singularity at M=1)
// Supersonic:   Ackeret          1/sqrt(M² - 1)
final double pgFactor;
if (mach <= 0.8) {
    double beta2 = Math.max(1.0 - mach * mach, 0.01); // floor prevents singularity
    pgFactor = 1.0 / Math.sqrt(beta2);
} else if (mach < 1.2) {
    // Evaluate subsonic and supersonic limits at the blend boundaries
    double subAt08 = 1.0 / Math.sqrt(Math.max(1.0 - 0.8 * 0.8, 0.01));
    double supAt12 = 1.0 / Math.sqrt(Math.max(1.2 * 1.2 - 1.0, 0.01));
    // Hermite smoothstep: t=0 at M=0.8, t=1 at M=1.2
    double t = (mach - 0.8) / 0.4;
    double smooth = t * t * (3.0 - 2.0 * t);
    pgFactor = subAt08 * (1.0 - smooth) + supAt12 * smooth;
} else {
    double beta2 = Math.max(mach * mach - 1.0, 0.01); // floor prevents singularity at M→1⁺
    pgFactor = 1.0 / Math.sqrt(beta2);
}
```

### Also fix `FinDragModel.cdFinInducedDrag` — same bug exists there

```
src/main/java/info/openrocket/core/aerodynamics/rom/core/physics/FinDragModel.java
```

Line 63 contains:
```java
double pgFactor = (mach < 0.8) ? 1.0 / Math.sqrt(1.0 - mach * mach) : 1.0;
```

Apply the identical three-regime replacement. The variable name `pgFactor` is the same;
only the computation block changes. The surrounding code (`cl = cnAlpha * alphaEff * ...`)
remains unchanged.

### Verification

Write inline assertions (or a unit test class if the project has a test module) confirming:

```
mach=0.5  → pgFactor ≈ 1.155   (1/sqrt(1-0.25))
mach=0.8  → pgFactor ≈ 1.667   (1/sqrt(0.36))
mach=1.0  → pgFactor ≈ 1.389   (midpoint of blend, not 1.0)
mach=1.2  → pgFactor ≈ 2.294   (1/sqrt(0.19) ≈ 2.294... wait, let's compute:
                                  sqrt(1.44-1)=sqrt(0.44)≈0.663, 1/0.663≈1.508)
                                  Actually: 1/sqrt(max(1.2²-1,0.01))=1/sqrt(0.44)≈1.508
mach=2.0  → pgFactor ≈ 0.577   (1/sqrt(3))
mach=4.0  → pgFactor ≈ 0.258   (1/sqrt(15))
```

Confirm continuity: the value at mach=0.8 computed by the subsonic branch equals the
blend start value (both ≈ 1.667). The value at mach=1.2 computed by the supersonic
branch equals the blend end value (both ≈ 1.508).

---

## Task 3 — Replace trilinear interpolation with tensor-product PCHIP

**Priority: HIGH — trilinear is C⁰ (discontinuous gradients at cell boundaries in Re,
alpha, beta). The ODE integrator in the trajectory solver will see kinks in the drag
force as the rocket transitions between cells.**

### File to modify

```
src/main/java/info/openrocket/core/aerodynamics/rom/core/surface/AeroSurface4DInterpolator.java
```

### Background

The current `trilinear()` private method evaluates the PCHIP interpolant at 8 surrounding
corners and blends with linear weights in Re, alpha, beta. This is O(8) evaluations with
C⁰ blending.

The replacement strategy is **tensor-product PCHIP**: for a query point (mach, logRe,
alphaDeg, betaDeg), construct short 1D PCHIP interpolants on-the-fly across each axis in
sequence. Since all axis arrays are short (N_RE=20, N_ALPHA≤13, N_BETA≤9), constructing
a `PchipInterpolator1D` over them is fast (O(n) construction, O(1) evaluation).

### New method to add

Add the following private method to `AeroSurface4DInterpolator`. It replaces `trilinear`
— do not delete `trilinear` yet, leave it commented out until Task 3 verification passes,
then delete it.

```java
/**
 * Tensor-product PCHIP interpolation in all four dimensions.
 *
 * Execution order:
 *   1. Evaluate all (nR × nA × nB) PCHIP-in-Mach slices at the query mach.
 *      This yields a dense 3D array of scalars.
 *   2. For each (ia, ib) pair, fit a PCHIP over the nR logRe values and
 *      evaluate at the query logRe. Yields a 2D (nA × nB) array.
 *   3. For each ib, fit a PCHIP over the nA alpha values and evaluate.
 *      Yields a 1D (nB) array.
 *   4. Fit a PCHIP over the nB beta values and evaluate. Returns a scalar.
 *
 * Cost: nR*nA*nB PCHIP evaluations (step 1, O(log n) each via binary search)
 *     + nA*nB short PCHIP constructions + evaluations (step 2, O(nR) each)
 *     + nB short PCHIP constructions + evaluations (step 3, O(nA) each)
 *     + 1 short PCHIP construction + evaluation (step 4, O(nB))
 * At grid size 20×13×9 this is fast and dominates less than 5 µs per full query.
 *
 * @param slices  pre-built PCHIP-in-Mach interpolants, indexed [ir][ia][ib]
 * @param mach    freestream Mach number (clamped externally before calling)
 * @param logRe   log10 of Reynolds number (clamped externally)
 * @param alphaDeg  angle of attack in degrees (absolute value, clamped externally)
 * @param betaDeg   sideslip angle in degrees (absolute value, clamped externally)
 */
private double tensorPchip(
        PchipInterpolator1D[][][] slices,
        double mach,
        double logRe,
        double alphaDeg,
        double betaDeg) {

    int nR = surface.logReAxis.length;
    int nA = surface.alphaAxis.length;
    int nB = surface.betaAxis.length;

    // Step 1: evaluate PCHIP-in-Mach at every grid node → 3D array
    double[][][] v = new double[nR][nA][nB];
    for (int ir = 0; ir < nR; ir++) {
        for (int ia = 0; ia < nA; ia++) {
            for (int ib = 0; ib < nB; ib++) {
                v[ir][ia][ib] = slices[ir][ia][ib].evaluate(mach);
            }
        }
    }

    // Step 2: PCHIP in logRe at each (ia, ib) → 2D array
    double[][] w = new double[nA][nB];
    double[] reCol = new double[nR];
    for (int ia = 0; ia < nA; ia++) {
        for (int ib = 0; ib < nB; ib++) {
            for (int ir = 0; ir < nR; ir++) {
                reCol[ir] = v[ir][ia][ib];
            }
            w[ia][ib] = new PchipInterpolator1D(surface.logReAxis, reCol).evaluate(logRe);
        }
    }

    // Step 3: PCHIP in alpha at each ib → 1D array
    if (nB == 1) {
        // No beta dimension — skip step 4
        double[] aCol = new double[nA];
        for (int ia = 0; ia < nA; ia++) {
            aCol[ia] = w[ia][0];
        }
        double result = new PchipInterpolator1D(surface.alphaAxis, aCol).evaluate(alphaDeg);
        return Double.isFinite(result) ? result : 0.0;
    }

    double[] betaRow = new double[nB];
    double[] aCol = new double[nA];
    for (int ib = 0; ib < nB; ib++) {
        for (int ia = 0; ia < nA; ia++) {
            aCol[ia] = w[ia][ib];
        }
        betaRow[ib] = new PchipInterpolator1D(surface.alphaAxis, aCol).evaluate(alphaDeg);
    }

    // Step 4: PCHIP in beta → scalar
    double result = new PchipInterpolator1D(surface.betaAxis, betaRow).evaluate(betaDeg);
    return Double.isFinite(result) ? result : 0.0;
}
```

### Update `query()` to use the new method

In the `query()` method, replace **all five** `trilinear(...)` calls with `tensorPchip(...)`.

**Current call pattern (find all five):**
```java
trilinear(slicesCdOff,  mach, ir0, ia0, ib0, tr, ta, tb)
trilinear(slicesCdOn,   mach, ir0, ia0, ib0, tr, ta, tb)
trilinear(slicesCdBody, mach, ir0, ia0, ib0, tr, ta, tb)
trilinear(slicesCN,     mach, ir0, ia0, ib0, tr, ta, tb)
trilinear(slicesCm,     mach, ir0, ia0, ib0, tr, ta, tb)
```

**Replacement call pattern:**
```java
tensorPchip(slicesCdOff,  mach, logRe, alphaAbs, betaClamped)
tensorPchip(slicesCdOn,   mach, logRe, alphaAbs, betaClamped)
tensorPchip(slicesCdBody, mach, logRe, alphaAbs, betaClamped)
tensorPchip(slicesCN,     mach, logRe, alphaAbs, betaClamped)
tensorPchip(slicesCm,     mach, logRe, alphaAbs, betaClamped)
```

Note that `logRe`, `alphaAbs`, and `betaClamped` are already computed at the top of
`query()`. Remove the lines that compute `ir0`, `ia0`, `ib0`, `tr`, `ta`, `tb`
after confirming no other method references them.

Also remove the `floor()` and `frac()` private helper methods, since `tensorPchip`
does not use them (PCHIP construction handles interval location internally).

### Verification

1. Compile cleanly.
2. For a representative geometry, query the same point 20 times as Mach sweeps from
   0.95 to 1.05 in steps of 0.005. The Cd values must be monotone-ish (drag rising then
   falling through transonic). No abrupt step changes that would indicate a cell boundary.
3. Query at exact grid nodes (alpha=0.0 exactly, Re=1e6 exactly, beta=0.0 exactly) before
   and after the change. Values at grid nodes must be unchanged (PCHIP is interpolating,
   not approximating, so it reproduces grid values exactly).

---

## Task 4 — Increase grid resolution: N_BETA=9, non-uniform alpha axis

**Priority: HIGH — N_BETA=5 gives 12–15° cell spacing in sideslip, which is too coarse
for accurate fin-induced drag at moderate sideslip angles.**

### File to modify

```
src/main/java/info/openrocket/core/aerodynamics/rom/core/eval/AeroGridEvaluator4D.java
```

### Change 1: Update grid resolution constants

```java
// BEFORE:
public static final int N_MACH  = 60;
public static final int N_RE    = 20;
public static final int N_ALPHA = 10;
public static final int N_BETA  =  5;

// AFTER:
public static final int N_MACH  = 60;   // unchanged
public static final int N_RE    = 20;   // unchanged
public static final int N_ALPHA = 12;   // increased; see buildAlphaAxis() below
public static final int N_BETA  =  9;   // increased; ~5° spacing for 4-fin (0–45°)
```

### Change 2: Replace `buildAlphaAxis()` with non-uniform version

The current uniform alpha axis has equal 1.67° spacing. The AoA-dependent terms
(`cdBodyAoA` via `cdInduced`, `CN` via `NormalForceModel`) have largest curvature near
α=0 where induced drag is growing as α². Clustering points near 0° substantially reduces
interpolation error without increasing grid size.

```java
/**
 * Non-uniform alpha axis: clustered near 0° where induced drag curvature is highest.
 *
 * Points (degrees): 0, 0.5, 1, 2, 3, 4.5, 6, 7.5, 9, 11, 13, 15
 * 12 points total. Spacing grows from 0.5° near origin to 2° in the tail.
 *
 * The first point MUST be 0.0 — enforced by AeroSurface4D.validateAxes().
 */
public static double[] buildAlphaAxis() {
    return new double[]{0.0, 0.5, 1.0, 2.0, 3.0, 4.5, 6.0, 7.5, 9.0, 11.0, 13.0, 15.0};
}
```

Note: `N_ALPHA` is used in the grid allocation (`new double[N_MACH][N_RE][N_ALPHA][nBeta]`).
Update it to match the array returned by `buildAlphaAxis()`. Since the axis is now
hardcoded (not built from `N_ALPHA`), the constant is used only for allocation. Either:
- Keep `N_ALPHA = 12` and ensure `buildAlphaAxis().length == 12` (they match).
- Or change `N_ALPHA` to `buildAlphaAxis().length` for safety — whichever pattern the
  surrounding code uses.

Confirm the inner loop `for (int ia = 0; ia < N_ALPHA; ia++)` in `evaluate()` still
iterates the correct number of times.

### Change 3: Update `buildBetaAxis()` for denser spacing

The beta axis is built from `N_BETA` and `maxBeta`. Increasing `N_BETA` to 9 automatically
gives denser spacing. No code change needed beyond the constant update in Change 1.

Verify the resulting axis spacing:
- 4-fin (maxBeta=45°): 9 points → 0, 5.625, 11.25, 16.875, 22.5, 28.125, 33.75, 39.375, 45
- 3-fin (maxBeta=60°): 9 points → 0, 7.5, 15, 22.5, 30, 37.5, 45, 52.5, 60

### Memory impact

Grid size before: 60 × 20 × 10 × 5 = 60,000 doubles × 5 grids = 300,000 doubles ≈ 2.3 MB
Grid size after:  60 × 20 × 12 × 9 = 129,600 doubles × 5 grids = 648,000 doubles ≈ 4.9 MB

Both compressed (via `AeroSurfaceSerializer`'s GZIP) to roughly 30–40% of raw size.
This is entirely acceptable. Document it in a comment above the constants.

### Verification

After the change, call `AeroGridEvaluator4D.buildAlphaAxis()` and assert:
- `length == 12`
- `axis[0] == 0.0`
- Strictly increasing (each element > previous)

Call `AeroGridEvaluator4D.buildBetaAxis(g)` for a 4-fin geometry and assert:
- `length == 9`
- `axis[0] == 0.0`
- `axis[8] == 45.0`

---

## Task 5 — Add LOO cross-validation metric to `AeroSurface4D` and `AeroGridEvaluator4D`

**Priority: HIGH — the existing `DragSurface.looRmsePercent` field was dropped from
`AeroSurface4D`, removing quality visibility.**

### Part A: Add `looRmsePercent` field to `AeroSurface4D`

**File:**
```
src/main/java/info/openrocket/core/aerodynamics/rom/core/surface/AeroSurface4D.java
```

1. Add the field declaration after `finCount`:
```java
public final double looRmsePercent;  // leave-one-out RMSE as % of mean Cd; 0.0 if not computed
```

2. Add it as the last parameter in the constructor:
```java
public AeroSurface4D(double[] machAxis, double[] logReAxis,
        double[] alphaAxis, double[] betaAxis,
        double[][][][] cdPlumeOff, double[][][][] cdPlumeOn,
        double[][][][] cdBody,
        double[][][][] CN, double[][][][] Cm,
        String geometryHash, int finCount,
        double looRmsePercent) {      // ← new parameter
    // ... existing assignments ...
    this.looRmsePercent = looRmsePercent;
    // validateAxes() call remains last
}
```

3. Update all call sites of the `AeroSurface4D` constructor. There are two:
   - `AeroGridEvaluator4D.evaluate()` — pass `0.0` as a placeholder (LOO is computed
     after build in Part B).
   - `AeroSurfaceSerializer.deserialize()` — pass `0.0` (LOO is not stored in the binary
     format; it is recomputed when needed).

### Part B: Add `computeMachLooRmse()` to `AeroGridEvaluator4D`

**File:**
```
src/main/java/info/openrocket/core/aerodynamics/rom/core/eval/AeroGridEvaluator4D.java
```

Add the following static method at the bottom of the class, before the closing brace:

```java
/**
 * Computes leave-one-out (LOO) RMSE over the Mach axis for the Cd_plume_off
 * surface at the first (Re=0, alpha=0, beta=0) grid node.
 *
 * For each Mach grid point m_i, a PCHIP is fit through all OTHER Mach points
 * and evaluated at m_i. The difference is the LOO error at that point.
 * RMSE is returned as a percentage of the mean Cd_off value.
 *
 * This is the axis with highest curvature (transonic peak) and therefore
 * the most informative single-axis quality metric.
 *
 * Requires nMach >= 4. Returns 0.0 for degenerate cases.
 *
 * @param surface a fully-built AeroSurface4D
 * @return LOO RMSE as percentage of mean, e.g. 0.8 means 0.8%
 */
public static double computeMachLooRmse(AeroSurface4D surface) {
    int nM = surface.machAxis.length;
    if (nM < 4) {
        return 0.0;
    }

    double[] cdFull = new double[nM];
    for (int im = 0; im < nM; im++) {
        cdFull[im] = surface.cdPlumeOff[im][0][0][0];
    }
    double mean = 0.0;
    for (double v : cdFull) {
        mean += v;
    }
    mean /= nM;
    if (mean < 1e-9) {
        return 0.0;
    }

    double ssq = 0.0;
    double[] xLoo = new double[nM - 1];
    double[] yLoo = new double[nM - 1];

    for (int leave = 0; leave < nM; leave++) {
        int j = 0;
        for (int i = 0; i < nM; i++) {
            if (i == leave) {
                continue;
            }
            xLoo[j] = surface.machAxis[i];
            yLoo[j] = cdFull[i];
            j++;
        }
        // Import is already in the same package via Task 1 (or use fully qualified name):
        // info.openrocket.core.aerodynamics.rom.core.surface.PchipInterpolator1D
        double predicted = new info.openrocket.core.aerodynamics.rom.core.surface
                .PchipInterpolator1D(xLoo, yLoo).evaluate(surface.machAxis[leave]);
        double err = predicted - cdFull[leave];
        ssq += err * err;
    }

    return 100.0 * Math.sqrt(ssq / nM) / mean;
}
```

### Part C: Wire LOO into the build pipeline

In `AeroGridEvaluator4D.evaluate(RomGeometryInput g, String geometryHash, ProgressListener progress)`,
update the `return` statement to compute and record LOO:

```java
// BEFORE:
return new AeroSurface4D(
        machAxis, logReAxis, alphaAxis, betaAxis,
        cdOff, cdOn, cdBody, cn, cm,
        geometryHash, g.finCount);

// AFTER:
AeroSurface4D built = new AeroSurface4D(
        machAxis, logReAxis, alphaAxis, betaAxis,
        cdOff, cdOn, cdBody, cn, cm,
        geometryHash, g.finCount,
        0.0);  // LOO computed below

double loo = computeMachLooRmse(built);

// AeroSurface4D is immutable (all fields final) so rebuild with LOO value:
return new AeroSurface4D(
        machAxis, logReAxis, alphaAxis, betaAxis,
        cdOff, cdOn, cdBody, cn, cm,
        geometryHash, g.finCount,
        loo);
```

Note: The double-construction is cheap — it only allocates a new wrapper object; no arrays
are copied. Alternatively, if immutability is a concern, make `looRmsePercent` non-final
with package-private setter — but prefer the double-construction approach to preserve the
clean immutable design.

### Part D: Update `AeroSurfaceSerializer` to round-trip `looRmsePercent`

The binary format does not need to change. The LOO value should be stored as a metadata
attribute at the XML/database layer (same pattern as `buildTimestampMs`). No changes to
`AeroSurfaceSerializer` are required. The `deserialize` call passes `0.0` as documented
in Part A.

### Verification

Build a surface for a representative geometry. Assert:
- `surface.looRmsePercent >= 0.0`
- `surface.looRmsePercent < 5.0` (anything above 5% indicates a problem with the physics
  model or a pathological geometry)
- The value is deterministic: build the same geometry twice and assert the LOO values
  are exactly equal (double equality).

---

## Task 6 — Add greedy Mach axis builder to `AeroGridEvaluator4D`

**Priority: HIGH — the current hardcoded piecewise-uniform axis places points based on
regime guesses, not on the actual drag curve shape for the specific geometry being built.**

### Background

The Empirical Interpolation Method (EIM) greedy algorithm from Quarteroni & Rozza
Chapter 3 (Bebendorf et al.) selects interpolation nodes by iteratively finding where
the current interpolant has the largest pointwise error. Applied here in 1D to the Mach
axis, it automatically concentrates points near the transonic drag rise, which shifts
with nose shape and fineness ratio.

This method runs entirely offline (once per geometry change). It costs O(N_MACH × N_CAND)
semi-empirical evaluations, which is fast.

### Add `buildMachAxisGreedy()` to `AeroGridEvaluator4D`

```java
/**
 * Greedy 1D Mach axis construction using the EIM algorithm
 * (Quarteroni & Rozza, "Reduced Order Methods", Ch.3, Bebendorf et al.).
 *
 * Starting from a small seed set, iteratively inserts the Mach number
 * where the current PCHIP interpolant's pointwise error is largest,
 * continuing until {@code nPoints} are placed or the maximum error falls
 * below {@code tolPercent} percent of the mean Cd value.
 *
 * The resulting axis concentrates nodes near the transonic drag rise,
 * which shifts with nose shape and fineness ratio, without any
 * hardcoded regime boundaries.
 *
 * @param g              rocket geometry
 * @param nPoints        target number of Mach axis points (recommended: 60–80)
 * @param logReTypical   log10(Re_L) representative of peak drag conditions;
 *                       use log10(5e5) ≈ 5.7 as a safe default
 * @param alphaRad       AoA in radians for axis construction (use 0.0 unless
 *                       building an AoA-specific axis)
 * @param tolPercent     early-stopping threshold: stop when max LOO error
 *                       falls below this percentage of mean Cd (e.g. 0.3)
 * @return strictly increasing double[] of Mach values covering [0.01, 4.0]
 */
public static double[] buildMachAxisGreedy(
        RomGeometryInput g,
        int nPoints,
        double logReTypical,
        double alphaRad,
        double tolPercent) {

    final int N_CAND = 600;  // dense candidate pool
    final double MACH_LO = 0.01;
    final double MACH_HI = 4.0;

    // Build candidate Mach pool: uniform spacing
    double[] cands = new double[N_CAND];
    for (int i = 0; i < N_CAND; i++) {
        cands[i] = MACH_LO + i * (MACH_HI - MACH_LO) / (N_CAND - 1);
    }

    // Pre-evaluate Cd_off at all candidate points (cheap — semi-empirical)
    double reL = Math.pow(10.0, logReTypical);
    double[] cdTrue = new double[N_CAND];
    double cdMean = 0.0;
    for (int i = 0; i < N_CAND; i++) {
        cdTrue[i] = computePoint(cands[i], reL, alphaRad, 0.0, g).cdPlumeOff;
        cdMean += cdTrue[i];
    }
    cdMean /= N_CAND;
    double tolAbs = (tolPercent / 100.0) * Math.max(cdMean, 1e-4);

    // Seed: always include endpoints and three interior anchors so the
    // initial PCHIP has a reasonable shape to refine from.
    java.util.List<Integer> chosen = new java.util.ArrayList<>(
            java.util.Arrays.asList(
                    0,                    // M=0.01
                    N_CAND / 5,          // M≈0.80 (approximate transonic onset)
                    N_CAND / 2,          // M≈2.0
                    3 * N_CAND / 4,      // M≈3.0
                    N_CAND - 1           // M=4.0
            ));

    // Track which candidates are already chosen, for O(1) skip
    boolean[] used = new boolean[N_CAND];
    for (int idx : chosen) {
        used[idx] = true;
    }

    // Greedy loop: insert the candidate with maximum interpolation error
    while (chosen.size() < nPoints) {
        // Build PCHIP from current chosen set
        int n = chosen.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        java.util.List<Integer> sorted = new java.util.ArrayList<>(chosen);
        java.util.Collections.sort(sorted);
        for (int k = 0; k < n; k++) {
            xs[k] = cands[sorted.get(k)];
            ys[k] = cdTrue[sorted.get(k)];
        }
        info.openrocket.core.aerodynamics.rom.core.surface.PchipInterpolator1D interp =
                new info.openrocket.core.aerodynamics.rom.core.surface.PchipInterpolator1D(xs, ys);

        // Find worst candidate
        double maxErr = -1.0;
        int worstIdx = -1;
        for (int i = 0; i < N_CAND; i++) {
            if (used[i]) {
                continue;
            }
            double err = Math.abs(interp.evaluate(cands[i]) - cdTrue[i]);
            if (err > maxErr) {
                maxErr = err;
                worstIdx = i;
            }
        }

        if (worstIdx < 0 || maxErr < tolAbs) {
            break;  // no improvement possible, or tolerance met
        }

        chosen.add(worstIdx);
        used[worstIdx] = true;
    }

    // Sort chosen indices by Mach value and extract axis
    chosen.sort(java.util.Comparator.comparingDouble(i -> cands[i]));
    double[] axis = new double[chosen.size()];
    for (int k = 0; k < chosen.size(); k++) {
        axis[k] = cands[chosen.get(k)];
    }
    return axis;
}
```

### Wire the greedy axis into `evaluate()` — opt-in via a flag

Do not unconditionally replace `buildMachAxis()` in the main `evaluate()` call yet —
the greedy build costs one extra sweep over 600 candidates, which is fast but represents
a behavioral change. Instead, add an overloaded `evaluate` that accepts a flag:

```java
/**
 * Evaluate with optional greedy Mach axis construction.
 *
 * @param useGreedyMachAxis  if true, use EIM greedy axis (geometry-adaptive);
 *                           if false, use the fixed piecewise-uniform axis.
 *                           Recommended: true for final production builds,
 *                           false for rapid development/testing.
 */
public static AeroSurface4D evaluate(
        RomGeometryInput g,
        String geometryHash,
        boolean useGreedyMachAxis,
        ProgressListener progress) {

    double[] machAxis = useGreedyMachAxis
            ? buildMachAxisGreedy(g, N_MACH, 5.7, 0.0, 0.3)
            : buildMachAxis();

    double[] logReAxis = buildLogReAxis();
    double[] alphaAxis = buildAlphaAxis();
    double[] betaAxis  = buildBetaAxis(g);
    int nBeta = betaAxis.length;

    // ... rest of the build loop is identical ...
}
```

Update the existing `evaluate(RomGeometryInput g, String geometryHash, ProgressListener progress)`
to delegate:

```java
public static AeroSurface4D evaluate(RomGeometryInput g, String geometryHash,
                                      ProgressListener progress) {
    return evaluate(g, geometryHash, false, progress);  // default: uniform axis
}
```

This allows callers to opt in with `evaluate(g, hash, true, progress)` once the greedy
axis has been validated.

### Verification

1. Build with `useGreedyMachAxis=true` for a 4-fin ogive rocket (e.g.
   `bodyLength=1.0m, maxDiameter=0.1m, noseLength=0.2m, finCount=4`).
2. Print the resulting Mach axis. Confirm:
   - First point ≈ 0.01, last point = 4.0.
   - Dense clustering in 0.7–1.3 (transonic region).
   - All 60 points present.
   - Strictly increasing.
3. Compare LOO RMSE between uniform and greedy axes (call `computeMachLooRmse` on both).
   The greedy axis should produce equal or lower LOO RMSE.

---

## Task 7 — Deprecate the legacy 3D system

**Priority: CRITICAL (for codebase hygiene) — the legacy `rom.*` classes use a different
geometry type (`RomGeometryParameters` with OpenRocket component dependencies) vs the
clean `RomGeometryInput` in `rom.core.geometry`. Having both active creates a maintenance
fork.**

### Files to annotate with `@Deprecated`

Add `@Deprecated` to the class declaration of each of the following. Do **not** delete
the files — deletion is a separate cleanup step that requires verifying no call sites
remain. The annotation is sufficient to surface usages in IDE warnings and CI.

```
rom/BaseDragModel.java              → @Deprecated on public class BaseDragModel
rom/DragGridEvaluator.java          → @Deprecated on public class DragGridEvaluator
rom/DragSurface.java                → @Deprecated on public class DragSurface
rom/DragSurfaceInterpolator.java    → @Deprecated on public class DragSurfaceInterpolator
rom/DragSurfaceSerializer.java      → @Deprecated on public class DragSurfaceSerializer
rom/InducedDragModel.java           → @Deprecated on public class InducedDragModel
rom/SkinFrictionModel.java          → @Deprecated on public class SkinFrictionModel
rom/TransonicBlendingModel.java     → @Deprecated on public class TransonicBlendingModel
rom/WaveDragModel.java              → @Deprecated on public class WaveDragModel
```

**Do NOT deprecate `rom/PchipInterpolator1D.java`** — it is still used by
`DragSurfaceInterpolator` which is itself deprecated. Deprecate the interpolator only
after removing the last consumer.

**Do NOT deprecate `rom/RomGeometryParameters.java`** — it contains the
`fromRocket(FlightConfiguration)` factory which is the live integration point with the
OpenRocket component tree. This class is the **bridge** between OR and the `rom.core`
system. Instead, add a Javadoc note explaining its role:

```java
/**
 * Bridge class: extracts geometry parameters from the live OpenRocket component
 * tree and packages them for use with the rom.core physics models via
 * {@link info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput}.
 *
 * <p>To convert: call {@link #toRomGeometryInput()} (see below) after constructing
 * via {@link #fromRocket(FlightConfiguration)}.
 */
```

### Add `toRomGeometryInput()` bridge method to `RomGeometryParameters`

This is the missing link between the OR component tree and the `rom.core` system.
Add the following method to `RomGeometryParameters`:

```java
/**
 * Converts this legacy geometry parameter object to the clean
 * {@link info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput}
 * required by all {@code rom.core.*} physics models and evaluators.
 *
 * Call this after constructing via {@link #fromRocket(FlightConfiguration)}.
 */
public info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput toRomGeometryInput() {
    info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape coreShape;
    switch (this.noseShape) {
        case CONICAL:    coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.CONICAL;    break;
        case OGIVE:      coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.OGIVE;      break;
        case VON_KARMAN: coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.VON_KARMAN; break;
        case PARABOLIC:  coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.PARABOLIC;  break;
        case ELLIPSOID:  coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.ELLIPSOID;  break;
        case HAACK:      coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.HAACK;      break;
        default:         coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.OGIVE;
    }
    return new info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput(
            this.bodyLength,
            this.maxDiameter,
            this.baseArea,
            this.wetArea,
            this.noseLength,
            coreShape,
            this.finessRatio,
            this.referenceArea,
            this.boattailLength,
            this.boattailBaseDiameter,
            this.finCount,
            this.finRootChord,
            this.finTipChord,
            this.finSpan,
            this.finThickness,
            this.finSweepAngle,
            this.finWettedArea,
            this.motorExitArea,
            this.surfaceRoughness);
}
```

### Verification

1. After adding `@Deprecated`, run `./gradlew compileJava -Xlint:deprecation`. The
   compiler output should show deprecation warnings only for internal self-references
   within the `rom.*` package. If warnings appear in `rom.core.*` files, those files
   have a stray import of the legacy system — fix them.

2. Confirm `RomGeometryParameters.toRomGeometryInput()` compiles. Write a trivial
   smoke test: construct a `RomGeometryInput` via `toRomGeometryInput()` and pass it
   to `AeroGridEvaluator4D.evaluate()`.

---

## Task 8 — Fix `SideslipModel.cdBodyCrossflow` Mach dependency

**Priority: MEDIUM — the crossflow drag coefficient `cdCf=1.2` is hardcoded as a
constant. In reality it decreases with Mach number above M≈0.5 as the crossflow
becomes compressible.**

### File to modify

```
src/main/java/info/openrocket/core/aerodynamics/rom/core/physics/SideslipModel.java
```

### Current code

```java
public static double cdBodyCrossflow(double betaRad, RomGeometryInput g) {
    if (Math.abs(betaRad) < 1e-6) {
        return 0.0;
    }
    double cdCf = 1.2;   // ← hardcoded, Mach-independent
    double sinBeta = Math.sin(betaRad);
    double diam = Math.max(g.maxDiameter, 1e-4);
    double areaRatio = 4.0 * g.bodyLength / (Math.PI * diam);
    return cdCf * areaRatio * sinBeta * sinBeta;
}
```

### Problem

`cdBodyCrossflow` does not accept a `mach` argument, but `cdSideslipIncrement` (its
caller) does. The fix requires:
1. Adding a `mach` parameter to `cdBodyCrossflow`.
2. Applying a Mach-dependent crossflow coefficient.
3. Updating the call site in `cdSideslipIncrement`.

### Mach-dependent crossflow coefficient

The crossflow drag coefficient for a cylinder at high angle of attack follows:
- M < 0.5: `cdCf ≈ 1.2` (incompressible)
- 0.5 ≤ M < 1.0: linearly reduces to ≈ 0.8 as local crossflow becomes transonic
- M ≥ 1.0: `cdCf ≈ 0.8` (supersonic crossflow, compressibility locks the value)

```java
/**
 * Mach-dependent normal-force coefficient for a circular cylinder in crossflow.
 * Approximate empirical fit (Jorgensen 1977, Hoerner 1965).
 *
 * @param mach freestream Mach number
 * @return dimensionless crossflow drag coefficient (referenced to frontal area per unit length)
 */
static double crossflowCdCf(double mach) {
    if (mach <= 0.5) {
        return 1.2;
    }
    if (mach < 1.0) {
        // Linear interpolation 1.2 → 0.8 over M=0.5..1.0
        double t = (mach - 0.5) / 0.5;
        return 1.2 - 0.4 * t;
    }
    return 0.8;
}
```

### Updated `cdBodyCrossflow` signature and body

```java
/**
 * Mach- and beta-dependent body crossflow drag contribution.
 *
 * @param betaRad sideslip angle in radians
 * @param mach    freestream Mach number
 * @param g       rocket geometry
 */
public static double cdBodyCrossflow(double betaRad, double mach, RomGeometryInput g) {
    if (Math.abs(betaRad) < 1e-6) {
        return 0.0;
    }
    double cdCf = crossflowCdCf(mach);
    double sinBeta = Math.sin(betaRad);
    double diam = Math.max(g.maxDiameter, 1e-4);
    double areaRatio = 4.0 * g.bodyLength / (Math.PI * diam);
    return cdCf * areaRatio * sinBeta * sinBeta;
}
```

### Update `cdSideslipIncrement` call site

```java
// BEFORE:
double dCdBody = cdBodyCrossflow(betaRad, g);

// AFTER:
double dCdBody = cdBodyCrossflow(betaRad, mach, g);
```

Note: `cdSideslipIncrement` already accepts `mach` — it is passed through unchanged.

### Verification

Assert for beta=30°, g=(bodyLength=1.0m, maxDiameter=0.1m):
```
mach=0.0 → cdBodyCrossflow(π/6, 0.0, g)  ≈ 1.2 * (4*1.0/(π*0.1)) * sin²(30°)
                                          = 1.2 * 12.732 * 0.25 ≈ 3.82
mach=0.75 → cdCf ≈ 1.2 - 0.4*0.5 = 1.0  → result ≈ 3.18
mach=2.0  → cdCf = 0.8               → result ≈ 2.55
```

Confirm `cdBodyCrossflow(beta, 0.0, g) == cdBodyCrossflow(beta, 0.5, g)` (both at
boundary point; value is 1.2 for mach≤0.5 and 1.2 for mach=0.5 before reduction starts).
Actually at mach=0.5 exactly: `t=(0.5-0.5)/0.5=0`, so `cdCf=1.2-0.0=1.2`. Correct.

---

## Task 9 — Improve `PitchingMomentModel.Cm` centre-of-pressure location

**Priority: MEDIUM — the current `xCp` uses fixed fractions (0.5×noseLength for body,
0.85×bodyLength for fins). The Barrowman equations give exact expressions.**

### File to modify

```
src/main/java/info/openrocket/core/aerodynamics/rom/core/physics/PitchingMomentModel.java
```

### Current (approximate) code

```java
double xCpNose = 0.5 * g.noseLength;
double xCpFins = 0.85 * g.bodyLength;
double finFraction = (g.finCount > 0) ? 0.6 : 0.0;
double xCp = (1.0 - finFraction) * xCpNose + finFraction * xCpFins;
return -CN * (xCp / g.bodyLength);
```

### Problems

- `0.5 * noseLength` is correct only for a conical nose. An ogive's CP is at ≈ 2/3
  of nose length.
- `0.85 * bodyLength` for fins is a crude estimate. Barrowman places fin CP at the
  aerodynamic centre of the fin planform, which depends on root chord, tip chord, and sweep.
- The fixed 60% weighting is not tied to the actual CN contributions.

### Improved implementation

```java
/**
 * Pitching moment coefficient Cm about the rocket nose tip.
 *
 * Uses Barrowman-derived CP locations:
 *   Nose: shape-dependent fraction of nose length
 *   Fins: aerodynamic centre of trapezoidal planform
 *
 * Weighted by the CN contributions of each component.
 *
 * @param CN       total normal force coefficient (from NormalForceModel)
 * @param alphaRad angle of attack in radians
 * @param g        rocket geometry
 * @return Cm (positive nose-up convention)
 */
public static double Cm(double CN, double alphaRad, RomGeometryInput g) {
    if (Math.abs(alphaRad) < 1e-8 || g.bodyLength < 1e-6 || Math.abs(CN) < 1e-10) {
        return 0.0;
    }

    // ---- Nose CP location (fraction of nose length from tip) ----
    // Barrowman: xCp_nose / L_nose
    //   Conical:    1/3
    //   Ogive:      0.466   (standard tangent ogive approximation)
    //   Von Karman: 0.500   (Sears-Haack body CP)
    //   Parabolic:  0.417
    //   Ellipsoid:  0.333   (same as conical for 2:1 ellipsoid)
    //   Haack:      0.500
    final double noseCpFraction;
    switch (g.noseShape) {
        case CONICAL:    noseCpFraction = 1.0 / 3.0; break;
        case OGIVE:      noseCpFraction = 0.466;      break;
        case VON_KARMAN: noseCpFraction = 0.500;      break;
        case PARABOLIC:  noseCpFraction = 0.417;      break;
        case ELLIPSOID:  noseCpFraction = 0.333;      break;
        case HAACK:      noseCpFraction = 0.500;      break;
        default:         noseCpFraction = 0.466;
    }
    double xCpNose = noseCpFraction * g.noseLength;

    // ---- Fin CP location (distance from nose tip) ----
    // Barrowman aerodynamic centre of trapezoidal fin:
    //   xAC_fin (from fin root leading edge) =
    //       (rootChord/3) * (1 + tipChord/rootChord) / (1 + tipChord/rootChord²... simplified:
    //   Use the standard mean aerodynamic chord quarter-chord location.
    //   For a trapezoid: MAC_LE = (rootChord/3)*(1 + 2*λ)/(1 + λ)  where λ = tipChord/rootChord
    //   xAC from MAC LE = 0.25 * MAC_chord
    double xCpFins = xCpNose; // default: no fins
    if (g.finCount > 0 && g.finRootChord > 1e-6) {
        double lambda = g.finTipChord / g.finRootChord;  // taper ratio
        // MAC leading edge offset from root LE (sweep component)
        double macLeOffset = (g.finRootChord / 3.0) * (1.0 + 2.0 * lambda) / (1.0 + lambda);
        // Fin root LE is approximately at (bodyLength - finRootChord - boattailLength)
        // Use a conservative estimate: fin mid-root at 0.85 * bodyLength from nose
        // This can be refined if fin axial position is available in RomGeometryInput.
        double finRootLeX = Math.max(0.0, g.bodyLength - g.finRootChord
                - Math.max(g.boattailLength, 0.0));
        // AC from nose = fin root LE + MAC LE offset + 0.25 * mean chord
        double cMean = (g.finRootChord + g.finTipChord) / 2.0;
        xCpFins = finRootLeX + macLeOffset + 0.25 * cMean;
        // Clamp to physical range
        xCpFins = Math.max(xCpNose, Math.min(xCpFins, g.bodyLength));
    }

    // ---- CN weighting ----
    // Body CN ~ 2*alpha (Barrowman), fin CN ~ the remainder.
    // Use the ratio to weight the two CP locations.
    double cnBody = 2.0 * Math.abs(alphaRad);
    double cnFin  = Math.max(0.0, Math.abs(CN) - cnBody);
    double cnTotal = cnBody + cnFin;
    double xCp = (cnTotal > 1e-10)
            ? (cnBody * xCpNose + cnFin * xCpFins) / cnTotal
            : xCpNose;

    // Cm = -CN * (xCp / Lref)   positive nose-up
    return -CN * (xCp / g.bodyLength);
}
```

### Verification

For a rocket with `noseLength=0.2m, bodyLength=1.0m, finRootChord=0.12m, finTipChord=0.06m,
finCount=4, boattailLength=0.0`:
- `xCpNose` (OGIVE) ≈ 0.466 × 0.2 = 0.0932 m
- `finRootLeX` ≈ 1.0 - 0.12 - 0.0 = 0.88 m
- `macLeOffset` = (0.12/3) × (1 + 2×0.5) / (1+0.5) = 0.04 × 2/1.5 ≈ 0.0533 m
- `cMean` = 0.09 m
- `xCpFins` ≈ 0.88 + 0.0533 + 0.0225 ≈ 0.956 m
- Result: fins near 95% of body length ✓ (rear-stable configuration)

Confirm `Cm(0, 0, g) == 0.0` and `Cm(CN, 1e-9, g) == 0.0` (near-zero alpha guard).

---

## Task 10 — Add `finAxialPosition` to `RomGeometryInput`

**Priority: MEDIUM — required to make Task 9's fin CP location exact rather than
estimated from `bodyLength - finRootChord`.**

### File to modify

```
src/main/java/info/openrocket/core/aerodynamics/rom/core/geometry/RomGeometryInput.java
```

### Change

Add one field and update the constructor:

```java
// New field (add after finWettedArea):
public final double finAxialPosition;  // distance from nose tip to fin root leading edge (m)
```

Update the constructor to accept this parameter. It is the second-to-last parameter
(before `motorExitArea`):

```java
public RomGeometryInput(
        double bodyLength, double maxDiameter, double baseArea,
        double wetArea, double noseLength, NoseShape noseShape,
        double finenessRatio, double referenceArea,
        double boattailLength, double boattailBaseDiameter,
        int finCount, double finRootChord, double finTipChord,
        double finSpan, double finThickness, double finSweepAngle,
        double finWettedArea,
        double finAxialPosition,      // ← new parameter
        double motorExitArea, double surfaceRoughness) {
    // ... all existing assignments ...
    this.finAxialPosition = finAxialPosition;
}
```

### Update `RomGeometryParameters.toRomGeometryInput()`

In the bridge method added in Task 7, pass the fin axial position. The legacy class
does not yet compute it from the component tree, so pass a conservative estimate:

```java
// Estimated fin leading edge: bodyLength - finRootChord - boattailLength
double finAxialEst = Math.max(0.0,
        this.bodyLength - this.finRootChord - Math.max(this.boattailLength, 0.0));
```

Pass `finAxialEst` as the `finAxialPosition` argument in the `new RomGeometryInput(...)` call.

### Update `AeroGridEvaluator4D.computeHash()`

Add the new field to the hash string to invalidate cached surfaces when fin position changes:

```java
// In the String.format call, add after finSweepAngle:
"%.6f|%.6f|%.6f|%.6f",  // existing fields
finAxialPosition,         // new field
```

### Update `PitchingMomentModel.Cm()`

Replace the estimated `finRootLeX` computation with the exact value:

```java
// BEFORE (estimated):
double finRootLeX = Math.max(0.0, g.bodyLength - g.finRootChord
        - Math.max(g.boattailLength, 0.0));

// AFTER (exact when available):
double finRootLeX = (g.finAxialPosition > 0.0)
        ? g.finAxialPosition
        : Math.max(0.0, g.bodyLength - g.finRootChord - Math.max(g.boattailLength, 0.0));
```

### Verification

Build and assert that `RomGeometryInput` with `finAxialPosition=0.88` and
`finAxialPosition=0.0` (triggering the fallback) produce consistent `Cm` values
in the same ballpark (within 5%).

---

## Task 11 — Validate end-to-end and update `CsvExporter`

**Priority: POLISH — ensures all the above tasks compose correctly and provides
diagnostic output.**

### The existing `CsvExporter` is already correct

Verify `CsvExporter.exportToCsv(AeroSurface4D, OutputStream)` compiles and runs with
the updated `AeroSurface4D` (which now has the `looRmsePercent` field from Task 5).
No code changes should be needed since `CsvExporter` does not reference `looRmsePercent`.

### Add a header comment row to CSV output

Modify the header line in `CsvExporter.exportToCsv` to include the LOO metric as a
comment (tools that read CSV will ignore lines starting with `#`):

```java
// BEFORE:
String header = "mach,log10Re,alpha_deg,beta_deg,Cd_plume_off,Cd_plume_on,Cd_body,dCd_fin,CN,Cm\n";

// AFTER:
String header = String.format(Locale.ROOT,
        "# AeroSurface4D export — geomHash=%s buildTimestampMs=%d looRmsePercent=%.4f%n"
        + "mach,log10Re,alpha_deg,beta_deg,Cd_plume_off,Cd_plume_on,Cd_body,dCd_fin,CN,Cm%n",
        surface.geometryHash,
        surface.buildTimestampMs,
        surface.looRmsePercent);
```

### Add a smoke-test `main` method to `AeroGridEvaluator4D`

This allows the agent (or a developer) to run a quick sanity check without a full test
suite. Place it at the very bottom of `AeroGridEvaluator4D`:

```java
/**
 * Smoke test: builds a surface for a representative 4-fin ogive rocket,
 * prints the LOO RMSE, and exports a Mach-sweep CSV to stdout.
 * Run with: java -cp ... info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D
 */
public static void main(String[] args) throws Exception {
    info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput g =
            new info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput(
                    1.0,    // bodyLength m
                    0.1,    // maxDiameter m
                    Math.PI * 0.0025,  // baseArea m^2
                    0.35,   // wetArea m^2
                    0.2,    // noseLength m
                    info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.OGIVE,
                    10.0,   // finenessRatio
                    Math.PI * 0.0025,  // referenceArea m^2
                    0.0,    // boattailLength
                    0.0,    // boattailBaseDiameter
                    4,      // finCount
                    0.12,   // finRootChord m
                    0.06,   // finTipChord m
                    0.06,   // finSpan m
                    0.003,  // finThickness m
                    Math.toRadians(30.0), // finSweepAngle rad
                    0.0216, // finWettedArea m^2  (approx 4 * 2 * 0.09*0.06/2)
                    0.88,   // finAxialPosition m
                    0.0,    // motorExitArea m^2
                    6.4e-6  // surfaceRoughness m
            );

    System.out.println("Building AeroSurface4D...");
    long t0 = System.currentTimeMillis();
    AeroSurface4D surface = evaluate(g, progress -> {
        if ((int)(progress * 20) % 5 == 0) {
            System.out.printf("  %.0f%%%n", progress * 100);
        }
    });
    long elapsed = System.currentTimeMillis() - t0;
    System.out.printf("Build complete in %d ms%n", elapsed);
    System.out.printf("LOO RMSE: %.4f%%%n", surface.looRmsePercent);
    System.out.printf("Grid: %d × %d × %d × %d%n",
            surface.machAxis.length, surface.logReAxis.length,
            surface.alphaAxis.length, surface.betaAxis.length);

    // Print Cd vs Mach at Re=1e6, alpha=2°, beta=0°
    var interp = new info.openrocket.core.aerodynamics.rom.core.surface
            .AeroSurface4DInterpolator(surface);
    System.out.println("mach,Cd_off,CN,Cm");
    for (double mach = 0.3; mach <= 4.0; mach += 0.1) {
        var r = interp.query(mach, 1e6, 2.0, 0.0);
        System.out.printf(java.util.Locale.ROOT,
                "%.2f,%.6f,%.6f,%.6f%n", mach, r.cdPlumeOff, r.CN, r.Cm);
    }
}
```

---

## Completion checklist

Run through each item before marking the work done. Every item must be ✓.

### Compilation
- [ ] `./gradlew compileJava` exits with code 0 (zero errors)
- [ ] No deprecation warnings in `rom.core.*` packages
- [ ] `PchipInterpolator1D` exists in both `rom` and `rom.core.surface` packages

### Task correctness
- [ ] **Task 1**: `AeroSurface4DInterpolator` compiles without any import for `PchipInterpolator1D`
- [ ] **Task 2**: `NormalForceModel.CN` pgFactor is `1/sqrt(M²-1)` for M=2.0 (value ≈ 0.577)
- [ ] **Task 2**: `FinDragModel.cdFinInducedDrag` has the same three-regime fix applied
- [ ] **Task 3**: `trilinear()` method is deleted from `AeroSurface4DInterpolator`
- [ ] **Task 3**: `floor()` and `frac()` private helpers are deleted from `AeroSurface4DInterpolator`
- [ ] **Task 3**: Query at exact grid node returns same value before/after the Task 3 change
- [ ] **Task 4**: `buildAlphaAxis()` returns exactly 12 elements, first is 0.0
- [ ] **Task 4**: `buildBetaAxis()` for 4-fin returns exactly 9 elements, first 0.0, last 45.0
- [ ] **Task 4**: `N_ALPHA == buildAlphaAxis().length` (no off-by-one in grid allocation)
- [ ] **Task 5**: `AeroSurface4D.looRmsePercent` field exists and is `0.0 ≤ value < 5.0` for realistic geometry
- [ ] **Task 6**: `buildMachAxisGreedy()` is present and callable
- [ ] **Task 6**: Greedy axis LOO RMSE ≤ uniform axis LOO RMSE for the smoke-test geometry
- [ ] **Task 7**: All listed legacy classes carry `@Deprecated` annotation
- [ ] **Task 7**: `RomGeometryParameters.toRomGeometryInput()` is present and callable
- [ ] **Task 8**: `SideslipModel.cdBodyCrossflow` accepts a `mach` parameter
- [ ] **Task 8**: `cdBodyCrossflow(beta, 2.0, g) < cdBodyCrossflow(beta, 0.3, g)` (decreasing with Mach)
- [ ] **Task 9**: `PitchingMomentModel.Cm` uses `noseCpFraction` switch on `g.noseShape`
- [ ] **Task 10**: `RomGeometryInput` constructor has `finAxialPosition` parameter
- [ ] **Task 11**: Smoke-test `main` runs and prints LOO RMSE and Cd vs Mach table without exceptions

### Regression
- [ ] `AeroSurfaceSerializer.serialize()` / `deserialize()` round-trip on a built surface
      produces an object that queries to equal Cd values (test at 5 sample points)
- [ ] `CsvExporter.exportToCsv()` produces a valid CSV with the geometry hash comment line

---

## Notes for the agent

**Order strictly matters.** Do not attempt Task 3 before Task 1 — the
`AeroSurface4DInterpolator` won't compile without the `PchipInterpolator1D` in the
correct package. Do not attempt Task 5 before Task 1 either, as the LOO computation
in Task 5 also instantiates `PchipInterpolator1D` from the `rom.core.surface` package.

**Preserve all existing test coverage.** If any test class references a constructor
or method signature being changed (Tasks 5 and 10 add constructor parameters), update
those tests to pass the new argument. Do not delete tests.

**Do not "improve" unchanged files.** If a file is not listed as a target for a task,
do not reformat, rename variables, or alter logic in it. Unnecessary diffs make review
harder.

**Use `Locale.ROOT` in all `String.format` calls.** The codebase uses this consistently;
do not introduce default-locale formatting.

**Array reuse in `tensorPchip`.** The temporary arrays `reCol` and `aCol` inside
`tensorPchip` are allocated on every call. If profiling reveals this is a hotspot
(unlikely — queries happen at most thousands of times per trajectory simulation), they
can be promoted to instance fields with length guards. Do not prematurely optimize.

**`AeroSurface4D` immutability.** All fields are `final`. The double-construction
in Task 5 (build with LOO=0.0, compute LOO, rebuild with actual LOO) is intentional
and correct. Do not attempt to work around it by making fields non-final.
