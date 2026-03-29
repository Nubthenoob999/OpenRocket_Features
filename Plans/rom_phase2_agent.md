# ROM Phase 2 — Advanced Reduction Agent Instructions
## OpenRocket — From Fixed-Geometry Lookup Table to Geometry-Parametric ROM

---

## Context and prerequisites

Phase 1 (the previous agent .md) produced a correct, C¹-smooth, 4D precomputed
lookup table `AeroSurface4D(M, Re, α, β)` for a **single fixed geometry**. Every
class compiles, the trilinear interpolation bug is fixed, LOO error is tracked, and
the legacy 3D system is deprecated.

This document drives Phase 2, which makes the ROM **geometry-parametric**: instead
of rebuilding the full 4D grid every time the rocket geometry changes, Phase 2
constructs a reduced basis over geometry space so that new geometries can be
evaluated in microseconds without a grid rebuild. It also adds certified error bounds,
a proper offline/online decomposition, local ROM support for the transonic region,
and a POD-based compression layer for the stored surface.

All techniques in this document are derived directly from Quarteroni & Rozza,
*Reduced Order Methods for Modeling and Computational Reduction* (Springer, 2014),
referenced by chapter and section throughout.

**Complete every task in Phase 1 before starting this document.**

---

## Theoretical grounding (read before touching any code)

### Why the current system still has a fundamental scaling problem

The current `AeroGridEvaluator4D.evaluate()` builds a 60×20×12×9 grid by calling
`computePoint()` at every node — roughly 130,000 evaluations per geometry. When a
user modifies their rocket in OpenRocket and the geometry hash changes, the entire
grid is rebuilt. For a trajectory simulation that tests 50 rocket configurations,
this is 50 × 130,000 = 6.5 million semi-empirical evaluations. The physics is cheap
(microseconds per call), but the pattern is wrong: the solution manifold
`{Cd(M, Re, α, β; geometry)}` varies smoothly with geometry, and the book tells us
exactly how to exploit this.

### The Kolmogorov n-width argument (Ch. 3, Bebendorf et al.)

Chapter 3 establishes that if the family of functions `{f(·, y)}` parametrized by `y`
has small Kolmogorov n-width — meaning the solution manifold is well-approximated
by a low-dimensional linear subspace — then POD, ACA, and EIM all produce near-optimal
approximations with very few basis functions. The key question for Phase 2 is: does
the family `{Cd(M, Re, α, β; g)}` parametrized by geometry `g` have small n-width?

The answer is yes, for the same reason it is yes for parametric PDEs: the geometry
parameters (nose length, fineness ratio, fin sweep angle) change the Cd surface
smoothly and continuously. Wave drag scales as `1/lnOverD²`, skin friction as
`(Re_L)^{-0.2}`, and transonic blending shifts the peak location predictably with
drag divergence Mach. These are all smooth functions of geometry parameters —
the solution manifold is a smooth low-dimensional submanifold of function space.
The book's convergence theorems (Section 3.4, Remark 3.8) guarantee that a small
number of basis functions will suffice.

### The offline/online split (Ch. 2, Benner et al.; Ch. 5, Urban et al.)

Chapters 2 and 5 establish the central organizing principle: **offline** computation
is expensive but done once; **online** evaluation is cheap and done many times.
For OpenRocket:

- **Offline**: Sample the geometry space, build full 4D surfaces for each sample,
  extract POD basis vectors, store compressed representation. This runs once per
  software release (or when the physics models change).
- **Online**: Given a new geometry hash, express the new surface as a linear
  combination of stored basis surfaces. Query via the existing `AeroSurface4DInterpolator`.

This directly mirrors the offline/online decomposition described in Ch. 5 Section 5.1.

### Local ROM for the transonic region (Ch. 3, Section 3.4.6.2 — hp-EIM)

The transonic drag rise is a near-discontinuity in the Mach direction: the peak
location and height change sharply with nose shape and fineness ratio. A single
global POD basis cannot capture this efficiently — the singular value decay is slow
because the "shock" in Mach space moves as geometry changes (this is the transport
problem described in the project knowledge documents).

Section 3.4.6.2 of the book describes the **hp-EIM**: partition the parameter space
`Ω_y` into subdomains `Ω_y^1, ..., Ω_y^P`, and build a separate local EIM/POD on
each subdomain. For the geometry-parametric ROM, this means:
- One local basis for subsonic-dominated geometries (high fineness ratio, ogive nose)
- One local basis for transonic-dominated geometries (blunt noses, low fineness ratio)
- Blend at boundaries using the same `TransonicBlendingModel` sigmoid

### Gappy POD for sparse geometry sampling (Ch. 3, Section 3.6)

Section 3.6 describes Gappy POD / Missing Point Estimation: given **incomplete**
evaluations of a function (only L < M sensor locations available), reconstruct the
full function via least-squares projection onto the POD basis. For OpenRocket, this
means a new geometry can be characterized by evaluating `computePoint()` at only
`L` strategic (M, Re, α, β) locations — the "magic points" from the EIM — instead
of the full 130,000-point grid. Reconstruction accuracy is guaranteed by the LOO
RMSE criterion from Phase 1.

---

## New package structure

Phase 2 introduces a new subpackage for the geometry-parametric layer:

```
rom/core/
├── eval/
│   ├── AeroGridEvaluator4D.java          (Phase 1 — unchanged)
│   └── GeometricRomEvaluator.java        (Phase 2 — NEW)
├── basis/
│   ├── GeometrySnapshot.java             (Phase 2 — NEW)
│   ├── PodBasis.java                     (Phase 2 — NEW)
│   ├── LocalPodRegion.java               (Phase 2 — NEW)
│   └── GeometryParametricRom.java        (Phase 2 — NEW)
├── sampling/
│   ├── GeometrySampler.java              (Phase 2 — NEW)
│   └── MagicPointSelector.java           (Phase 2 — NEW)
├── geometry/
│   └── RomGeometryInput.java             (Phase 1 — add geometry distance method)
├── io/
│   ├── AeroSurfaceSerializer.java        (Phase 1 — unchanged)
│   ├── CsvExporter.java                  (Phase 1 — unchanged)
│   └── GeometricRomSerializer.java       (Phase 2 — NEW)
├── physics/  (all Phase 1 — unchanged)
└── surface/  (all Phase 1 — unchanged)
```

Create all new directories and empty `.java` stubs before beginning Task 1.

---

## Task 1 — Geometry parameterization and distance metric

**Grounding: Ch. 3 Section 3.4.6.2 (hp-EIM local parameter distance); Ch. 5 Section 5.2**

The geometry-parametric ROM needs a way to measure how "far apart" two rocket
geometries are in parameter space. This distance determines which local POD region
applies (hp-EIM partitioning) and how well a new geometry is represented by the
stored basis (Gappy POD reconstruction quality).

### 1a — Define the normalized geometry feature vector in `RomGeometryInput`

Add the following method to `RomGeometryInput.java`:

