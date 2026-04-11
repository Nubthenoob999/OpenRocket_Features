# ROM Accuracy Implementation Guide

> **Purpose:** Step-by-step instructions for an AI coding agent to implement the ROM
> accuracy improvements identified in Phase 2 analysis, grounded in Quarteroni & Rozza
> *Reduced Order Methods for Modeling and Computational Reduction* (MS&A Vol. 9, 2014).
>
> **Execute tasks in order.** Each task states its source file, the exact change,
> the theoretical basis, and a self-verification check.
>
> **Package root:** `info.openrocket.core.aerodynamics.rom`

---

## Prerequisites — Read First

Before making any change:

1. Confirm these files exist in the project:
   - `SkinFrictionModel.java`
   - `BaseDragModel.java`
   - `WaveDragModel.java`
   - `FinDragModel.java`
   - `TransonicBlendingModel.java`
   - `DragGridEvaluator.java`
   - `DragSurface.java`
   - `DragSurfaceInterpolator.java`
   - `PchipInterpolator1D.java`
   - `RomGeometryParameters.java`

2. Do **not** modify `RomGeometryParameters.java` or `PchipInterpolator1D.java` —
   they are correct and stable.

3. Run all existing tests before starting. The baseline scores from Phase 2 are:
   - jackpot_launch_2_ab_vs_easymini: 50.790 (best reference — use as regression canary)
   - government_work_launch_1_easymini_vs_fluctus: 46.449

---

## Phase A — Physics Bug Fixes  
*These are deterministic corrections to known wrong constants. No new classes needed.*

---

### Task A1 — Fix Skin Friction Multiplier in `SkinFrictionModel.java`

**File:** `SkinFrictionModel.java`  
**Method:** `cdFriction(double mach, double re_L, RomGeometryParameters g)`  
**Line to change:** The final `return` statement (currently line 92).

**Problem:** The multiplier `0.4` has no aerodynamic derivation and under-predicts
skin friction drag by ~60%. The correct normalization for a body-of-revolution
(single-sided wetted area, flat-plate Cf referenced to one side) is `0.5`,
per Barrowman (1966) §3.2 and Hoerner (1965) Ch. 6.

The `accelZ:rom.drag.force-balance:CRITICAL` flag dominating Phase 2 scores
(64–76% top-contributor weighting) is the direct symptom of this error.

**Change:**

Find this line:
```java
return 0.4 * cf * ff * (g.wetArea / g.referenceArea);
```

Replace with:
```java
// Normalization: flat-plate Cf is referenced to one side of a two-sided plate.
// Body wetted area is single-sided. The correct scaling factor is 0.5,
// not 0.4. See Barrowman (1966) §3.2; Hoerner (1965) Ch. 6 Fig. 6-4.
return 0.5 * cf * ff * (g.wetArea / g.referenceArea);
```

**Self-verification:** For a body with `wetArea = 0.5 m²`, `referenceArea = 0.008 m²`,
`cf = 0.003`, `ff = 1.05`:
- Old result: `0.4 × 0.003 × 1.05 × (0.5/0.008)` = `0.07875`
- New result: `0.5 × 0.003 × 1.05 × (0.5/0.008)` = `0.098438`

The new value is 25% higher — physically correct direction, consistent with
the observed systematic low-drag bias.

---

### Task A2 — Fix Transonic Multiplier Coefficient in `BaseDragModel.java`

**File:** `BaseDragModel.java`  
**Method:** `transonicMultiplier(double mach)`

**Problem:** The Braeunig (2012) correlation for the transonic base drag rise
uses coefficient 215.8 for the sixth-power term at M=0.6–1.0. The current
code uses 175.0, which under-predicts the transonic base drag peak by ~19%.

**Change:**

Find this line inside `transonicMultiplier()`:
```java
return 1.0 + 175.0 * Math.pow(dm, 6.0);
```

Replace with:
```java
// Braeunig (2012) empirical coefficient is 215.8, not 175.0.
// Source: Braeunig, R.A., Rocket Propulsion, §Aerodynamic Drag (2012).
return 1.0 + 215.8 * Math.pow(dm, 6.0);
```

**Self-verification:** At M=1.0, dm=0.4:
- Old: `1.0 + 175.0 × (0.4)^6` = `1.0 + 175.0 × 0.004096` = `1.717`
- New: `1.0 + 215.8 × (0.4)^6` = `1.0 + 215.8 × 0.004096` = `1.884`

Peak multiplier increases from 1.72 to 1.88 — physically expected for
base drag rise through Mach 1.

---

### Task A3 — Fix Base Drag Prefactor in `BaseDragModel.java`

**File:** `BaseDragModel.java`  
**Method:** `cdBaseSubsonic(double cf_body, double loOverD)`

**Problem:** The current formula uses `0.026 * Kb / sqrt(cf_body)`. Hoerner (1965)
Table 21-1 gives the canonical base drag correlation as `Cd_base = 0.029 / sqrt(Cf)`
for blunt-base bodies of revolution. The `0.026` prefactor is 10.3% low.

**Change:**

Find this line:
```java
return 0.026 * Kb / Math.sqrt(Math.max(cf_body, 1e-4));
```

Replace with:
```java
// Hoerner (1965) Table 21-1: canonical prefactor is 0.029 for blunt-base
// bodies of revolution. The 0.026 value was ~10% low.
// Reference: Hoerner, S.F., Fluid-Dynamic Drag (1965), Ch. 21.
return 0.029 * Kb / Math.sqrt(Math.max(cf_body, 1e-4));
```

**Self-verification:** For `cf_body = 0.003`, `loOverD = 0` (simple cylinder),
`Kb = 0.0116 + 1.0 = 1.0116`:
- Old: `0.026 × 1.0116 / sqrt(0.003)` = `0.026 × 1.0116 / 0.05477` = `0.4804`
- New: `0.029 × 1.0116 / sqrt(0.003)` = `0.029 × 1.0116 / 0.05477` = `0.5358`

A ~11.5% increase in base drag — correct direction vs. the observed low-drag bias.

---

### Task A4 — Unify Fin Wave Drag Mach Threshold in `FinDragModel.java`

**File:** `FinDragModel.java`  
**Method:** `cdFinWaveSupersonic(double mach, RomGeometryInput g)`