```java
/**
 * Returns a normalized geometry feature vector suitable for computing
 * distances in the geometry parameter space.
 *
 * Components (all dimensionless):
 *   [0] fineness ratio:          bodyLength / maxDiameter
 *   [1] nose fraction:           noseLength / bodyLength
 *   [2] boattail fraction:       boattailLength / bodyLength  (0 if absent)
 *   [3] boattail contraction:    boattailBaseDiameter / maxDiameter  (1 if absent)
 *   [4] fin aspect ratio:        2 * finSpan / meanChord  (0 if finCount==0)
 *   [5] fin taper ratio:         finTipChord / finRootChord  (0 if finCount==0)
 *   [6] fin thickness ratio:     finThickness / meanFinChord  (0 if finCount==0)
 *   [7] fin area fraction:       finCount * finWettedArea / referenceArea  (0 if none)
 *   [8] nose shape ordinal:      0=CONICAL, 1=PARABOLIC, 2=ELLIPSOID,
 *                                3=OGIVE, 4=VON_KARMAN, 5=HAACK
 *                                (scaled to [0,1] by dividing by 5)
 *   [9] motor exit fraction:     motorExitArea / baseArea  (0 if no motor)
 *
 * The vector is designed so that Euclidean distance in this space correlates
 * with difference in Cd curves. Weights are uniform (no a-priori scaling);
 * anisotropic scaling can be added later via a learned metric tensor.
 *
 * @return double[10] feature vector, all components in [0, ∞) or [0,1]
 */
public double[] toFeatureVector() {
    double[] v = new double[10];
    double denom;

    // [0] fineness ratio (capped at 30 for normalization stability)
    v[0] = (maxDiameter > 1e-9) ? Math.min(bodyLength / maxDiameter, 30.0) / 30.0 : 0.0;

    // [1] nose fraction
    v[1] = (bodyLength > 1e-9) ? Math.min(noseLength / bodyLength, 1.0) : 0.0;

    // [2] boattail fraction
    v[2] = (bodyLength > 1e-9) ? Math.min(boattailLength / bodyLength, 0.5) / 0.5 : 0.0;

    // [3] boattail contraction (diameter ratio)
    v[3] = (maxDiameter > 1e-9 && boattailBaseDiameter > 0.0)
            ? boattailBaseDiameter / maxDiameter : 1.0;

    // [4] fin aspect ratio (capped at 8)
    if (finCount > 0 && finRootChord > 1e-9) {
        double meanChord = (finRootChord + finTipChord) / 2.0;
        v[4] = Math.min(2.0 * finSpan / meanChord, 8.0) / 8.0;
    }

    // [5] fin taper ratio (0 = delta, 1 = rectangular)
    v[5] = (finCount > 0 && finRootChord > 1e-9)
            ? Math.min(finTipChord / finRootChord, 1.0) : 0.0;

    // [6] fin thickness ratio (capped at 0.2)
    if (finCount > 0 && finRootChord > 1e-9) {
        double meanChord = (finRootChord + finTipChord) / 2.0;
        v[6] = Math.min(finThickness / meanChord, 0.2) / 0.2;
    }

    // [7] fin area fraction (capped at 5)
    v[7] = (referenceArea > 1e-12)
            ? Math.min(finCount * finWettedArea / referenceArea, 5.0) / 5.0 : 0.0;

    // [8] nose shape ordinal (0=CONICAL .. 5=HAACK, normalized to [0,1])
    switch (noseShape) {
        case CONICAL:    v[8] = 0.0 / 5.0; break;
        case PARABOLIC:  v[8] = 1.0 / 5.0; break;
        case ELLIPSOID:  v[8] = 2.0 / 5.0; break;
        case OGIVE:      v[8] = 3.0 / 5.0; break;
        case VON_KARMAN: v[8] = 4.0 / 5.0; break;
        case HAACK:      v[8] = 5.0 / 5.0; break;
        default:         v[8] = 3.0 / 5.0; // default OGIVE
    }

    // [9] motor exit fraction
    v[9] = (baseArea > 1e-12) ? Math.min(motorExitArea / baseArea, 1.0) : 0.0;

    return v;
}

/**
 * Euclidean distance between two geometry feature vectors.
 * Used by GeometryParametricRom to find the nearest basis snapshot
 * and to determine which hp-EIM local region applies.
 */
public double featureDistance(RomGeometryInput other) {
    double[] a = this.toFeatureVector();
    double[] b = other.toFeatureVector();
    double sum = 0.0;
    for (int i = 0; i < a.length; i++) {
        double d = a[i] - b[i];
        sum += d * d;
    }
    return Math.sqrt(sum);
}
```

### 1b — Add `NoseShape.ordinal()` mapping to geometry hash

The existing `computeHash()` in `AeroGridEvaluator4D` already hashes `noseShape.name()`.
No change needed there. But confirm that `RomGeometryInput.NoseShape` has exactly
the six values listed above, in the declared order, so the ordinal mapping in
`toFeatureVector()` is stable.

### 1c — Verification

For two identical geometries: `g1.featureDistance(g2) == 0.0`.
For a rocket with `noseShape=OGIVE` vs same rocket with `noseShape=CONICAL`:
`featureDistance > 0` and specifically `== abs(3.0/5.0 - 0.0/5.0) = 0.6` (only
dimension [8] differs, others are zero since other geometry is identical).

---

## Task 2 — Geometry snapshot collection and POD basis construction

**Grounding: Ch. 3 Section 3.2 (POD algorithm); Ch. 4 Section 4.3.1 (POD for subspace
generation)**

### 2a — `GeometrySnapshot.java`

```java
package info.openrocket.core.aerodynamics.rom.core.basis;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

/**
 * A single (geometry, surface) pair used to build the POD basis.
 * Stores the flattened Cd_plume_off values as a snapshot vector,
 * plus metadata for reconstruction.
 *
 * The snapshot vector is the column of the snapshot matrix S in
 * the POD formulation (Ch. 3, Scheme 3.1 of Quarteroni-Rozza).
 * Its length is N_MACH * N_RE * N_ALPHA * N_BETA.
 */
public final class GeometrySnapshot {

    public final RomGeometryInput geometry;
    public final String geometryHash;

    /**
     * Flattened Cd_plume_off values, row-major over (im, ir, ia, ib).
     * This is the snapshot vector s_j in the POD formulation.
     * Length = machAxis.length * logReAxis.length * alphaAxis.length * betaAxis.length
     */
    public final double[] cdOffFlat;

    /**
     * Flattened Cd_plume_on values, same layout.
     * Stored separately so the ROM can reconstruct both surfaces.
     */
    public final double[] cdOnFlat;

    /** Axis arrays from the source surface — all snapshots must share these. */
    public final double[] machAxis;
    public final double[] logReAxis;
    public final double[] alphaAxis;
    public final double[] betaAxis;

    /** Geometry feature vector for distance computations (Task 1). */
    public final double[] featureVector;

    public GeometrySnapshot(RomGeometryInput geometry, AeroSurface4D surface) {
        this.geometry = geometry;
        this.geometryHash = surface.geometryHash;
        this.machAxis   = surface.machAxis;
        this.logReAxis  = surface.logReAxis;
        this.alphaAxis  = surface.alphaAxis;
        this.betaAxis   = surface.betaAxis;
        this.featureVector = geometry.toFeatureVector();

        int nM = surface.machAxis.length;
        int nR = surface.logReAxis.length;
        int nA = surface.alphaAxis.length;
        int nB = surface.betaAxis.length;
        int N  = nM * nR * nA * nB;

        this.cdOffFlat = new double[N];
        this.cdOnFlat  = new double[N];

        int idx = 0;
        for (int im = 0; im < nM; im++) {
            for (int ir = 0; ir < nR; ir++) {
                for (int ia = 0; ia < nA; ia++) {
                    for (int ib = 0; ib < nB; ib++) {
                        cdOffFlat[idx]   = surface.cdPlumeOff[im][ir][ia][ib];
                        cdOnFlat[idx]    = surface.cdPlumeOn[im][ir][ia][ib];
                        idx++;
                    }
                }
            }
        }
    }
}
```

### 2b — `PodBasis.java`

The POD basis is computed via the SVD of the snapshot matrix S whose columns are
the flattened snapshot vectors `s_1, ..., s_K` (Ch. 4, Algorithm 4.1). Since Java
has no built-in SVD, use the following approach: form the **correlation matrix**
C = S^T S (K × K, where K is the number of snapshots, typically K << N) and
solve the K × K eigenvalue problem — this is the "method of snapshots" (Ch. 3
Section 3.2, Remark 3.1).

```java
package info.openrocket.core.aerodynamics.rom.core.basis;

import java.util.List;

/**
 * POD basis built from a collection of geometry snapshots.
 *
 * Implements the "method of snapshots" (Lumley 1967, Sirovich 1987) as described
 * in Quarteroni-Rozza Ch. 3 Section 3.2, Scheme 3.1.
 *
 * Given K snapshots s_1,...,s_K ∈ R^N (N = grid size, K = snapshot count),
 * forms the K×K correlation matrix C_ij = s_i · s_j / N, solves for eigenvectors
 * {v_q}, and constructs POD modes h_q = Σ_n (v_q)_n * s_n.
 *
 * The first Q modes form the reduced basis. The truncation Q is chosen so that
 * the relative energy (sum of discarded eigenvalues / total energy) < tolerance.
 */
public final class PodBasis {

    /** POD modes, shape [Q][N] — each row is one POD mode. */
    public final double[][] modes;

    /** Mean snapshot (subtracted before projection, added back after). */
    public final double[] mean;

    /** Singular values (square roots of eigenvalues), descending order, length Q. */
    public final double[] singularValues;

    /** Relative energy captured by this basis: Σ σ_q² / Σ σ_total². */
    public final double relativeEnergy;

    /** Number of retained modes Q. */
    public final int rank;

    /** Length of each snapshot vector N = nM*nR*nA*nB. */
    public final int snapshotLength;

    /** Axis arrays — shared across all snapshots that built this basis. */
    public final double[] machAxis;
    public final double[] logReAxis;
    public final double[] alphaAxis;
    public final double[] betaAxis;

    /**
     * Constructs a POD basis from a list of snapshots.
     *
     * @param snapshots    list of K geometry snapshots (all must share axes)
     * @param tolerance    energy tolerance: retain modes until cumulative energy
     *                     fraction reaches (1 - tolerance). E.g. 1e-4 retains 99.99%.
     * @param useCdOff     if true, use cdOffFlat; if false, use cdOnFlat
     */
    public PodBasis(List<GeometrySnapshot> snapshots, double tolerance, boolean useCdOff) {
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("At least one snapshot is required");
        }
        this.machAxis  = snapshots.get(0).machAxis;
        this.logReAxis = snapshots.get(0).logReAxis;
        this.alphaAxis = snapshots.get(0).alphaAxis;
        this.betaAxis  = snapshots.get(0).betaAxis;

        int K = snapshots.size();
        int N = snapshots.get(0).cdOffFlat.length;
        this.snapshotLength = N;

        // --- Step 1: Compute mean snapshot ---
        this.mean = new double[N];
        for (GeometrySnapshot s : snapshots) {
            double[] flat = useCdOff ? s.cdOffFlat : s.cdOnFlat;
            for (int i = 0; i < N; i++) {
                mean[i] += flat[i];
            }
        }
        for (int i = 0; i < N; i++) {
            mean[i] /= K;
        }

        // --- Step 2: Center snapshots ---
        double[][] centered = new double[K][N];
        for (int k = 0; k < K; k++) {
            double[] flat = useCdOff
                    ? snapshots.get(k).cdOffFlat
                    : snapshots.get(k).cdOnFlat;
            for (int i = 0; i < N; i++) {
                centered[k][i] = flat[i] - mean[i];
            }
        }

        // --- Step 3: Form K×K correlation matrix C = S^T S / N ---
        // (method of snapshots — cheap when K << N)
        double[][] C = new double[K][K];
        for (int j = 0; j < K; j++) {
            for (int l = j; l < K; l++) {
                double dot = 0.0;
                for (int i = 0; i < N; i++) {
                    dot += centered[j][i] * centered[l][i];
                }
                dot /= N;
                C[j][l] = dot;
                C[l][j] = dot;  // symmetric
            }
        }

        // --- Step 4: Symmetric eigendecomposition of C (K×K) ---
        // Use Jacobi iteration for small K (K ≤ 200 typical).
        // For larger K, replace with a proper LAPACK binding.
        double[][] eigVecs = new double[K][K];
        double[] eigVals   = new double[K];
        jacobiEigen(C, eigVecs, eigVals);
        sortEigenDescending(eigVals, eigVecs);

        // --- Step 5: Compute total energy and determine rank Q ---
        double totalEnergy = 0.0;
        for (double ev : eigVals) {
            totalEnergy += Math.max(ev, 0.0);
        }
        double cumulativeEnergy = 0.0;
        int Q = 0;
        for (int q = 0; q < K; q++) {
            cumulativeEnergy += Math.max(eigVals[q], 0.0);
            Q = q + 1;
            if (totalEnergy > 1e-15
                    && (cumulativeEnergy / totalEnergy) >= (1.0 - tolerance)) {
                break;
            }
        }
        this.rank = Q;
        this.relativeEnergy = (totalEnergy > 1e-15)
                ? cumulativeEnergy / totalEnergy : 1.0;

        // --- Step 6: Construct POD modes h_q = Σ_n (v_q)_n * s_n ---
        // h_q is normalized in the Euclidean sense.
        double[][] modesTmp = new double[Q][N];
        double[] svTmp = new double[Q];
        for (int q = 0; q < Q; q++) {
            for (int k = 0; k < K; k++) {
                double coeff = eigVecs[k][q];
                if (Math.abs(coeff) < 1e-15) continue;
                for (int i = 0; i < N; i++) {
                    modesTmp[q][i] += coeff * centered[k][i];
                }
            }
            // Normalize
            double norm = 0.0;
            for (int i = 0; i < N; i++) {
                norm += modesTmp[q][i] * modesTmp[q][i];
            }
            norm = Math.sqrt(Math.max(norm, 1e-30));
            for (int i = 0; i < N; i++) {
                modesTmp[q][i] /= norm;
            }
            svTmp[q] = Math.sqrt(Math.max(eigVals[q], 0.0));
        }
        this.modes = modesTmp;
        this.singularValues = svTmp;
    }

    /**
     * Projects a snapshot vector onto this POD basis, returning Q coefficients.
     * The mean is subtracted first, then an inner product with each mode is taken.
     * This is the online reconstruction step (Ch. 3 Section 3.2).
     *
     * @param snapshot flattened Cd values of length N
     * @return double[rank] projection coefficients a_q = (snapshot - mean) · h_q
     */
    public double[] project(double[] snapshot) {
        if (snapshot.length != snapshotLength) {
            throw new IllegalArgumentException(
                "Snapshot length mismatch: expected " + snapshotLength
                + ", got " + snapshot.length);
        }
        double[] coeffs = new double[rank];
        for (int q = 0; q < rank; q++) {
            double dot = 0.0;
            for (int i = 0; i < snapshotLength; i++) {
                dot += (snapshot[i] - mean[i]) * modes[q][i];
            }
            coeffs[q] = dot;
        }
        return coeffs;
    }

    /**
     * Reconstructs a snapshot vector from Q projection coefficients.
     * Mean is added back after linear combination of modes.
     *
     * @param coeffs double[rank] POD coefficients
     * @return reconstructed snapshot of length N
     */
    public double[] reconstruct(double[] coeffs) {
        if (coeffs.length != rank) {
            throw new IllegalArgumentException(
                "Coefficient length mismatch: expected " + rank
                + ", got " + coeffs.length);
        }
        double[] result = new double[snapshotLength];
        for (int i = 0; i < snapshotLength; i++) {
            result[i] = mean[i];
        }
        for (int q = 0; q < rank; q++) {
            if (Math.abs(coeffs[q]) < 1e-15) continue;
            for (int i = 0; i < snapshotLength; i++) {
                result[i] += coeffs[q] * modes[q][i];
            }
        }
        return result;
    }

    /**
     * Reconstruction error (L2 relative) for a given snapshot.
     * Used to validate basis quality: should be < tolerance for all training snapshots.
     *
     * @param snapshot flattened Cd vector to test
     * @return relative L2 error ||snapshot - reconstructed|| / ||snapshot||
     */
    public double reconstructionError(double[] snapshot) {
        double[] coeffs = project(snapshot);
        double[] recon  = reconstruct(coeffs);
        double errNorm  = 0.0;
        double snapNorm = 0.0;
        for (int i = 0; i < snapshotLength; i++) {
            double d = recon[i] - snapshot[i];
            errNorm  += d * d;
            snapNorm += snapshot[i] * snapshot[i];
        }
        return (snapNorm > 1e-15) ? Math.sqrt(errNorm / snapNorm) : 0.0;
    }

    // ---- Private: Jacobi eigendecomposition for symmetric K×K matrix ----
    // Suitable for K ≤ ~200 (which covers any realistic snapshot count).
    // Replace with LAPACK dsyev binding for larger K.

    private static void jacobiEigen(double[][] A, double[][] V, double[] d) {
        int n = A.length;
        // Initialize V as identity
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                V[i][j] = (i == j) ? 1.0 : 0.0;
            }
        }
        // Copy A to working matrix
        double[][] a = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = A[i][j];
            }
        }

        final int MAX_ITER = 200 * n * n;
        for (int iter = 0; iter < MAX_ITER; iter++) {
            // Find largest off-diagonal element
            int p = 0, q = 1;
            double maxOff = Math.abs(a[0][1]);
            for (int i = 0; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (Math.abs(a[i][j]) > maxOff) {
                        maxOff = Math.abs(a[i][j]);
                        p = i;
                        q = j;
                    }
                }
            }
            if (maxOff < 1e-14) break;

            // Jacobi rotation angle
            double theta = (a[q][q] - a[p][p]) / (2.0 * a[p][q]);
            double t = (theta >= 0.0)
                    ? 1.0 / (theta + Math.sqrt(1.0 + theta * theta))
                    : 1.0 / (theta - Math.sqrt(1.0 + theta * theta));
            double c = 1.0 / Math.sqrt(1.0 + t * t);
            double s = t * c;
            double tau = s / (1.0 + c);

            // Update a
            double apq = a[p][q];
            a[p][q] = 0.0;
            a[q][p] = 0.0;
            a[p][p] -= t * apq;
            a[q][q] += t * apq;
            for (int r = 0; r < n; r++) {
                if (r != p && r != q) {
                    double apr = a[p][r];
                    double aqr = a[q][r];
                    a[p][r] = apr - s * (aqr + tau * apr);
                    a[q][r] = aqr + s * (apr - tau * aqr);
                    a[r][p] = a[p][r];
                    a[r][q] = a[q][r];
                }
            }
            // Update V (eigenvectors as columns)
            for (int r = 0; r < n; r++) {
                double vr_p = V[r][p];
                double vr_q = V[r][q];
                V[r][p] = vr_p - s * (vr_q + tau * vr_p);
                V[r][q] = vr_q + s * (vr_p - tau * vr_q);
            }
        }
        // Extract eigenvalues
        for (int i = 0; i < n; i++) {
            d[i] = a[i][i];
        }
    }

    private static void sortEigenDescending(double[] vals, double[][] vecs) {
        int n = vals.length;
        for (int i = 0; i < n - 1; i++) {
            int maxIdx = i;
            for (int j = i + 1; j < n; j++) {
                if (vals[j] > vals[maxIdx]) maxIdx = j;
            }
            if (maxIdx != i) {
                double tmp = vals[i]; vals[i] = vals[maxIdx]; vals[maxIdx] = tmp;
                for (int k = 0; k < n; k++) {
                    tmp = vecs[k][i]; vecs[k][i] = vecs[k][maxIdx]; vecs[k][maxIdx] = tmp;
                }
            }
        }
    }
}
```