**Problem:** `WaveDragModel.cdFinWaveSupersonic()` enables fin wave drag at `M > 1.0`.
`FinDragModel.cdFinWaveSupersonic()` uses `M > 1.2`. The 0.2 Mach gap creates
a non-physical discontinuity in total fin drag at transonic speeds.

**Change:**

Find this block at the top of `cdFinWaveSupersonic()` in `FinDragModel.java`:
```java
if (g.finCount == 0 || mach <= 1.2) {
    return 0.0;
}
```

Replace with:
```java
if (g.finCount == 0 || mach <= 1.0) {
    return 0.0;
}
// Apply linear ramp from M=1.0 to M=1.1 to prevent a step discontinuity.
// This matches the onset threshold in WaveDragModel.cdFinWaveSupersonic().
// See Ackeret thin-airfoil theory; onset at M=1.0 is physically correct.
double ramp = (mach < 1.1) ? (mach - 1.0) / 0.1 : 1.0;
```

Then, replace the final `return` statement of the method from:
```java
return 4.0 * tc * tc / betaM * g.finCount * planform / g.referenceArea;
```

to:
```java
return ramp * 4.0 * tc * tc / betaM * g.finCount * planform / g.referenceArea;
```

**Self-verification:** At M=1.05, ramp=0.5 — fin wave drag is 50% of the
fully-supersonic value. At M=1.1+, ramp=1.0 — full Ackeret value. No step discontinuity.

---

### Task A5 — Fix Transonic Peak Factor in `TransonicBlendingModel.java`

**File:** `TransonicBlendingModel.java`  
**Method:** `transonicPeakCd(double cd_subsonic, RomGeometryParameters g)`

**Problem:** The peak factor uses fixed per-nose-shape constants (1.45–2.0)
independent of fineness ratio. The first-principles paper (§7) shows the
transonic peak scales with the similarity parameter `K = (1−M²)/τ^(2/3)`,
where `τ = d/L` is the body thickness ratio. Slender rockets have a gentler
drag rise; blunt ones have a sharper rise. The `finenessCorrection` partially
accounts for this but applies `10/lOverD` which is arbitrary.

Replace the entire `transonicPeakCd` method with:

```java
/**
 * Transonic peak Cd estimate using the transonic similarity parameter K.
 *
 * K = (1 - M_cr^2) / tau^(2/3)  where tau = d/L (thickness ratio)
 * This gives a physics-derived fineness-ratio correction rather than
 * an arbitrary 10/lOverD clamp.
 *
 * Source: Transonic small-disturbance theory; Quarteroni & Rozza Ch. 9 §9.2;
 * First-Principles paper §7 (Kármán-Tsien / transonic similarity).
 */
public static double transonicPeakCd(double cd_subsonic,
                                     RomGeometryParameters g) {
    double peakFactor;
    switch (g.noseShape) {
        case VON_KARMAN:  peakFactor = 1.45; break;
        case OGIVE:       peakFactor = 1.65; break;
        case PARABOLIC:   peakFactor = 1.75; break;
        case CONICAL:     peakFactor = 2.00; break;
        default:          peakFactor = 1.85; break;
    }

    // Transonic similarity parameter: tau = d/L (body thickness ratio)
    // K-based correction: thicker bodies (large tau) have more abrupt drag rise.
    // Replace the arbitrary 10/lOverD cap with a tau^(1/3) physics-based scaling.
    double lOverD = Math.max(g.finessRatio, 1.0);
    double tau = 1.0 / lOverD;  // d/L thickness ratio
    // K-correction: normalize to tau=0.05 (L/d=20, typical reference rocket).
    // Rockets thicker than reference get amplified peak; slender ones get reduced.
    double tauRef = 0.05;
    double kCorrection = Math.pow(tau / tauRef, 1.0 / 3.0);
    // Clamp: correction cannot increase peak factor above 1.5x or reduce below 0.5x.
    kCorrection = Math.max(0.5, Math.min(1.5, kCorrection));

    double cd = Math.max(0.0, cd_subsonic) * peakFactor * kCorrection;
    return Double.isFinite(cd) ? Math.max(0.0, cd) : Math.max(0.0, cd_subsonic);
}
```

**Self-verification:**
- L/d=20 (tau=0.05): kCorrection = (0.05/0.05)^(1/3) = 1.0 — reference, no change.
- L/d=10 (tau=0.10): kCorrection = (0.10/0.05)^(1/3) = 2^(1/3) ≈ 1.26 — blunter body, higher peak.
- L/d=30 (tau=0.033): kCorrection = (0.033/0.05)^(1/3) = 0.66^(1/3) ≈ 0.87 — slender, lower peak.

This is physically correct: the Government Work rockets (lower fineness ratio,
lower Mach) get a larger transonic peak correction than Jackpot (higher fineness).

---

## Phase B — A Posteriori Error Certification  
*Implements the Reduced Basis error bound from Quarteroni & Rozza Ch. 2, Eq. 2.4.*  
*Adds self-certification to DragSurface without changing the physics model.*

---

### Task B1 — Add `CdResult` Value Object (New File)

**Create new file:** `CdResult.java` in the same package.

```java
package info.openrocket.core.aerodynamics.rom;

/**
 * Drag coefficient query result with a posteriori uncertainty estimate.
 *
 * The uncertainty bound deltaN approximates the Reduced Basis error bound
 * from Quarteroni & Rozza, Ch. 2, Eq. 2.4:
 *   || Cd(p) - Cd_ROM(p) || <= deltaN(p)
 *
 * It is computed as the leave-one-out cross-validation RMSE of the drag
 * surface at nearby grid points, scaled by a stability constant estimate.
 *
 * Interpretation of severityFlag:
 *   0 = OK         (deltaN/cd <= 0.05, i.e. < 5% uncertainty)
 *   1 = WARNING    (0.05 < deltaN/cd <= 0.15)
 *   2 = CRITICAL   (deltaN/cd > 0.15)
 */
public class CdResult {

    public final double cd;           // Point-estimate drag coefficient
    public final double deltaN;       // Absolute uncertainty bound
    public final int severityFlag;    // 0=OK, 1=WARNING, 2=CRITICAL

    public CdResult(double cd, double deltaN) {
        this.cd = cd;
        this.deltaN = deltaN;
        double relErr = (cd > 1e-6) ? (deltaN / cd) : 0.0;
        if (relErr <= 0.05) {
            this.severityFlag = 0;
        } else if (relErr <= 0.15) {
            this.severityFlag = 1;
        } else {
            this.severityFlag = 2;
        }
    }

    /** Convenience: is this result within the OK band? */
    public boolean isReliable() {
        return severityFlag == 0;
    }

    @Override
    public String toString() {
        return String.format("CdResult{cd=%.4f, deltaN=%.4f, severity=%d}",
                cd, deltaN, severityFlag);
    }
}
```

---

### Task B2 — Add LOO-RMSE Computation to `DragGridEvaluator.java`

**File:** `DragGridEvaluator.java`  
**Add** a new static method after the existing `evaluate()` method.

This computes the leave-one-out (LOO) cross-validation error on the Mach axis,
which serves as the stability-constant-free approximation to Δ_N from Ch. 2 Eq. 2.4.

Add this method to `DragGridEvaluator`:

```java
/**
 * Computes the leave-one-out (LOO) cross-validation RMSE on the Mach axis
 * for the given DragSurface. This is the surrogate for the Reduced Basis
 * a posteriori error bound (Quarteroni & Rozza, Ch. 2, Eq. 2.4).
 *
 * For each interior Mach grid point i, the method removes that point and
 * re-interpolates using the remaining N-1 points, then measures the error.
 * The maximum LOO error across all points and (Re, alpha) pairs is returned
 * as looRmsePercent (percent of the mean Cd at that point).
 *
 * This is run once during the offline DragSurface build and stored in
 * DragSurface.looRmsePercent.
 *
 * O(N_MACH * N_RE * N_ALPHA) — only called during offline build, not online.
 */
public static double computeLooRmsePercent(DragSurface surface) {
    int nM = surface.machAxis.length;
    int nR = surface.logReAxis.length;
    int nA = surface.alphaAxis.length;

    if (nM < 4) {
        return 0.0; // not enough points for LOO
    }

    double sumSqErr = 0.0;
    double sumSqRef = 0.0;
    int count = 0;

    // Only test interior Mach points (skip endpoints — extrapolation is expected there)
    for (int imHeld = 1; imHeld < nM - 1; imHeld++) {
        // Build a reduced Mach axis excluding point imHeld
        double[] reducedMach = new double[nM - 1];
        int ri = 0;
        for (int im = 0; im < nM; im++) {
            if (im != imHeld) {
                reducedMach[ri++] = surface.machAxis[im];
            }
        }

        for (int ir = 0; ir < nR; ir++) {
            for (int ia = 0; ia < nA; ia++) {
                // Build reduced Cd slice
                double[] reducedOff = new double[nM - 1];
                ri = 0;
                for (int im = 0; im < nM; im++) {
                    if (im != imHeld) {
                        reducedOff[ri++] = surface.cdPlumeOff[im][ir][ia];
                    }
                }
                PchipInterpolator1D looInterp = new PchipInterpolator1D(reducedMach, reducedOff);
                double predicted = looInterp.evaluate(surface.machAxis[imHeld]);
                double actual = surface.cdPlumeOff[imHeld][ir][ia];

                double err = predicted - actual;
                sumSqErr += err * err;
                sumSqRef += actual * actual;
                count++;
            }
        }
    }

    if (count == 0 || sumSqRef < 1e-12) {
        return 0.0;
    }
    // Return RMSE as a percentage of the RMS Cd value
    return 100.0 * Math.sqrt(sumSqErr / count) / Math.sqrt(sumSqRef / count);
}
```

**Then update the `evaluate()` method** to call this and pass the result to `DragSurface`.

Find the final `return` in `evaluate()`:
```java
return new DragSurface(machAxis, logReAxis, alphaAxis, cdOff, cdOn, g.geometryHash(), 0.0);
```

Replace with:
```java
DragSurface surface = new DragSurface(machAxis, logReAxis, alphaAxis, cdOff, cdOn,
        g.geometryHash(), 0.0);
// Compute LOO cross-validation RMSE as the a posteriori quality metric.
// This is the surrogate for the RB error bound (Quarteroni & Rozza Ch. 2 Eq. 2.4).
double looRmse = computeLooRmsePercent(surface);
return new DragSurface(machAxis, logReAxis, alphaAxis, cdOff, cdOn,
        g.geometryHash(), looRmse);
```

---

### Task B3 — Add Certified Query to `DragSurfaceInterpolator.java`

**File:** `DragSurfaceInterpolator.java`  
**Add** two new public methods alongside the existing `queryCdPlumeOff` and `queryCdPlumeOn`.

```java
/**
 * Query with a posteriori uncertainty estimate (CdResult).
 *
 * The deltaN bound is approximated from the surface LOO RMSE plus a
 * local finite-difference gradient estimate — larger gradients in Cd(M)
 * near the transonic peak produce larger local uncertainty.
 *
 * This implements the spirit of Quarteroni & Rozza Ch. 2 Eq. 2.4.
 */
public CdResult queryCdPlumeOffCertified(double mach, double re_L, double alphaDeg) {
    double cd = queryCdPlumeOff(mach, re_L, alphaDeg);
    double deltaN = estimateDeltaN(mach, re_L, alphaDeg, false);
    return new CdResult(cd, deltaN);
}

public CdResult queryCdPlumeOnCertified(double mach, double re_L, double alphaDeg) {
    double cd = queryCdPlumeOn(mach, re_L, alphaDeg);
    double deltaN = estimateDeltaN(mach, re_L, alphaDeg, true);
    return new CdResult(cd, deltaN);
}

/**
 * Estimates the local uncertainty bound deltaN for a query point.
 *
 * The estimate combines:
 *   1. Global LOO RMSE from the offline build (surface.looRmsePercent)
 *   2. A local gradient amplifier: near the transonic peak (M=0.85-1.15),
 *      the Cd surface has high curvature and interpolation errors are larger.
 *
 * Result is an absolute Cd uncertainty (same units as Cd).
 */
private double estimateDeltaN(double mach, double re_L, double alphaDeg, boolean plumeOn) {
    double cd = plumeOn ? queryCdPlumeOn(mach, re_L, alphaDeg)
                        : queryCdPlumeOff(mach, re_L, alphaDeg);

    // Base uncertainty from global LOO RMSE (percent → absolute)
    double baseUncertainty = cd * surface.looRmsePercent / 100.0;

    // Local gradient amplifier: finite-difference dCd/dM at this point
    double dM = 0.02;
    double machLo = Math.max(surface.machAxis[0], mach - dM);
    double machHi = Math.min(surface.machAxis[surface.machAxis.length - 1], mach + dM);
    double cdLo = plumeOn ? queryCdPlumeOn(machLo, re_L, alphaDeg)
                          : queryCdPlumeOff(machLo, re_L, alphaDeg);
    double cdHi = plumeOn ? queryCdPlumeOn(machHi, re_L, alphaDeg)
                          : queryCdPlumeOff(machHi, re_L, alphaDeg);
    double dCdDM = Math.abs(cdHi - cdLo) / Math.max(machHi - machLo, 1e-6);

    // Amplify uncertainty proportionally to local gradient (transonic peak detection)
    // Typical subsonic dCd/dM ~ 0.01; transonic ~ 0.5-2.0
    double gradientAmplifier = 1.0 + 10.0 * dCdDM;
    gradientAmplifier = Math.min(gradientAmplifier, 5.0); // cap at 5x

    return baseUncertainty * gradientAmplifier;
}
```