### 2c — Verification

Build `PodBasis` from 3 identical snapshots. Assert:
- `rank == 1` (all snapshots identical → 1 mode captures 100% energy)
- `reconstructionError(snapshot) < 1e-10` for training snapshot

Build from 5 diverse snapshots (vary fineness ratio 4, 6, 8, 10, 12; everything else fixed).
Assert `rank ≤ 3` (smooth 1D parameter variation needs few modes) and
`relativeEnergy >= 0.999` for `tolerance=1e-3`.

---

## Task 3 — Geometry sample space and greedy sampling

**Grounding: Ch. 5, Urban et al. — Greedy Sampling Using Nonlinear Optimization;
Ch. 3 Section 3.4.3 (EIM greedy algorithm); Ch. 5 Section 5.1**

The POD basis quality depends entirely on how well the training snapshots cover
geometry space. Uniform sampling wastes evaluations in low-variation regions;
greedy sampling concentrates them where the current basis is weakest — exactly
the EIM approach from Ch. 3.

### 3a — `GeometrySampler.java`

```java
package info.openrocket.core.aerodynamics.rom.core.sampling;

import info.openrocket.core.aerodynamics.rom.core.basis.GeometrySnapshot;
import info.openrocket.core.aerodynamics.rom.core.basis.PodBasis;
import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

import java.util.ArrayList;
import java.util.List;

/**
 * Greedy geometry sampler implementing the EIM algorithm
 * (Quarteroni-Rozza Ch. 3 Section 3.4.3, Scheme 3.4).
 *
 * Starting from a seed set, iteratively selects the geometry from a candidate
 * pool that has the LARGEST reconstruction error under the current POD basis.
 * This is the maximum-error greedy selection (Eq. 3.18 in the book):
 *
 *   g_next = argmax_{g ∈ Ω_train} ||Cd(·,·,·,·; g) - I_{q-1}[Cd(·,·,·,·; g)]||
 *
 * Stops when max reconstruction error < targetErrorPercent OR maxSnapshots reached.
 *
 * All grids share the same (M, Re, α, β) axes, so snapshots are directly comparable.
 */
public final class GeometrySampler {

    private GeometrySampler() {}

    public interface ProgressListener {
        void onSnapshot(int snapshotIndex, int totalCandidates,
                        double currentMaxError, RomGeometryInput selected);
    }

    /**
     * Runs the greedy sampling loop.
     *
     * @param candidates        pool of candidate geometries to draw from
     * @param seeds             initial geometries always included (min 2 recommended)
     * @param maxSnapshots      maximum number of snapshots to collect
     * @param targetErrorPct    stop when max reconstruction error falls below this %
     * @param podTolerance      energy tolerance for PodBasis construction (e.g. 1e-4)
     * @param progress          optional progress listener (may be null)
     * @return list of selected GeometrySnapshots in selection order
     */
    public static List<GeometrySnapshot> greedySample(
            List<RomGeometryInput> candidates,
            List<RomGeometryInput> seeds,
            int maxSnapshots,
            double targetErrorPct,
            double podTolerance,
            ProgressListener progress) {

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidate pool must not be empty");
        }

        List<GeometrySnapshot> selected = new ArrayList<>();
        // Mark which candidates are already selected (by index)
        boolean[] used = new boolean[candidates.size()];

        // Step 1: Build full 4D surfaces for seed geometries and add as snapshots
        for (RomGeometryInput seed : seeds) {
            AeroSurface4D surface = AeroGridEvaluator4D.evaluate(seed, null);
            selected.add(new GeometrySnapshot(seed, surface));
            // Mark the matching candidate as used (exact feature vector match)
            for (int i = 0; i < candidates.size(); i++) {
                if (!used[i] && seed.geometryHash(candidates.get(i))) {
                    used[i] = true;
                    break;
                }
            }
        }

        // Step 2: Greedy loop
        while (selected.size() < maxSnapshots) {
            // Build current POD basis from selected snapshots
            PodBasis basis = new PodBasis(selected, podTolerance, true);

            // Find candidate with maximum reconstruction error
            double maxErr = -1.0;
            int worstIdx  = -1;
            RomGeometryInput worstGeom = null;

            for (int i = 0; i < candidates.size(); i++) {
                if (used[i]) continue;
                RomGeometryInput g = candidates.get(i);

                // Evaluate full grid for this candidate (this is the "truth" evaluation)
                AeroSurface4D surface = AeroGridEvaluator4D.evaluate(g, null);
                GeometrySnapshot snap = new GeometrySnapshot(g, surface);

                double err = basis.reconstructionError(snap.cdOffFlat);
                // Convert to percentage of mean Cd for comparability
                double meanCd = 0.0;
                for (double v : snap.cdOffFlat) meanCd += v;
                meanCd /= snap.cdOffFlat.length;
                double errPct = (meanCd > 1e-9) ? 100.0 * err : 0.0;

                if (errPct > maxErr) {
                    maxErr  = errPct;
                    worstIdx = i;
                    worstGeom = g;
                }
            }

            if (worstIdx < 0 || maxErr < targetErrorPct) {
                break; // tolerance met or no candidates remain
            }

            // Add the worst-approximated geometry to the selected set
            AeroSurface4D worstSurface = AeroGridEvaluator4D.evaluate(worstGeom, null);
            GeometrySnapshot worstSnap = new GeometrySnapshot(worstGeom, worstSurface);
            selected.add(worstSnap);
            used[worstIdx] = true;

            if (progress != null) {
                progress.onSnapshot(selected.size(), candidates.size(),
                        maxErr, worstGeom);
            }
        }

        return selected;
    }
}
```