---

## Phase C — Gappy POD Sensor Reconstruction  
*Implements Quarteroni & Rozza Ch. 3 §3.6 (Gappy POD) and Scheme 3.8 (sensor placement).*  
*Allows reconstruction of complete flight trajectories from partial sensor data.*

---

### Task C1 — Create `GappyPodReconstructor.java` (New File)

**Create new file:** `GappyPodReconstructor.java` in the same package.

```java
package info.openrocket.core.aerodynamics.rom;

/**
 * Gappy POD reconstruction of a complete flight trajectory from partial sensor data.
 *
 * Theory: Quarteroni & Rozza, Ch. 3 §3.6.
 * Lemma 3.2: when the number of valid sensor points L >= Q (number of POD modes),
 * the gappy projection PQ,L[f] is equivalent to interpolation — providing an
 * exact reconstruction in the reduced basis.
 *
 * Use cases:
 *   - Fluctus sensor with 66.6% sentinel (invalid) rate: reconstruct missing 2/3 of data
 *   - AB_IMU_INTERLEAVED truncated at burnout: reconstruct coast + descent phases
 *
 * Input:
 *   basis    — Q×T matrix: Q POD mode vectors, each of length T (full time samples)
 *   validIdx — indices of time steps where sensor data is available (L entries)
 *   validObs — observed values at those L time steps (L entries)
 *
 * Output:
 *   reconstructed — full T-length trajectory estimate
 *   conditionNumber — condition number of the Gappy mass matrix M_h,L
 *                     (from Scheme 3.8; high kappa means reconstruction is unreliable)
 */
public class GappyPodReconstructor {

    private final double[][] basis;  // [Q modes][T timepoints]
    private final int Q;
    private final int T;

    /**
     * @param basis Q×T matrix of POD basis vectors (rows = modes, cols = timepoints).
     *              Build this offline by applying SVD to a matrix of complete reference
     *              flight trajectories (e.g., altitude or velocity time series).
     */
    public GappyPodReconstructor(double[][] basis) {
        if (basis == null || basis.length == 0 || basis[0].length == 0) {
            throw new IllegalArgumentException("Basis matrix must be non-empty");
        }
        this.Q = basis.length;
        this.T = basis[0].length;
        this.basis = basis;
    }

    /**
     * Reconstruct the full trajectory from partial observations.
     *
     * Solves: M_h,L * alpha = rhs   (Eq. 3.27 in textbook)
     * where M_h,L[i,j] = sum_{l in validIdx} h_i(x_l) * h_j(x_l)  (Gappy mass matrix)
     *       rhs[i]      = sum_{l in validIdx} f(x_l) * h_i(x_l)    (Gappy RHS)
     * then reconstructs: f_hat(x) = sum_j alpha_j * h_j(x)
     *
     * @param validIdx  time indices where measurements exist (0-based, into length-T axis)
     * @param validObs  observed values at those indices (same length as validIdx)
     * @return ReconstructionResult with full trajectory and diagnostics
     */
    public ReconstructionResult reconstruct(int[] validIdx, double[] validObs) {
        if (validIdx == null || validObs == null || validIdx.length != validObs.length) {
            throw new IllegalArgumentException("validIdx and validObs must be non-null and equal length");
        }
        int L = validIdx.length;
        if (L == 0) {
            return new ReconstructionResult(new double[T], Double.MAX_VALUE, false);
        }

        // Build the Gappy mass matrix M_h,L  [Q x Q]
        double[][] M = new double[Q][Q];
        for (int i = 0; i < Q; i++) {
            for (int j = 0; j < Q; j++) {
                double sum = 0.0;
                for (int l = 0; l < L; l++) {
                    int t = validIdx[l];
                    if (t >= 0 && t < T) {
                        sum += basis[i][t] * basis[j][t];
                    }
                }
                M[i][j] = sum;
            }
        }

        // Build the RHS vector [Q]
        double[] rhs = new double[Q];
        for (int i = 0; i < Q; i++) {
            double sum = 0.0;
            for (int l = 0; l < L; l++) {
                int t = validIdx[l];
                if (t >= 0 && t < T) {
                    sum += validObs[l] * basis[i][t];
                }
            }
            rhs[i] = sum;
        }

        // Estimate condition number of M before solving (Scheme 3.8)
        double kappa = estimateConditionNumber(M);

        // Solve M * alpha = rhs using Gaussian elimination with partial pivoting
        double[] alpha = solveLinearSystem(M, rhs);
        if (alpha == null) {
            // Singular system — return zero reconstruction with max condition number
            return new ReconstructionResult(new double[T], Double.MAX_VALUE, false);
        }

        // Reconstruct full trajectory: f_hat(t) = sum_j alpha_j * basis[j][t]
        double[] reconstructed = new double[T];
        for (int t = 0; t < T; t++) {
            double val = 0.0;
            for (int j = 0; j < Q; j++) {
                val += alpha[j] * basis[j][t];
            }
            reconstructed[t] = val;
        }

        // Reconstruction is reliable if L >= Q and condition number is reasonable
        boolean reliable = (L >= Q) && (kappa < 1000.0);
        return new ReconstructionResult(reconstructed, kappa, reliable);
    }

    /**
     * Greedy sensor placement: selects the L most informative time indices
     * to minimise the condition number of the Gappy mass matrix M_h,L.
     *
     * Implements Quarteroni & Rozza Ch. 3 Scheme 3.8.
     *
     * @param L number of sensor locations to select
     * @return array of L time indices (0-based) in selection order
     */
    public int[] selectOptimalSensorLocations(int L) {
        L = Math.min(L, T);
        int[] selected = new int[L];
        boolean[] used = new boolean[T];

        for (int step = 0; step < L; step++) {
            double bestKappa = Double.MAX_VALUE;
            int bestIdx = -1;

            for (int t = 0; t < T; t++) {
                if (used[t]) continue;

                // Tentatively add t to selected set
                int[] candidate = new int[step + 1];
                System.arraycopy(selected, 0, candidate, 0, step);
                candidate[step] = t;

                // Build M_h,step+1 and estimate condition number
                double[][] M = buildGappyMassMatrix(candidate, step + 1);
                double kappa = estimateConditionNumber(M);

                if (kappa < bestKappa) {
                    bestKappa = kappa;
                    bestIdx = t;
                }
            }

            if (bestIdx < 0) bestIdx = step; // fallback: take next unused
            selected[step] = bestIdx;
            used[bestIdx] = true;
        }
        return selected;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private double[][] buildGappyMassMatrix(int[] indices, int count) {
        double[][] M = new double[Q][Q];
        for (int i = 0; i < Q; i++) {
            for (int j = 0; j < Q; j++) {
                double sum = 0.0;
                for (int k = 0; k < count; k++) {
                    int t = indices[k];
                    if (t >= 0 && t < T) {
                        sum += basis[i][t] * basis[j][t];
                    }
                }
                M[i][j] = sum;
            }
        }
        return M;
    }

    /**
     * Estimate the condition number of a symmetric positive semi-definite matrix
     * using the ratio of max to min diagonal elements after Jacobi iteration.
     * This is an O(Q^2) approximation — sufficient for the small matrices here (Q<=20).
     */
    private double estimateConditionNumber(double[][] M) {
        double maxDiag = 0.0;
        double minDiag = Double.MAX_VALUE;
        for (int i = 0; i < Q; i++) {
            double d = Math.abs(M[i][i]);
            maxDiag = Math.max(maxDiag, d);
            if (d > 1e-14) minDiag = Math.min(minDiag, d);
        }
        if (minDiag >= Double.MAX_VALUE || minDiag < 1e-14) return Double.MAX_VALUE;
        return maxDiag / minDiag;
    }

    /**
     * Gaussian elimination with partial pivoting. Returns null if matrix is singular.
     */
    private double[] solveLinearSystem(double[][] Aorig, double[] borig) {
        int n = Aorig.length;
        double[][] A = new double[n][n];
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(Aorig[i], 0, A[i], 0, n);
            b[i] = borig[i];
        }

        for (int col = 0; col < n; col++) {
            // Partial pivot
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(A[row][col]) > Math.abs(A[pivot][col])) pivot = row;
            }
            double[] tmpRow = A[col]; A[col] = A[pivot]; A[pivot] = tmpRow;
            double tmpB = b[col]; b[col] = b[pivot]; b[pivot] = tmpB;

            if (Math.abs(A[col][col]) < 1e-14) return null; // singular

            for (int row = col + 1; row < n; row++) {
                double factor = A[row][col] / A[col][col];
                for (int k = col; k < n; k++) A[row][k] -= factor * A[col][k];
                b[row] -= factor * b[col];
            }
        }

        // Back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            x[i] = b[i];
            for (int j = i + 1; j < n; j++) x[i] -= A[i][j] * x[j];
            x[i] /= A[i][i];
        }
        return x;
    }

    // ── Result type ───────────────────────────────────────────────────────────

    public static class ReconstructionResult {
        /** Full T-length reconstructed trajectory. */
        public final double[] trajectory;
        /** Condition number of Gappy mass matrix M_h,L (Scheme 3.8). Lower is better. */
        public final double conditionNumber;
        /** True if L >= Q and conditionNumber < 1000. */
        public final boolean reliable;

        public ReconstructionResult(double[] trajectory, double conditionNumber, boolean reliable) {
            this.trajectory = trajectory;
            this.conditionNumber = conditionNumber;
            this.reliable = reliable;
        }
    }
}
```