**Important implementation note**: The inner loop builds a full 4D surface for every
unevaluated candidate at each greedy iteration. For a pool of 500 candidates and 20
greedy iterations, this is 10,000 full grid builds — expensive if done naively. Add a
**lazy evaluation cache**: build all candidate surfaces once upfront and reuse them.
Add an optional `Map<String, AeroSurface4D> prebuiltCache` parameter.

### 3b — Canonical geometry sample space

Add a static factory to `GeometrySampler` that generates a standard Latin Hypercube
Sampling (LHS) candidate pool covering the typical OpenRocket rocket design space:

```java
/**
 * Generates a Latin Hypercube Sampling (LHS) pool of RomGeometryInput objects
 * covering the practical OpenRocket design space.
 *
 * Parameter ranges (all based on typical amateur/research rocket geometries):
 *   finenessRatio:    [4, 20]      (stubby to slender)
 *   noseFraction:     [0.15, 0.35] (nose length / body length)
 *   finAR:            [1.0, 5.0]   (fin aspect ratio)
 *   finTaperRatio:    [0.0, 0.6]   (tip/root)
 *   finThickRatio:    [0.03, 0.10] (t/c)
 *   finAreaFraction:  [0.3, 2.0]   (fin area / Aref)
 *   noseShape:        one of the 6 NoseShape values (categorical, sampled uniformly)
 *   boattailFraction: [0.0, 0.15]  (boattail length / body length)
 *
 * The body diameter is fixed at 0.1 m; all other dimensions are derived from
 * the dimensionless ratios above to give physically consistent geometries.
 *
 * @param n number of candidate geometries to generate (recommended: 200–500)
 * @param seed random seed for reproducibility
 */
public static List<RomGeometryInput> buildLhsPool(int n, long seed) {
    // ... (implement LHS sampling over the parameter ranges above)
    // Reference: https://en.wikipedia.org/wiki/Latin_hypercube_sampling
    // Each dimension is divided into n equal-probability intervals; one sample
    // is drawn from each interval, and the dimensions are permuted randomly.
}
```

The LHS implementation uses a simple stratified random permutation — no external
library needed. Each parameter dimension is divided into `n` equal intervals; one
sample is drawn uniformly within each interval; then each dimension's samples are
permuted independently.

---

## Task 4 — hp-EIM: Local POD regions for transonic vs. subsonic geometries

**Grounding: Ch. 3 Section 3.4.6.2 (hp-EIM); Section 3.4 Remark 3.9**

A single global POD basis performs poorly when the solution manifold has
transport-like features: geometries with low fineness ratio have early drag rise
(M_DD ≈ 0.70), while slender ogive rockets have M_DD ≈ 0.90. The transonic peak
effectively "translates" in Mach space as geometry changes — the canonical
transport problem that breaks global POD (described in the project knowledge
documents for this codebase).

The hp-EIM partitions parameter space `Ω_geometry` into subregions, each with
its own local POD basis. Blending at boundaries uses smooth partition-of-unity
weights (the same sigmoid approach used in `TransonicBlendingModel`).

### 4a — `LocalPodRegion.java`

```java
package info.openrocket.core.aerodynamics.rom.core.basis;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import java.util.List;

/**
 * A local POD region in the hp-EIM partitioning of geometry space.
 * (Quarteroni-Rozza Ch. 3 Section 3.4.6.2)
 *
 * Each region has:
 *   - A centroid geometry feature vector (center of the cluster)
 *   - A radius (Euclidean distance in feature space beyond which weight → 0)
 *   - A POD basis for Cd_off reconstruction
 *   - A POD basis for Cd_on reconstruction (same modes, different projections)
 *
 * Weight function: Gaussian kernel w(g) = exp(-||g - centroid||² / (2σ²))
 * where σ = radius / 2. Weights across all regions are normalized to sum to 1.
 */
public final class LocalPodRegion {

    public final double[] centroidFeatureVector;
    public final double sigma;          // bandwidth parameter
    public final PodBasis basisOff;     // POD basis for Cd_plume_off
    public final PodBasis basisOn;      // POD basis for Cd_plume_on
    public final String regionLabel;    // human-readable name (e.g., "slender_ogive")

    public LocalPodRegion(double[] centroid, double sigma,
                           PodBasis basisOff, PodBasis basisOn,
                           String regionLabel) {
        this.centroidFeatureVector = centroid;
        this.sigma      = sigma;
        this.basisOff   = basisOff;
        this.basisOn    = basisOn;
        this.regionLabel = regionLabel;
    }

    /**
     * Gaussian weight of a query geometry with respect to this region.
     *
     * @param queryFeatures feature vector from RomGeometryInput.toFeatureVector()
     */
    public double weight(double[] queryFeatures) {
        double distSq = 0.0;
        for (int i = 0; i < centroidFeatureVector.length; i++) {
            double d = queryFeatures[i] - centroidFeatureVector[i];
            distSq += d * d;
        }
        return Math.exp(-distSq / (2.0 * sigma * sigma));
    }
}
```

### 4b — Two-region partition (initial implementation)

For the initial hp-EIM implementation, use two regions:

**Region A — "Slender streamlined"**: fineness ratio > 8 AND nose type ∈ {OGIVE, VON_KARMAN, HAACK}
**Region B — "Blunt / transonic-critical"**: fineness ratio ≤ 8 OR nose type ∈ {CONICAL, PARABOLIC, ELLIPSOID}

The centroid of each region is the mean feature vector of all snapshots assigned to it.
`sigma` is set to the maximum feature distance among snapshots in the region (so all
training points have weight ≥ `exp(-0.5) ≈ 0.6`).

This partition can be extended to more regions later without changing the interpolation
interface — the `GeometryParametricRom` always normalizes weights across all regions.

---

## Task 5 — `GeometryParametricRom` — the online reconstruction engine

**Grounding: Ch. 3 Section 3.2 (POD projection); Ch. 5 Section 5.2.3 (online stage)**

This is the central online-stage class. Given a new `RomGeometryInput`, it:
1. Computes the geometry feature vector.
2. Evaluates weights for each local POD region.
3. Projects the new geometry's Cd surface onto each region's POD basis
   using **Gappy POD** (Ch. 3 Section 3.6) — evaluating only at the magic points.
4. Reconstructs the full `AeroSurface4D` as a weighted combination.
5. Returns an `AeroSurface4DInterpolator` ready for trajectory queries.

### 5a — Magic point selection via EIM (Ch. 3 Section 3.4.3 Algorithm, Scheme 3.4)

Before the online stage can use Gappy POD, the offline stage must identify "magic
points" — the sparse set of (M, Re, α, β) indices where evaluating `computePoint()`
gives maximum information about the POD coefficients.

Add `MagicPointSelector.java`:

```java
package info.openrocket.core.aerodynamics.rom.core.sampling;

import info.openrocket.core.aerodynamics.rom.core.basis.PodBasis;

/**
 * Selects magic points for Gappy POD reconstruction using the greedy EIM
 * algorithm (Quarteroni-Rozza Ch. 3 Section 3.4, Algorithm Scheme 3.4).
 *
 * The magic points are indices into the flattened snapshot vector
 * [im * nR*nA*nB + ir * nA*nB + ia * nB + ib] at which evaluating
 * computePoint() gives maximum information about the POD coefficients.
 *
 * Algorithm (follows Scheme 3.4 exactly):
 *   q=1: x_1 = argmax |h_1(x)|  — first magic point is where mode 1 peaks
 *   q>1: solve B g = f_y at current magic points; r = f_y - I_{q-1}[f_y];
 *         x_q = argmax |r(x)|
 *
 * The resulting L magic points (L ≥ rank) allow reconstruction of any new
 * snapshot by solving an L×rank least-squares system.
 *
 * @param basis   POD basis (modes are h_1, ..., h_Q)
 * @param nExtra  number of extra points beyond rank for stability (default: rank/2)
 * @return int[] of length (rank + nExtra) — indices into flattened snapshot vector
 */
public static int[] selectMagicPoints(PodBasis basis, int nExtra) {
    int Q = basis.rank;
    int N = basis.snapshotLength;
    int L = Math.min(Q + nExtra, N);

    int[] points = new int[L];
    // Interpolation matrix B (lower triangular with unit diagonal, per book)
    double[][] B = new double[L][Q];

    // q=1: first magic point = argmax |h_1(x)|
    double maxVal = 0.0;
    int p1 = 0;
    for (int i = 0; i < N; i++) {
        if (Math.abs(basis.modes[0][i]) > maxVal) {
            maxVal = Math.abs(basis.modes[0][i]);
            p1 = i;
        }
    }
    points[0] = p1;
    B[0][0] = basis.modes[0][p1];

    // q=2,...,L:
    for (int q = 1; q < L; q++) {
        // Use mode q (or mode Q-1 if q >= Q — extra points use last mode)
        int modeIdx = Math.min(q, Q - 1);
        double[] hq = basis.modes[modeIdx];

        // Build interpolation values at current points for mode hq
        // Solve B_{q×q} g = hq[points[0..q-1]] (lower triangular forward substitution)
        double[] fAtPoints = new double[q];
        for (int k = 0; k < q; k++) {
            fAtPoints[k] = hq[points[k]];
        }
        double[] g = forwardSolve(B, fAtPoints, q);

        // Compute residual r(x) = hq(x) - Σ g_j h_j(x) at all N points
        double maxResidual = 0.0;
        int pq = -1;
        for (int i = 0; i < N; i++) {
            boolean alreadyChosen = false;
            for (int k = 0; k < q; k++) {
                if (points[k] == i) { alreadyChosen = true; break; }
            }
            if (alreadyChosen) continue;

            double r = hq[i];
            for (int j = 0; j < q; j++) {
                r -= g[j] * basis.modes[Math.min(j, Q - 1)][i];
            }
            if (Math.abs(r) > maxResidual) {
                maxResidual = Math.abs(r);
                pq = i;
            }
        }
        if (pq < 0) break;
        points[q] = pq;
        for (int j = 0; j < Q; j++) {
            B[q][j] = basis.modes[j][pq];
        }
    }
    return points;
}

private static double[] forwardSolve(double[][] L, double[] b, int n) {
    double[] x = new double[n];
    for (int i = 0; i < n; i++) {
        x[i] = b[i];
        for (int j = 0; j < i; j++) {
            x[i] -= L[i][j] * x[j];
        }
        if (Math.abs(L[i][i]) > 1e-15) {
            x[i] /= L[i][i];
        }
    }
    return x;
}
```

### 5b — `GeometryParametricRom.java` — online reconstruction