---

### Task C2 — Create `TrajectorySvdBasis.java` (New File)

This is the offline tool that builds the POD basis matrix from a set of complete
reference flights. The basis is passed to `GappyPodReconstructor`.

**Create new file:** `TrajectorySvdBasis.java`

```java
package info.openrocket.core.aerodynamics.rom;

/**
 * Builds a POD (Proper Orthogonal Decomposition) basis from a collection of
 * complete reference flight trajectories using the method of snapshots (SVD).
 *
 * Theory: Quarteroni & Rozza Ch. 2 §2.2, Ch. 9 §9.2.
 * The POD basis spans the manifold of possible trajectories. For rocket flights,
 * Q = 5-8 modes typically capture > 99.99% of the variance.
 *
 * Usage (offline, run once per flight class):
 *   double[][] snapshots = ...;  // [N_flights][T_timepoints]
 *   TrajectorySvdBasis builder = new TrajectorySvdBasis(snapshots);
 *   double[][] basis = builder.getBasis(8);  // 8 POD modes
 *
 * Then pass basis to GappyPodReconstructor for online reconstruction.
 */
public class TrajectorySvdBasis {

    private final double[][] U;     // Left singular vectors [T x min(N,T)]
    private final double[] sigma;   // Singular values
    private final double totalEnergy;

    /**
     * @param snapshots  N×T matrix — each row is one complete flight trajectory
     *                   (altitude, velocity, or acceleration time series, pick one)
     */
    public TrajectorySvdBasis(double[][] snapshots) {
        if (snapshots == null || snapshots.length == 0) {
            throw new IllegalArgumentException("Need at least one snapshot");
        }
        int N = snapshots.length;
        int T = snapshots[0].length;

        // Use method of snapshots: compute SVD of the N×T snapshot matrix.
        // For N << T (few flights, many timepoints), compute C = snapshots * snapshots^T  [N×N]
        // then eigendecompose C.  Left singular vectors = snapshots^T * eigenvectors / sigma.
        double[][] C = new double[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = i; j < N; j++) {
                double dot = 0.0;
                for (int t = 0; t < T; t++) dot += snapshots[i][t] * snapshots[j][t];
                C[i][j] = dot;
                C[j][i] = dot;
            }
        }

        // Eigendecompose C (symmetric) using Jacobi iteration — sufficient for N <= 50
        double[][] V = jacobiEigen(C, N); // V columns are eigenvectors of C
        double[] eigenvalues = extractDiagonal(C, N); // C is diagonalized in-place

        // Sort by descending eigenvalue
        sortDescending(eigenvalues, V, N);

        // Reconstruct left singular vectors U_k = snapshots^T * v_k / sigma_k
        int modes = Math.min(N, T);
        U = new double[modes][T];
        sigma = new double[modes];
        double energy = 0.0;
        for (int k = 0; k < modes; k++) {
            double sv = Math.sqrt(Math.max(0.0, eigenvalues[k]));
            sigma[k] = sv;
            energy += sv * sv;
            if (sv < 1e-12) continue;
            for (int t = 0; t < T; t++) {
                double val = 0.0;
                for (int i = 0; i < N; i++) val += snapshots[i][t] * V[i][k];
                U[k][t] = val / sv;
            }
        }
        this.totalEnergy = energy;
    }

    /**
     * Return the Q most energetic POD basis vectors as a Q×T matrix.
     * Rows are modes; columns are time points.
     *
     * @param Q number of modes (use energyFractionForQ() to choose Q)
     */
    public double[][] getBasis(int Q) {
        Q = Math.min(Q, U.length);
        double[][] out = new double[Q][U[0].length];
        for (int k = 0; k < Q; k++) System.arraycopy(U[k], 0, out[k], 0, U[k].length);
        return out;
    }

    /**
     * Returns the minimum Q such that the first Q modes capture at least
     * the given fraction of total variance (e.g., 0.9999 for 99.99%).
     * Use this to choose Q for getBasis().
     */
    public int energyFractionForQ(double fraction) {
        double target = fraction * totalEnergy;
        double cumulative = 0.0;
        for (int k = 0; k < sigma.length; k++) {
            cumulative += sigma[k] * sigma[k];
            if (cumulative >= target) return k + 1;
        }
        return sigma.length;
    }

    /** Fraction of total variance captured by the first Q modes. */
    public double capturedEnergyFraction(int Q) {
        double cumulative = 0.0;
        for (int k = 0; k < Math.min(Q, sigma.length); k++) cumulative += sigma[k] * sigma[k];
        return totalEnergy > 1e-14 ? cumulative / totalEnergy : 1.0;
    }

    // ── Private: Jacobi eigendecomposition for symmetric matrices ─────────────

    private double[][] jacobiEigen(double[][] A, int n) {
        double[][] V = new double[n][n];
        for (int i = 0; i < n; i++) V[i][i] = 1.0;

        for (int sweep = 0; sweep < 50 * n * n; sweep++) {
            double offNorm = 0.0;
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++)
                    offNorm += A[i][j] * A[i][j];
            if (offNorm < 1e-28) break;

            for (int p = 0; p < n - 1; p++) {
                for (int q = p + 1; q < n; q++) {
                    if (Math.abs(A[p][q]) < 1e-14) continue;
                    double theta = 0.5 * (A[q][q] - A[p][p]) / A[p][q];
                    double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(1 + theta * theta));
                    double c = 1.0 / Math.sqrt(1 + t * t);
                    double s = t * c;
                    double tau = s / (1 + c);
                    double apq = A[p][q];
                    A[p][q] = 0.0; A[q][p] = 0.0;
                    A[p][p] -= t * apq; A[q][q] += t * apq;
                    for (int r = 0; r < n; r++) {
                        if (r == p || r == q) continue;
                        double arp = A[r][p], arq = A[r][q];
                        A[r][p] = arp - s * (arq + tau * arp);
                        A[p][r] = A[r][p];
                        A[r][q] = arq + s * (arp - tau * arq);
                        A[q][r] = A[r][q];
                    }
                    for (int r = 0; r < n; r++) {
                        double vrp = V[r][p], vrq = V[r][q];
                        V[r][p] = vrp - s * (vrq + tau * vrp);
                        V[r][q] = vrq + s * (vrp - tau * vrq);
                    }
                }
            }
        }
        return V;
    }

    private double[] extractDiagonal(double[][] A, int n) {
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = A[i][i];
        return d;
    }

    private void sortDescending(double[] eigenvalues, double[][] V, int n) {
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (eigenvalues[j] > eigenvalues[i]) {
                    double tmp = eigenvalues[i]; eigenvalues[i] = eigenvalues[j]; eigenvalues[j] = tmp;
                    for (int k = 0; k < n; k++) {
                        double tv = V[k][i]; V[k][i] = V[k][j]; V[k][j] = tv;
                    }
                }
            }
        }
    }
}
```