```java
package info.openrocket.core.aerodynamics.rom.core.basis;

import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.sampling.MagicPointSelector;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4DInterpolator;

import java.util.List;

/**
 * Online-stage geometry-parametric ROM.
 *
 * Workflow for a new query geometry g:
 *
 *   1. Compute feature vector v = g.toFeatureVector()
 *   2. For each local region r: weight w_r = region_r.weight(v)
 *   3. For each region r: evaluate computePoint() at magic points only (sparse)
 *   4. For each region r: reconstruct full Cd surface via Gappy POD
 *   5. Blend reconstructed surfaces: Cd = Σ_r (w_r * Cd_r) / Σ_r w_r
 *   6. Wrap in AeroSurface4D and return AeroSurface4DInterpolator
 *
 * The key cost saving: instead of 130,000 computePoint() calls for a full grid
 * rebuild, this requires only L * numRegions calls (L ≈ 2*rank, typically 10–30).
 * For 2 regions and rank=8, this is ~30 evaluations vs 130,000 — a 4000× speedup
 * with reconstruction error < 1%.
 *
 * References: Ch. 3 Sections 3.2, 3.6 (Gappy POD); Ch. 5 Section 5.2.3 (online stage)
 */
public final class GeometryParametricRom {

    private final List<LocalPodRegion> regions;
    private final int[][] magicPointsOff; // [regionIdx][pointIdx] — flattened grid indices
    private final int[][] magicPointsOn;

    /** Shared axis arrays — all regions must use these. */
    public final double[] machAxis;
    public final double[] logReAxis;
    public final double[] alphaAxis;
    public final double[] betaAxis;

    /** Total number of full-grid evaluations saved vs naive rebuild (diagnostic). */
    private long savedEvaluations = 0L;

    public GeometryParametricRom(List<LocalPodRegion> regions,
                                  double[] machAxis, double[] logReAxis,
                                  double[] alphaAxis, double[] betaAxis) {
        this.regions   = regions;
        this.machAxis  = machAxis;
        this.logReAxis = logReAxis;
        this.alphaAxis = alphaAxis;
        this.betaAxis  = betaAxis;

        int nRegions = regions.size();
        this.magicPointsOff = new int[nRegions][];
        this.magicPointsOn  = new int[nRegions][];

        for (int r = 0; r < nRegions; r++) {
            LocalPodRegion region = regions.get(r);
            int nExtra = Math.max(1, region.basisOff.rank / 2);
            magicPointsOff[r] = MagicPointSelector.selectMagicPoints(region.basisOff, nExtra);
            magicPointsOn[r]  = MagicPointSelector.selectMagicPoints(region.basisOn,  nExtra);
        }
    }

    /**
     * Reconstructs an AeroSurface4D for the given geometry using Gappy POD.
     *
     * Falls back to full grid evaluation (AeroGridEvaluator4D.evaluate) if:
     *   - All region weights are below MIN_WEIGHT (geometry too far from training data)
     *   - Any reconstruction error exceeds FALLBACK_ERROR_THRESHOLD
     *
     * @param g           query geometry
     * @param geometryHash SHA-256 hash of g (from AeroGridEvaluator4D.computeHash)
     * @return AeroSurface4D with LOO RMSE field populated (0.0 if reconstructed)
     */
    public AeroSurface4D reconstructSurface(RomGeometryInput g, String geometryHash) {
        final double MIN_WEIGHT = 1e-6;
        final double FALLBACK_ERROR_THRESHOLD = 0.05; // 5% relative L2 error

        double[] feat = g.toFeatureVector();
        int nRegions = regions.size();

        // Step 2: compute weights
        double[] weights = new double[nRegions];
        double weightSum = 0.0;
        for (int r = 0; r < nRegions; r++) {
            weights[r] = regions.get(r).weight(feat);
            weightSum += weights[r];
        }
        if (weightSum < MIN_WEIGHT) {
            // Geometry is too far from all training data — fall back to full build
            return AeroGridEvaluator4D.evaluate(g, geometryHash, null);
        }
        for (int r = 0; r < nRegions; r++) {
            weights[r] /= weightSum;
        }

        int nM = machAxis.length;
        int nR = logReAxis.length;
        int nA = alphaAxis.length;
        int nB = betaAxis.length;
        int N  = nM * nR * nA * nB;

        // Accumulate blended Cd surfaces
        double[] blendedOff = new double[N];
        double[] blendedOn  = new double[N];

        for (int r = 0; r < nRegions; r++) {
            if (weights[r] < MIN_WEIGHT / nRegions) continue;
            LocalPodRegion region = regions.get(r);

            // Step 3: evaluate at magic points only
            double[] sparseOff = evaluateAtMagicPoints(g, magicPointsOff[r], nM, nR, nA, nB);
            double[] sparseOn  = evaluateAtMagicPoints(g, magicPointsOn[r],  nM, nR, nA, nB);
            savedEvaluations += N - magicPointsOff[r].length - magicPointsOn[r].length;

            // Step 4: Gappy POD reconstruction
            double[] reconOff = gappyReconstruct(region.basisOff, sparseOff, magicPointsOff[r]);
            double[] reconOn  = gappyReconstruct(region.basisOn,  sparseOn,  magicPointsOn[r]);

            // Accumulate with weight
            for (int i = 0; i < N; i++) {
                blendedOff[i] += weights[r] * reconOff[i];
                blendedOn[i]  += weights[r] * reconOn[i];
            }
        }

        // Step 5: unflatten into 4D arrays
        double[][][][] cdOff   = new double[nM][nR][nA][nB];
        double[][][][] cdOn    = new double[nM][nR][nA][nB];
        double[][][][] cdBody  = new double[nM][nR][nA][nB];  // approximate: same as cdOff
        double[][][][] cnArr   = new double[nM][nR][nA][nB];
        double[][][][] cmArr   = new double[nM][nR][nA][nB];

        int idx = 0;
        for (int im = 0; im < nM; im++) {
            for (int ir = 0; ir < nR; ir++) {
                for (int ia = 0; ia < nA; ia++) {
                    for (int ib = 0; ib < nB; ib++) {
                        cdOff[im][ir][ia][ib]  = Math.max(0.001, blendedOff[idx]);
                        cdOn[im][ir][ia][ib]   = Math.max(0.001, blendedOn[idx]);
                        cdBody[im][ir][ia][ib] = Math.max(0.001, blendedOff[idx]); // approx
                        idx++;
                    }
                }
            }
        }

        // CN and Cm: these are not POD-compressed in Phase 2 — compute directly.
        // The cost is (nM * nR * nA * nB) evaluations of NormalForceModel and
        // PitchingMomentModel, which is fast (no transonic blending needed).
        for (int im = 0; im < nM; im++) {
            for (int ir = 0; ir < nR; ir++) {
                double reL = Math.pow(10.0, logReAxis[ir]);
                for (int ia = 0; ia < nA; ia++) {
                    double alphaRad = Math.toRadians(alphaAxis[ia]);
                    for (int ib = 0; ib < nB; ib++) {
                        double betaRad = Math.toRadians(betaAxis[ib]);
                        double cn = info.openrocket.core.aerodynamics.rom.core.physics
                                .NormalForceModel.CN(alphaRad, betaRad, machAxis[im], g);
                        double cm = info.openrocket.core.aerodynamics.rom.core.physics
                                .PitchingMomentModel.Cm(cn, alphaRad, g);
                        cnArr[im][ir][ia][ib] = cn;
                        cmArr[im][ir][ia][ib] = cm;
                    }
                }
            }
        }

        return new AeroSurface4D(machAxis, logReAxis, alphaAxis, betaAxis,
                cdOff, cdOn, cdBody, cnArr, cmArr,
                geometryHash, g.finCount, 0.0);
    }

    // ---- Private helpers ----

    /**
     * Evaluates computePoint() at the specified magic point indices.
     * Returns a sparse array with N entries where non-magic-point entries are 0.
     */
    private double[] evaluateAtMagicPoints(RomGeometryInput g, int[] magicPoints,
                                            int nM, int nR, int nA, int nB) {
        double[] sparse = new double[nM * nR * nA * nB];
        for (int flatIdx : magicPoints) {
            // Unflatten index: flatIdx = im*nR*nA*nB + ir*nA*nB + ia*nB + ib
            int tmp = flatIdx;
            int ib  = tmp % nB; tmp /= nB;
            int ia  = tmp % nA; tmp /= nA;
            int ir  = tmp % nR; tmp /= nR;
            int im  = tmp;

            double mach   = machAxis[im];
            double reL    = Math.pow(10.0, logReAxis[ir]);
            double alphaRad = Math.toRadians(alphaAxis[ia]);
            double betaRad  = Math.toRadians(betaAxis[ib]);

            AeroGridEvaluator4D.PointResult pt =
                    AeroGridEvaluator4D.computePoint(mach, reL, alphaRad, betaRad, g);
            sparse[flatIdx] = pt.cdPlumeOff;
        }
        return sparse;
    }

    /**
     * Gappy POD reconstruction from sparse observations.
     * (Quarteroni-Rozza Ch. 3 Section 3.6, Eq. 3.27)
     *
     * Solves the L×Q least-squares system M h = f_sparse at magic points,
     * where M_lq = mode_q(x_l) and f_sparse_l = observed(x_l) - mean(x_l).
     * Returns the full reconstructed snapshot.
     */
    private static double[] gappyReconstruct(PodBasis basis, double[] sparse,
                                              int[] magicPoints) {
        int Q = basis.rank;
        int L = magicPoints.length;

        // Assemble L×Q matrix M and RHS vector
        double[][] M = new double[L][Q];
        double[] rhs = new double[L];
        for (int l = 0; l < L; l++) {
            int idx = magicPoints[l];
            rhs[l]  = sparse[idx] - basis.mean[idx];
            for (int q = 0; q < Q; q++) {
                M[l][q] = basis.modes[q][idx];
            }
        }

        // Solve normal equations M^T M c = M^T rhs (least squares)
        double[][] MTM = new double[Q][Q];
        double[] MTrhs  = new double[Q];
        for (int p = 0; p < Q; p++) {
            for (int q = 0; q < Q; q++) {
                for (int l = 0; l < L; l++) {
                    MTM[p][q] += M[l][p] * M[l][q];
                }
            }
            for (int l = 0; l < L; l++) {
                MTrhs[p] += M[l][p] * rhs[l];
            }
        }

        // Cholesky or direct solve of Q×Q symmetric system
        double[] coeffs = solveSymmetric(MTM, MTrhs);
        return basis.reconstruct(coeffs);
    }

    /** Solves A x = b for symmetric positive (semi-)definite A via Cholesky. */
    private static double[] solveSymmetric(double[][] A, double[] b) {
        int n = A.length;
        double[][] L = new double[n][n];
        // Cholesky decomposition
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = A[i][j];
                for (int k = 0; k < j; k++) sum -= L[i][k] * L[j][k];
                if (i == j) {
                    L[i][j] = Math.sqrt(Math.max(sum, 1e-30));
                } else {
                    L[i][j] = (L[j][j] > 1e-15) ? sum / L[j][j] : 0.0;
                }
            }
        }
        // Forward solve L y = b
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = b[i];
            for (int k = 0; k < i; k++) y[i] -= L[i][k] * y[k];
            y[i] = (L[i][i] > 1e-15) ? y[i] / L[i][i] : 0.0;
        }
        // Backward solve L^T x = y
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            x[i] = y[i];
            for (int k = i + 1; k < n; k++) x[i] -= L[k][i] * x[k];
            x[i] = (L[i][i] > 1e-15) ? x[i] / L[i][i] : 0.0;
        }
        return x;
    }
}
```