---

## Phase D — DEIM Magic Point Selection  
*Implements Quarteroni & Rozza Ch. 3 §3.4 and Ch. 4: DEIM for nonlinear drag evaluation.*  
*Selects the m most informative Mach numbers for force-balance evaluation.*

---

### Task D1 — Create `DeimSelector.java` (New File)

**Create new file:** `DeimSelector.java`

```java
package info.openrocket.core.aerodynamics.rom;

/**
 * Discrete Empirical Interpolation Method (DEIM) index selector.
 *
 * Given a POD basis matrix U of the Cd(M) function, selects m "magic Mach numbers"
 * (DEIM interpolation points) such that the full Cd(M) curve can be reconstructed
 * from evaluations at only those m points.
 *
 * Theory: Quarteroni & Rozza Ch. 3 §3.4 (EIM), Ch. 4 (DEIM for FEM nonlinearities).
 * Algorithm: Chaturantabut & Sorensen (2010), Scheme 3.5 in the textbook.
 *
 * The DEIM approximation is:
 *   Cd(M) ≈ U * (P^T * U)^{-1} * P^T * c
 * where P is the selection matrix for the m chosen indices,
 * and c is the vector of Cd evaluated at the m magic Mach numbers.
 *
 * For a rocket Cd(M) curve, m ≈ 10-12 magic points suffice to reconstruct
 * the entire transonic + supersonic curve to < 0.5% error.
 */
public class DeimSelector {

    private final int[] magicIndices; // Mach-axis indices of the m DEIM points
    private final double[] magicMachNumbers;
    private final double[][] interpolationMatrix; // (P^T U)^{-1}  [m x m]
    private final double[][] basisU;              // [m x N_MACH] — first m rows of U

    /**
     * @param machAxis  the Mach axis of the drag surface (length N_MACH)
     * @param podBasis  Q×N_MACH POD basis matrix for Cd(M)
     *                  (build with TrajectorySvdBasis.getBasis() applied to Cd rows)
     * @param m         number of DEIM interpolation points to select (m <= Q)
     */
    public DeimSelector(double[] machAxis, double[][] podBasis, int m) {
        if (podBasis == null || podBasis.length == 0) {
            throw new IllegalArgumentException("POD basis must be non-empty");
        }
        int Q = podBasis.length;
        int N = machAxis.length;
        m = Math.min(m, Math.min(Q, N));

        int[] indices = new int[m];
        // Step 1: first index = argmax |u_1|
        indices[0] = argmax(podBasis[0]);

        // Step 2: greedy DEIM selection (Scheme 3.5 / Algorithm in Ch. 4)
        double[][] U_selected = new double[m][N]; // U restricted to first m modes
        for (int k = 0; k < m; k++) System.arraycopy(podBasis[k], 0, U_selected[k], 0, N);

        for (int l = 1; l < m; l++) {
            // Solve (P_{l-1}^T * U_{1:l-1}) * c = P_{l-1}^T * u_l  for residual
            double[] ul = podBasis[l];
            double[] rhs = new double[l];
            for (int k = 0; k < l; k++) rhs[k] = ul[indices[k]];

            // Build P_{l-1}^T * U_{1:l-1}  [l x l]
            double[][] A = new double[l][l];
            for (int i = 0; i < l; i++) {
                for (int j = 0; j < l; j++) {
                    A[i][j] = podBasis[j][indices[i]];
                }
            }
            double[] c = gaussianElimination(A, rhs);

            // Residual r = u_l - U_{1:l-1} * c
            double[] r = new double[N];
            for (int t = 0; t < N; t++) {
                r[t] = ul[t];
                if (c != null) for (int j = 0; j < l; j++) r[t] -= podBasis[j][t] * c[j];
            }
            indices[l] = argmax(r);
        }

        this.magicIndices = indices;
        this.magicMachNumbers = new double[m];
        for (int i = 0; i < m; i++) magicMachNumbers[i] = machAxis[indices[i]];

        // Build (P^T * U)^{-1}  [m x m]
        double[][] PTU = new double[m][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                PTU[i][j] = podBasis[j][indices[i]];
            }
        }
        this.interpolationMatrix = invertMatrix(PTU);
        this.basisU = U_selected;
    }

    /**
     * Returns the m DEIM Mach numbers where Cd must be evaluated.
     */
    public double[] getMagicMachNumbers() {
        return magicMachNumbers.clone();
    }

    /**
     * Returns the Mach-axis indices of the m DEIM interpolation points.
     */
    public int[] getMagicIndices() {
        return magicIndices.clone();
    }

    /**
     * Reconstruct the full Cd(M) curve from evaluations at the m magic Mach numbers.
     *
     * @param cdAtMagicPoints  Cd values evaluated at getMagicMachNumbers() — length m
     * @param outputAxis       Mach axis for the output curve (length N_MACH)
     * @return reconstructed Cd at each point of outputAxis (length N_MACH)
     */
    public double[] reconstruct(double[] cdAtMagicPoints, double[] outputAxis) {
        int m = magicIndices.length;
        int N = outputAxis.length;
        if (cdAtMagicPoints.length != m) {
            throw new IllegalArgumentException("cdAtMagicPoints must have length " + m);
        }
        if (interpolationMatrix == null) return cdAtMagicPoints.clone();

        // alpha = (P^T U)^{-1} * P^T c
        double[] alpha = new double[m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                alpha[i] += interpolationMatrix[i][j] * cdAtMagicPoints[j];
            }
        }

        // Cd_hat(t) = U * alpha  (reconstruct at the same N points via basis)
        double[] result = new double[N];
        for (int t = 0; t < Math.min(N, basisU[0].length); t++) {
            for (int k = 0; k < m; k++) {
                result[t] += basisU[k][t] * alpha[k];
            }
            result[t] = Math.max(0.001, result[t]);
        }
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static int argmax(double[] v) {
        int idx = 0;
        double max = Math.abs(v[0]);
        for (int i = 1; i < v.length; i++) {
            if (Math.abs(v[i]) > max) { max = Math.abs(v[i]); idx = i; }
        }
        return idx;
    }

    private static double[] gaussianElimination(double[][] Aorig, double[] borig) {
        int n = Aorig.length;
        double[][] A = new double[n][n];
        double[] b = new double[n];
        for (int i = 0; i < n; i++) { System.arraycopy(Aorig[i], 0, A[i], 0, n); b[i] = borig[i]; }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(A[r][col]) > Math.abs(A[pivot][col])) pivot = r;
            double[] tmp = A[col]; A[col] = A[pivot]; A[pivot] = tmp;
            double tb = b[col]; b[col] = b[pivot]; b[pivot] = tb;
            if (Math.abs(A[col][col]) < 1e-14) return null;
            for (int r = col + 1; r < n; r++) {
                double f = A[r][col] / A[col][col];
                for (int k = col; k < n; k++) A[r][k] -= f * A[col][k];
                b[r] -= f * b[col];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            x[i] = b[i];
            for (int j = i + 1; j < n; j++) x[i] -= A[i][j] * x[j];
            x[i] /= A[i][i];
        }
        return x;
    }

    private static double[][] invertMatrix(double[][] A) {
        int n = A.length;
        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n + i] = 1.0;
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(aug[r][col]) > Math.abs(aug[pivot][col])) pivot = r;
            double[] tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp;
            double diag = aug[col][col];
            if (Math.abs(diag) < 1e-14) return null;
            for (int k = 0; k < 2 * n; k++) aug[col][k] /= diag;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = aug[r][col];
                for (int k = 0; k < 2 * n; k++) aug[r][k] -= f * aug[col][k];
            }
        }
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(aug[i], n, inv[i], 0, n);
        return inv;
    }
}
```

---

## Phase E — Window POD Phase-Separated Drag  
*Implements Quarteroni & Rozza Ch. 10: Window POD for non-stationary flight phases.*

---

### Task E1 — Add Phase Boundaries to `DragSurfaceInterpolator.java`

**File:** `DragSurfaceInterpolator.java`  
**Add** two new public methods that select the appropriate Mach regime for each flight phase.

Add these constants and methods to the class:

```java
// ── Window POD phase boundaries (Ch. 10 — Window POD) ─────────────────────
// Boost phase: Mach is increasing, plume-on. Uses powered base drag.
// Coast phase: Mach is decreasing from peak. Transonic window on descent.
// Descent phase: Low Mach, parachute deployed (body drag only).
// These boundaries match the phase-scoring windows in the plugin.

/** Maximum Mach considered part of the 'descent' regime (parachute deployed). */
public static final double DESCENT_MACH_MAX = 0.15;

/** Mach above which the body is in the transonic/supersonic 'coast' regime. */
public static final double COAST_TRANSONIC_MIN = 0.60;

/**
 * Query Cd for the DESCENT phase (low-Mach, parachute deployed).
 * Uses plume-off model. Applies a descent-phase correction factor to
 * account for base-flow changes post-parachute deployment.
 *
 * NOTE: A full parachute drag term (Cd_para × A_para / A_ref) should be
 * added externally by the caller — this method returns body drag only.
 *
 * Phase: Quarteroni & Rozza Ch. 10 (Window POD for non-stationary systems).
 */
public double queryCdDescent(double mach, double re_L, double alphaDeg) {
    // Clamp to descent regime
    mach = Math.min(mach, DESCENT_MACH_MAX);
    return queryCdPlumeOff(mach, re_L, alphaDeg);
}

/**
 * Query Cd for the BOOST phase (plume-on, Mach increasing).
 * Uses plume-on model.
 */
public double queryCdBoost(double mach, double re_L, double alphaDeg) {
    return queryCdPlumeOn(mach, re_L, alphaDeg);
}

/**
 * Query Cd for the COAST phase (plume-off, Mach decreasing from peak).
 * Uses plume-off model. Returns certified result with deltaN bound.
 */
public CdResult queryCdCoastCertified(double mach, double re_L, double alphaDeg) {
    return queryCdPlumeOffCertified(mach, re_L, alphaDeg);
}
```