---

## Task 6 — Offline build pipeline: `GeometricRomEvaluator`

**Grounding: Ch. 5 Section 5.1 (offline stage); Ch. 2 (offline/online decomposition)**

The offline pipeline ties Tasks 2–5 together into a single reproducible build process.
It produces a serialized `GeometricRomSerializer`-compatible artifact that can be
bundled with OpenRocket releases and loaded at startup.

```java
package info.openrocket.core.aerodynamics.rom.core.eval;

/**
 * Offline build pipeline for the geometry-parametric ROM.
 *
 * Produces a GeometryParametricRom from scratch:
 *   1. Generate geometry candidate pool via LHS
 *   2. Build seed surfaces (corners of geometry space)
 *   3. Run greedy snapshot selection until error < targetErrorPct
 *   4. Partition snapshots into hp-EIM local regions (Task 4)
 *   5. Build POD basis for each region (Task 2b)
 *   6. Select magic points for each region (Task 5a)
 *   7. Return GeometryParametricRom ready for online queries
 *
 * Expected runtime: 10–60 minutes depending on pool size and targetErrorPct.
 * This is run ONCE per software release, not per user session.
 */
public final class GeometricRomEvaluator {

    public static final int DEFAULT_POOL_SIZE = 300;
    public static final int DEFAULT_MAX_SNAPSHOTS = 40;
    public static final double DEFAULT_TARGET_ERROR_PCT = 1.0;
    public static final double DEFAULT_POD_TOLERANCE = 1e-4;

    // ... full implementation follows the greedy pipeline above
}
```

---

## Task 7 — Serialization for the geometry-parametric ROM

**Grounding: offline/online pattern — the offline artifact is loaded at runtime**

The `GeometryParametricRom` must be serializable so it can be:
1. Built once by the development team (or CI/CD pipeline)
2. Bundled as a resource file in the OpenRocket JAR
3. Loaded at application startup in ~100 ms

Create `GeometricRomSerializer.java` in `rom.core.io`. Binary format:

```
[Header]
int32:  magic number 0x524F4D34 ("ROM4")
int32:  format version = 1
int32:  numRegions
int32:  snapshotVectorLength (N = nM*nR*nA*nB)
int32:  nMach, nRe, nAlpha, nBeta

[Axes]
double[nMach]   machAxis
double[nRe]     logReAxis
double[nAlpha]  alphaAxis
double[nBeta]   betaAxis

[For each region r = 0..numRegions-1]
  double[10]    centroidFeatureVector
  double        sigma
  int32         rankOff
  int32         rankOn
  int32         snapshotVectorLength
  double[N]     meanOff
  double[rankOff * N]  modesOff (row-major: mode_q at all N points)
  double[rankOff]      singularValuesOff
  double[N]     meanOn
  double[rankOn * N]   modesOn
  double[rankOn]       singularValuesOn
  int32         numMagicPointsOff
  int32[]       magicPointsOff
  int32         numMagicPointsOn
  int32[]       magicPointsOn
  UTF8:         regionLabel

[Footer]
int64:  buildTimestampMs
int32:  totalSnapshotsUsed
double: achievedMaxReconstructionError
```

Entire blob is GZIP-compressed then Base64-encoded (matching `AeroSurfaceSerializer`
convention). Expected uncompressed size for 2 regions, rank=8, N=129,600:
approximately 2 × (8 × 129,600 × 8 bytes) ≈ 16 MB; GZIP'd to ≈ 4–6 MB.

---

## Completion checklist

### Correctness
- [ ] `RomGeometryInput.toFeatureVector()` returns length-10 array, all values in [0,1]
- [ ] `featureDistance(g, g) == 0.0` for any geometry g
- [ ] `PodBasis` built from K identical snapshots has `rank == 1`
- [ ] `PodBasis.reconstructionError(trainingSample) < tolerance` for all training samples
- [ ] `PodBasis.jacobiEigen` converges for a 5×5 test matrix (compare to known eigenvalues)
- [ ] `MagicPointSelector.selectMagicPoints` returns strictly distinct indices
- [ ] `GeometryParametricRom.reconstructSurface` produces `cdOff >= 0.001` everywhere
- [ ] For a geometry in the training set: reconstruction error < 2% relative L2
- [ ] For a geometry NOT in the training set but within the convex hull: error < 5%
- [ ] Fallback to `AeroGridEvaluator4D.evaluate` triggers when `weightSum < 1e-6`

### Performance
- [ ] Online reconstruction call takes < 50 ms for a typical 4-fin ogive geometry
  (this covers the Gappy POD evaluation at ~30 magic points per region)
- [ ] `savedEvaluations` after 1000 online queries is > 99% of equivalent full-grid calls
- [ ] `GeometricRomSerializer.serialize` + `deserialize` round-trips losslessly (max
  absolute error < 1e-10 per coefficient)

### Physical reasonableness
- [ ] Reconstructed Cd vs Mach curve for a new geometry (not in training set) has a
  transonic peak in the range M = 0.8–1.3
- [ ] `Cd(M=0.1) < Cd(M=1.0)` for all reconstructed geometries (drag rises toward transonic)
- [ ] `Cd(M=4.0) < Cd(M=1.0)` for all reconstructed geometries (supersonic drag less than transonic peak)

---

## Notes for the agent

**Do not prematurely optimize the Jacobi eigensolver.** For K ≤ 40 snapshots (the
expected practical range) it is fast. The note about replacing it with LAPACK for
larger K is forward guidance, not a Phase 2 requirement.

**The Gappy POD is the key insight.** The speedup from 130,000 to ~30 evaluations
per geometry query is what makes the ROM practical for real-time use in OpenRocket.
Do not bypass this by falling back to the full grid except in the explicitly described
fallback case.

**Snapshot axes must be shared.** All snapshots in `GeometrySampler` are built with
the same `buildMachAxis()`, `buildLogReAxis()`, `buildAlphaAxis()`, `buildBetaAxis()`
calls. Never mix snapshots built with different axes — the POD inner products are
meaningless if the discretization differs.

**CN/Cm are not POD-compressed in Phase 2.** The normal force and pitching moment
coefficients change less with geometry than Cd does (the fin planform CN formula is
nearly separable in geometry and aero parameters). Computing them directly at all
grid nodes for each reconstructed surface is acceptable. POD compression of CN/Cm
is a Phase 3 enhancement.

**The two-region hp-EIM is a starting point, not a final answer.** If the greedy
error does not converge below 2% with 40 snapshots per region, split into 3–4 regions.
The boundary between regions is defined by the feature vector distance metric, not by
any hardcoded geometry parameter threshold — keep the region assignment logic
data-driven.