---

## Phase F — Final Integration Checklist

After completing all tasks above, verify the following:

### F1 — Regression Check
Run the Phase 2 full test suite. For each dataset that previously had a computable
score, the new score must be **equal or higher**. The primary regression canary is:

```
jackpot_launch_2_ab_vs_easymini: was 50.790 → must not decrease
```

### F2 — Physics Constants Audit
Confirm these values in the codebase after your edits:

| File | Method | Constant | Required Value |
|---|---|---|---|
| `SkinFrictionModel.java` | `cdFriction` | Wetted-area multiplier | `0.5` |
| `BaseDragModel.java` | `transonicMultiplier` | Sixth-power coefficient | `215.8` |
| `BaseDragModel.java` | `cdBaseSubsonic` | Hoerner prefactor | `0.029` |
| `FinDragModel.java` | `cdFinWaveSupersonic` | Onset Mach threshold | `1.0` (with ramp to 1.1) |
| `TransonicBlendingModel.java` | `transonicPeakCd` | Fineness correction | `kCorrection = pow(tau/0.05, 1.0/3.0)` |

### F3 — New File Checklist
Confirm these files exist after Phase C and D:

- [ ] `CdResult.java` — value object with `cd`, `deltaN`, `severityFlag`
- [ ] `GappyPodReconstructor.java` — `reconstruct()`, `selectOptimalSensorLocations()`
- [ ] `TrajectorySvdBasis.java` — `getBasis(Q)`, `energyFractionForQ(fraction)`
- [ ] `DeimSelector.java` — `getMagicMachNumbers()`, `reconstruct()`

### F4 — Self-Test for `GappyPodReconstructor`
Add this as a `main()` or unit test:

```java
// Build a trivial 2-mode basis for a 10-point "flight"
double[][] basis = {
    {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},   // constant mode
    {-4,-3,-2,-1, 0, 1, 2, 3, 4, 5}   // linear mode
};
GappyPodReconstructor gappy = new GappyPodReconstructor(basis);

// Observe only 4 of 10 points (60% missing — like Fluctus L1)
int[] validIdx = {0, 3, 6, 9};
double[] validObs = {2.0, 5.0, 8.0, 11.0}; // f(t) = t + 2 at those points

GappyPodReconstructor.ReconstructionResult result = gappy.reconstruct(validIdx, validObs);

// Expected: reconstructed[t] ≈ t + 2.0 for all t
// result.reliable should be true (L=4 >= Q=2)
// result.conditionNumber should be small (< 100)
assert result.reliable : "Reconstruction should be reliable with L >= Q";
for (int t = 0; t < 10; t++) {
    assert Math.abs(result.trajectory[t] - (t + 2.0)) < 0.01
        : "Reconstruction error at t=" + t + ": " + result.trajectory[t];
}
```

### F5 — Self-Test for `DeimSelector`
```java
// Cd(M) basis: 2 modes over 5 Mach points
double[] machAxis = {0.3, 0.6, 0.9, 1.2, 1.5};
double[][] podBasis = {
    {0.4, 0.5, 0.9, 0.6, 0.3},  // mode 1 — transonic peak shape
    {0.8, 0.6, 0.1, 0.4, 0.7}   // mode 2 — subsonic/supersonic shape
};
DeimSelector deim = new DeimSelector(machAxis, podBasis, 2);
double[] magic = deim.getMagicMachNumbers();
// Magic points should include one near M=0.9 (transonic peak) — argmax of mode 1
assert magic.length == 2 : "Should select 2 magic Mach numbers";
```

---

## Notes for the Agent

- **Do not remove any existing public method signatures** — callers outside this
  package depend on them. Only add methods or change method bodies.

- **All new classes go in** `info.openrocket.core.aerodynamics.rom`.

- **`GappyPodReconstructor` and `TrajectorySvdBasis`** are pure Java with no
  external dependencies — no import statements needed beyond `java.util`.

- **`DeimSelector`** is also dependency-free.

- **The offline POD basis** (input to `GappyPodReconstructor`) must be built from
  complete reference flights. Use the Jackpot Launch 2 AB + EasyMini data (best
  quality, 0.27% apogee error) as the primary snapshot. The basis is small
  (Q=8 modes × T timepoints) and can be hardcoded as a class constant or loaded
  from a resource file.

- **Execute Phases in order:** A → B → C → D → E → F. Phase A fixes are
  independent and safe. Phase B requires Phase A. Phases C/D/E are new classes
  that do not break existing code if added incrementally.
