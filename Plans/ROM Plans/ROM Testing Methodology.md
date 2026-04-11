# ROM Testing Methodology
## Pre-CFD Validation: Unit, Physics, Integration, and Regression Testing

**Package:** `info.openrocket.core.aerodynamics.rom`  
**Scope:** All testing described here is executable without CFD results.
CFD-based external validation (L₂ field norms, Cp distribution comparison, DPW-style
benchmark runs) is tracked separately and is out of scope for this document.

---

## 1. Overview and Test Taxonomy

The ROM pipeline has four logical layers, each with its own test category:

```
Layer 1 — Physics Models          →  Unit Tests + Physics Checks
  (SkinFrictionModel, BaseDragModel, WaveDragModel,
   InducedDragModel, TransonicBlendingModel)

Layer 2 — Interpolation Kernel    →  Numerical Accuracy Tests
  (PchipInterpolator1D)

Layer 3 — Grid Assembly           →  Integration Tests + Monotonicity
  (DragGridEvaluator, DragSurface)

Layer 4 — Query + Serialization   →  Round-trip + Fidelity Tests
  (DragSurfaceInterpolator, DragSurfaceSerializer)
```

The validation gates that must pass before a `DragSurface` is accepted for use are:

| Gate | Criterion | Method |
|------|-----------|--------|
| G1 | Cd > 0 at all grid nodes | Grid scan |
| G2 | dCd/dM > 0 in transonic rise (M = 0.8–1.2) | Monotonicity check |
| G3 | dCd/dAlpha > 0 for alpha > 0 | Monotonicity check |
| G4 | Plume-on Cd ≤ plume-off Cd at all nodes | Grid comparison |
| G5 | Serialization round-trip error < 1 ULP | Binary diff |
| G6 | Interpolator reproduces grid knots exactly | Knot-recovery test |
| G7 | LOO cross-validation RMSE < 2% | Leave-one-out scan |
| G8 | Max pointwise LOO error < 5% | Leave-one-out scan |

---

## 2. Unit Tests — Physics Model Layer

Each model class is stateless and purely functional, making it straightforward to test
in isolation. All tests use the framework of your choice (JUnit 5 recommended). No
mocking is required.

### 2.1 `SkinFrictionModel`

#### 2.1.1 — Blasius Laminar Limit
At very low Reynolds number the incompressible friction coefficient must converge to the
Blasius flat-plate solution: `Cf = 1.328 / sqrt(Re)`.

```java
// Re_L = 1000 (pure laminar branch in cfIncompressible)
double re = 1000.0;
double expected = 1.328 / Math.sqrt(re);  // ≈ 0.04198
double actual = SkinFrictionModel.cfIncompressible(re, 5e5);
assertRelativeError(actual, expected, 1e-9);
```

#### 2.1.2 — Turbulent Prandtl-Schlichting at High Re
At Re = 1×10⁷ with a smooth wall (re_tr = 5×10⁵), the transition correction must
reduce Cf below the fully turbulent curve. The returned value must be positive and less
than the fully turbulent Prandtl-Schlichting value.

```java
double re = 1e7;
double cf = SkinFrictionModel.cfIncompressible(re, 5e5);
double cf_turb_only = 0.455 / Math.pow(Math.log10(re), 2.58);
assertTrue(cf > 0);
assertTrue(cf < cf_turb_only);
```

#### 2.1.3 — Van Driest II Compressibility
At M = 0 the compressibility correction must be an identity (output equals input).
At M = 2 the correction must reduce Cf (compressible Cf < incompressible).

```java
double cf_inc = 0.003;
// Identity at M=0
assertRelativeError(SkinFrictionModel.vanDriestII(cf_inc, 0.0, 1.0), cf_inc, 1e-6);
// Reduction at M=2
assertTrue(SkinFrictionModel.vanDriestII(cf_inc, 2.0, 1.0) < cf_inc);
```

#### 2.1.4 — Roughness Correction: Limiting Behavior
With roughness = 0 the result must be unchanged (smooth-wall path). With very high
roughness (k_s = 1e-2 m on a 0.5 m body), the rough-wall value must exceed the
smooth-wall value.

```java
double cf_smooth = 0.003;
// No roughness: identity
assertEquals(SkinFrictionModel.cfWithRoughness(cf_smooth, 0.5, 0.0), cf_smooth, 1e-12);
// Heavy roughness: cf_rough > cf_smooth
assertTrue(SkinFrictionModel.cfWithRoughness(cf_smooth, 0.5, 1e-2) > cf_smooth);
```

#### 2.1.5 — Form Factor: Slender vs. Blunt
A very slender body (d/L = 0.02) must have FF close to 1.0. A short blunt body
(d/L = 0.5) must have FF considerably above 1.

```java
assertTrue(SkinFrictionModel.bodyFormFactor(0.02, 1.0) < 1.05);   // nearly slender
assertTrue(SkinFrictionModel.bodyFormFactor(0.5, 1.0) > 1.20);    // blunt
```

#### 2.1.6 — `cdFriction` Positivity and Re Monotonicity
For a standard geometry, `cdFriction` must be strictly positive and must decrease with
increasing Reynolds number (turbulent regime).

```java
RomGeometryParameters g = standardGeometry();  // see Section 7
double cf1 = SkinFrictionModel.cdFriction(0.5, 1e6, g);
double cf2 = SkinFrictionModel.cdFriction(0.5, 1e7, g);
assertTrue(cf1 > 0);
assertTrue(cf1 > cf2);   // higher Re → lower friction drag
```

---

### 2.2 `BaseDragModel`

#### 2.2.1 — Subsonic Positivity
`cdBaseSubsonic` must be positive for any valid `cf_body` and `loOverD`.

```java
for (double cf : new double[]{0.001, 0.003, 0.005}) {
    for (double loD : new double[]{0.0, 0.5, 2.0, 5.0}) {
        assertTrue(BaseDragModel.cdBaseSubsonic(cf, loD) > 0);
    }
}
```

#### 2.2.2 — Transonic Multiplier: Boundary Conditions
The multiplier must equal exactly 1.0 at M = 0.0 and at M = 0.6 (onset of transonic
ramp). It must reach its maximum somewhere in 0.8 ≤ M ≤ 1.1.

```java
assertEquals(1.0, BaseDragModel.transonicMultiplier(0.0),  1e-12);
assertEquals(1.0, BaseDragModel.transonicMultiplier(0.6),  1e-12);

// Find peak in 0.6-2.0
double peak = 0;
for (double m = 0.6; m <= 2.0; m += 0.01) {
    peak = Math.max(peak, BaseDragModel.transonicMultiplier(m));
}
assertTrue(peak > 1.8);   // empirical: peak ~2.0 near M=1
```

#### 2.2.3 — Transonic Multiplier: Monotone Decay Above M = 2
For M > 2 the multiplier must be monotonically decreasing.

```java
double prev = BaseDragModel.transonicMultiplier(2.01);
for (double m = 2.1; m <= 4.0; m += 0.1) {
    double curr = BaseDragModel.transonicMultiplier(m);
    assertTrue(curr <= prev + 1e-9);
    prev = curr;
}
```

#### 2.2.4 — Plume-On ≤ Plume-Off
With a non-zero motor exit area, `cdBasePlumeOn` must be strictly less than
`cdBasePlumeOff`.

```java
RomGeometryParameters g = standardGeometry();
double cf = 0.003;
for (double mach : new double[]{0.3, 0.8, 1.5, 3.0}) {
    double off = BaseDragModel.cdBasePlumeOff(mach, cf, g);
    double on  = BaseDragModel.cdBasePlumeOn(mach,  cf, g);
    assertTrue(on <= off,
        "Plume-on must be ≤ plume-off at M=" + mach);
}
```

#### 2.2.5 — Zero Motor Exit Degeneracy
When `motorExitArea = 0`, `cdBasePlumeOn` must equal `cdBasePlumeOff` exactly.

```java
RomGeometryParameters gNoMotor = standardGeometry().withMotorExitDiameter(0.0);
double cf = 0.003;
double mach = 0.5;
assertEquals(
    BaseDragModel.cdBasePlumeOff(mach, cf, gNoMotor),
    BaseDragModel.cdBasePlumeOn(mach,  cf, gNoMotor),
    1e-12
);
```

---

### 2.3 `WaveDragModel`

#### 2.3.1 — Drag Divergence Mach: Physical Range
`dragDivergenceMach` must always return a value in the range [0.68, 0.90] for
fineness ratios lN/d ∈ [0.5, 6.0].

```java
RomGeometryParameters g = standardGeometry();
for (double lnOverD : new double[]{0.5, 1.0, 2.0, 4.0, 6.0}) {
    g = g.withNoseLength(lnOverD * g.maxDiameter);
    double mdd = WaveDragModel.dragDivergenceMach(g);
    assertTrue(mdd >= 0.68 && mdd <= 0.90,
        "M_DD out of range for lN/d=" + lnOverD);
}
```

#### 2.3.2 — Wave Drag is Zero Subsonic
`cdNoseWaveSupersonic` and `cdFinWaveSupersonic` must return 0 at M ≤ 1.

```java
RomGeometryParameters g = standardGeometry();
assertEquals(0.0, WaveDragModel.cdFinWaveSupersonic(0.99, g), 1e-12);
// Note: nose wave drag is not strictly zero at M=1 due to searsHaackDecay(1.0)=1.0
// but it must be non-negative
assertTrue(WaveDragModel.cdNoseWaveSupersonic(0.99, g) >= 0);
```

#### 2.3.3 — Wave Drag Decay at High Mach
Both wave drag components must decrease as Mach increases beyond 2.0 (Sears-Haack
decay and 1/sqrt(M²-1) scaling).

```java
RomGeometryParameters g = standardGeometry();
double wd_at2 = WaveDragModel.cdNoseWaveSupersonic(2.0, g);
double wd_at4 = WaveDragModel.cdNoseWaveSupersonic(4.0, g);
assertTrue(wd_at4 < wd_at2);

double fw_at2 = WaveDragModel.cdFinWaveSupersonic(2.0, g);
double fw_at4 = WaveDragModel.cdFinWaveSupersonic(4.0, g);
assertTrue(fw_at4 < fw_at2);
```

#### 2.3.4 — Von Karman < Ogive < Conical Wave Drag
Shape ordering must be preserved: Von Karman ogive must have the lowest nose wave
drag of the named shapes; conical must be highest.

```java
double mach = 2.0;
RomGeometryParameters g = standardGeometry();  // OGIVE base
double cd_vk    = WaveDragModel.cdNoseWaveSupersonic(mach, g.withNoseShape(NoseShape.VON_KARMAN));
double cd_ogive = WaveDragModel.cdNoseWaveSupersonic(mach, g.withNoseShape(NoseShape.OGIVE));
double cd_cone  = WaveDragModel.cdNoseWaveSupersonic(mach, g.withNoseShape(NoseShape.CONICAL));
assertTrue(cd_vk < cd_ogive);
assertTrue(cd_ogive < cd_cone);
```

---

### 2.4 `InducedDragModel`

#### 2.4.1 — Zero Alpha Returns Zero Induced Drag
```java
RomGeometryParameters g = standardGeometry();
assertEquals(0.0, InducedDragModel.cdInduced(0.0, 0.5, g), 1e-12);
```

#### 2.4.2 — Induced Drag Increases with Alpha²
For small AoA (linear regime), `cdInduced` must grow approximately as alpha². Verify
that doubling alpha approximately quadruples induced drag.

```java
RomGeometryParameters g = standardGeometry();
double cd1 = InducedDragModel.cdInduced(Math.toRadians(2.0), 0.5, g);
double cd2 = InducedDragModel.cdInduced(Math.toRadians(4.0), 0.5, g);
// cd2/cd1 should be ~ 4.0 (alpha² scaling)
double ratio = cd2 / cd1;
assertTrue(ratio > 3.5 && ratio < 5.0,
    "Expected ~4x ratio for 2x AoA, got " + ratio);
```

#### 2.4.3 — Protuberance Factor Clamp
`setProtuberanceFactor` must clamp its input to [1.0, 1.20].

```java
InducedDragModel.setProtuberanceFactor(0.5);
assertEquals(1.0, InducedDragModel.protuberanceFactor(), 1e-12);

InducedDragModel.setProtuberanceFactor(2.0);
assertEquals(1.20, InducedDragModel.protuberanceFactor(), 1e-12);

InducedDragModel.setProtuberanceFactor(1.04);  // reset to default
```

#### 2.4.4 — Compressibility Factor Continuity at M = 0.8 and M = 1.2
The smoothstep blending must produce a continuous (no jump) transition. Evaluate
`cdInduced` on both sides of M = 0.8 and M = 1.2 with tiny epsilon.

```java
RomGeometryParameters g = standardGeometry();
double alpha = Math.toRadians(5.0);
double eps = 1e-4;

double left_08  = InducedDragModel.cdInduced(alpha, 0.8 - eps, g);
double right_08 = InducedDragModel.cdInduced(alpha, 0.8 + eps, g);
assertRelativeError(left_08, right_08, 0.005);  // < 0.5% discontinuity

double left_12  = InducedDragModel.cdInduced(alpha, 1.2 - eps, g);
double right_12 = InducedDragModel.cdInduced(alpha, 1.2 + eps, g);
assertRelativeError(left_12, right_12, 0.005);
```

---

### 2.5 `TransonicBlendingModel`

#### 2.5.1 — Sigma Weights Sum to Unity
For all Mach numbers, the three sigma weights must sum to exactly 1.0.

```java
for (double m = 0.01; m <= 4.0; m += 0.01) {
    double sum = TransonicBlendingModel.sigmaSubsonic(m)
               + TransonicBlendingModel.sigmaTransonic(m)
               + TransonicBlendingModel.sigmaSupersonic(m);
    assertEquals(1.0, sum, 1e-10,
        "Sigma weights do not sum to 1 at M=" + m);
}
```

#### 2.5.2 — Blend Recovers Pure Inputs at Regime Extremes
At M = 0.1 (deep subsonic) the blend must return `cd_sub` essentially unchanged.
At M = 3.5 (deep supersonic) it must return `cd_sup` essentially unchanged.

```java
double sub = 0.25, trans = 0.50, sup = 0.35;
assertRelativeError(
    TransonicBlendingModel.blend(0.1, sub, trans, sup), sub, 0.001);
assertRelativeError(
    TransonicBlendingModel.blend(3.5, sub, trans, sup), sup, 0.001);
```

#### 2.5.3 — Transonic Peak Factor is Shape-Ordered
A conical nose (peakFactor = 2.2) must produce a higher transonic peak than Von Karman
(peakFactor = 1.6) for the same subsonic Cd.

```java
double cd_sub = 0.30;
RomGeometryParameters gVK   = standardGeometry().withNoseShape(NoseShape.VON_KARMAN);
RomGeometryParameters gCone = standardGeometry().withNoseShape(NoseShape.CONICAL);
assertTrue(
    TransonicBlendingModel.transonicPeakCd(cd_sub, gCone) >
    TransonicBlendingModel.transonicPeakCd(cd_sub, gVK)
);
```

---

## 3. Numerical Accuracy Tests — `PchipInterpolator1D`

### 3.1 — Knot Recovery (Exact Interpolation at Training Points)
A PCHIP interpolant must reproduce its input values exactly at every knot.

```java
double[] x = {0.0, 1.0, 2.0, 3.0, 4.0};
double[] y = {0.1, 0.25, 0.18, 0.30, 0.22};
PchipInterpolator1D interp = new PchipInterpolator1D(x, y);
for (int i = 0; i < x.length; i++) {
    assertEquals(y[i], interp.evaluate(x[i]), 1e-12,
        "Knot recovery failed at x=" + x[i]);
}
```

### 3.2 — Monotonicity Preservation
On a monotone increasing sequence, PCHIP must produce no overshoot.

```java
double[] x = {0.0, 1.0, 2.0, 3.0};
double[] y = {0.10, 0.20, 0.25, 0.28};  // monotone increasing, decelerating
PchipInterpolator1D interp = new PchipInterpolator1D(x, y);
double prev = interp.evaluate(0.0);
for (double xi = 0.01; xi <= 3.0; xi += 0.01) {
    double curr = interp.evaluate(xi);
    assertTrue(curr >= prev - 1e-9,
        "Monotonicity violated at x=" + xi);
    prev = curr;
}
```

### 3.3 — Boundary Clamping
Values outside [x[0], x[n-1]] must be clamped to the boundary values.

```java
double[] x = {1.0, 2.0, 3.0};
double[] y = {0.2, 0.5, 0.4};
PchipInterpolator1D interp = new PchipInterpolator1D(x, y);
assertEquals(0.2, interp.evaluate(0.0), 1e-12);   // left clamp
assertEquals(0.4, interp.evaluate(99.0), 1e-12);  // right clamp
```

### 3.4 — Linear Data Reproduction
On exactly linear input data, PCHIP must reproduce a straight line to floating-point
precision.

```java
double[] x = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0};
double[] y = new double[x.length];
for (int i = 0; i < x.length; i++) y[i] = 0.1 + 0.05 * x[i];
PchipInterpolator1D interp = new PchipInterpolator1D(x, y);
for (double xi = 0.0; xi <= 5.0; xi += 0.1) {
    double expected = 0.1 + 0.05 * xi;
    assertEquals(expected, interp.evaluate(xi), 1e-10);
}
```

### 3.5 — Two-Point Degenerate Case
With only two knots, PCHIP must produce the linear interpolant exactly.

```java
PchipInterpolator1D interp = new PchipInterpolator1D(
    new double[]{0.0, 1.0}, new double[]{0.2, 0.6});
assertEquals(0.4, interp.evaluate(0.5), 1e-12);
```

### 3.6 — `IllegalArgumentException` for Bad Inputs
Mismatched array lengths or fewer than two points must throw.

```java
assertThrows(IllegalArgumentException.class, () ->
    new PchipInterpolator1D(new double[]{1.0}, new double[]{0.3}));
assertThrows(IllegalArgumentException.class, () ->
    new PchipInterpolator1D(new double[]{0.0, 1.0}, new double[]{0.1}));
```

---

## 4. Integration Tests — `DragGridEvaluator`

### 4.1 — Axis Builder Correctness

#### 4.1.1 — Mach Axis Length and Strict Monotonicity
```java
double[] mach = DragGridEvaluator.buildMachAxis();
assertEquals(DragGridEvaluator.N_MACH, mach.length);
for (int i = 1; i < mach.length; i++) {
    assertTrue(mach[i] > mach[i - 1],
        "Mach axis not strictly monotone at index " + i);
}
assertEquals(0.01, mach[0], 1e-10);
assertEquals(4.0,  mach[mach.length - 1], 1e-6);
```

#### 4.1.2 — Log-Re Axis Covers Correct Range
```java
double[] logRe = DragGridEvaluator.buildLogReAxis();
assertEquals(DragGridEvaluator.N_RE, logRe.length);
assertEquals(Math.log10(1e4), logRe[0], 1e-10);
assertEquals(Math.log10(1e8), logRe[logRe.length - 1], 1e-10);
```

#### 4.1.3 — Alpha Axis Covers 0 to 15 Degrees
```java
double[] alpha = DragGridEvaluator.buildAlphaAxis();
assertEquals(DragGridEvaluator.N_ALPHA, alpha.length);
assertEquals(0.0,  alpha[0], 1e-10);
assertEquals(15.0, alpha[alpha.length - 1], 1e-6);
```

### 4.2 — Point-Level Physics Checks on `computeCdPlumeOff`

These tests call the package-private static method directly (or via a thin test wrapper)
to exercise specific physics combinations before the full grid is built.

#### 4.2.1 — Positivity at All Canonical Points
```java
RomGeometryParameters g = standardGeometry();
double[][] cases = {
    {0.1, 1e5, 0.0}, {0.5, 1e6, 0.0}, {0.9, 1e6, 0.0},
    {1.1, 1e6, 0.0}, {2.0, 1e7, 0.0}, {3.5, 1e7, 0.0},
    {0.5, 1e6, Math.toRadians(10.0)}
};
for (double[] c : cases) {
    double cd = DragGridEvaluator.computeCdPlumeOff(c[0], c[1], c[2], g);
    assertTrue(cd > 0, "Cd not positive at M=" + c[0] + " Re=" + c[1]);
}
```

#### 4.2.2 — Transonic Rise at Zero AoA
`computeCdPlumeOff` at M = 1.0 must exceed the value at M = 0.5 (same Re, same AoA = 0).

```java
RomGeometryParameters g = standardGeometry();
double re = 1e6;
double cd_sub = DragGridEvaluator.computeCdPlumeOff(0.5, re, 0.0, g);
double cd_trans = DragGridEvaluator.computeCdPlumeOff(1.0, re, 0.0, g);
assertTrue(cd_trans > cd_sub,
    "No transonic rise: Cd(M=1.0)=" + cd_trans + " ≤ Cd(M=0.5)=" + cd_sub);
```

#### 4.2.3 — Supersonic Decay
`computeCdPlumeOff` at M = 3.0 must be less than the transonic peak at M = 1.0.

```java
RomGeometryParameters g = standardGeometry();
double re = 1e6;
double cd_peak = DragGridEvaluator.computeCdPlumeOff(1.0, re, 0.0, g);
double cd_sup  = DragGridEvaluator.computeCdPlumeOff(3.0, re, 0.0, g);
assertTrue(cd_sup < cd_peak,
    "No supersonic decay: Cd(M=3)=" + cd_sup + " ≥ Cd(M=1)=" + cd_peak);
```

#### 4.2.4 — AoA Increment is Positive
At any point, adding AoA must increase Cd.

```java
RomGeometryParameters g = standardGeometry();
double re = 1e6;
for (double mach : new double[]{0.5, 1.0, 2.0}) {
    double cd0   = DragGridEvaluator.computeCdPlumeOff(mach, re, 0.0, g);
    double cd10  = DragGridEvaluator.computeCdPlumeOff(mach, re, Math.toRadians(10.0), g);
    assertTrue(cd10 > cd0,
        "AoA increment not positive at M=" + mach);
}
```

#### 4.2.5 — Plume-On ≤ Plume-Off Everywhere
```java
RomGeometryParameters g = standardGeometry();
double re = 1e6;
for (double mach : new double[]{0.3, 0.8, 1.2, 2.5}) {
    double off = DragGridEvaluator.computeCdPlumeOff(mach, re, 0.0, g);
    double on  = DragGridEvaluator.computeCdPlumeOn(mach, re, 0.0, g);
    assertTrue(on <= off + 1e-12,
        "Plume-on > Plume-off at M=" + mach);
}
```

### 4.3 — Full `DragSurface` Build and Grid Validation

These tests build the complete 80×20×10 grid (approximately 300 ms on a modern CPU).

#### 4.3.1 — Grid Dimensions Match Configuration
```java
DragSurface s = DragGridEvaluator.evaluate(standardGeometry(), null);
assertEquals(DragGridEvaluator.N_MACH,  s.machAxis.length);
assertEquals(DragGridEvaluator.N_RE,    s.logReAxis.length);
assertEquals(DragGridEvaluator.N_ALPHA, s.alphaAxis.length);
assertEquals(DragGridEvaluator.N_MACH,  s.cdPlumeOff.length);
assertEquals(DragGridEvaluator.N_RE,    s.cdPlumeOff[0].length);
assertEquals(DragGridEvaluator.N_ALPHA, s.cdPlumeOff[0][0].length);
```

#### 4.3.2 — Gate G1: All Grid Values Positive
```java
DragSurface s = DragGridEvaluator.evaluate(standardGeometry(), null);
for (int im = 0; im < s.machAxis.length; im++) {
    for (int ir = 0; ir < s.logReAxis.length; ir++) {
        for (int ia = 0; ia < s.alphaAxis.length; ia++) {
            assertTrue(s.cdPlumeOff[im][ir][ia] > 0,
                "cdPlumeOff not positive at im=" + im);
            assertTrue(s.cdPlumeOn[im][ir][ia] > 0,
                "cdPlumeOn not positive at im=" + im);
        }
    }
}
```

#### 4.3.3 — Gate G2: Transonic Rise in Mach Direction
At each (Re, alpha) slice, scan the Mach axis to confirm that the maximum Cd occurs
between M = 0.85 and M = 1.25, not in the subsonic or supersonic extremes.

```java
DragSurface s = DragGridEvaluator.evaluate(standardGeometry(), null);
for (int ir = 0; ir < s.logReAxis.length; ir++) {
    double[] cd = new double[s.machAxis.length];
    for (int im = 0; im < s.machAxis.length; im++) {
        cd[im] = s.cdPlumeOff[im][ir][0];  // alpha = 0
    }
    int peakIdx = argmax(cd);
    double peakMach = s.machAxis[peakIdx];
    assertTrue(peakMach >= 0.85 && peakMach <= 1.30,
        "Peak Cd at unexpected Mach=" + peakMach + " for logRe=" + s.logReAxis[ir]);
}
```

#### 4.3.4 — Gate G3: Cd Increases Monotonically with Alpha
At each (Mach, Re) node, Cd must be non-decreasing with alpha.

```java
DragSurface s = DragGridEvaluator.evaluate(standardGeometry(), null);
for (int im = 0; im < s.machAxis.length; im++) {
    for (int ir = 0; ir < s.logReAxis.length; ir++) {
        for (int ia = 1; ia < s.alphaAxis.length; ia++) {
            assertTrue(s.cdPlumeOff[im][ir][ia] >= s.cdPlumeOff[im][ir][ia - 1] - 1e-9,
                "Cd not monotone with alpha at im=" + im + " ir=" + ir);
        }
    }
}
```

#### 4.3.5 — Gate G4: Plume-On ≤ Plume-Off at All Nodes
```java
DragSurface s = DragGridEvaluator.evaluate(standardGeometry(), null);
for (int im = 0; im < s.machAxis.length; im++) {
    for (int ir = 0; ir < s.logReAxis.length; ir++) {
        for (int ia = 0; ia < s.alphaAxis.length; ia++) {
            assertTrue(s.cdPlumeOn[im][ir][ia] <= s.cdPlumeOff[im][ir][ia] + 1e-12,
                "Plume-on > plume-off at im=" + im + " ir=" + ir + " ia=" + ia);
        }
    }
}
```

#### 4.3.6 — Geometry Hash is Non-Empty and Deterministic
```java
RomGeometryParameters g = standardGeometry();
DragSurface s1 = DragGridEvaluator.evaluate(g, null);
DragSurface s2 = DragGridEvaluator.evaluate(g, null);
assertFalse(s1.geometryHash.isEmpty());
assertEquals(s1.geometryHash, s2.geometryHash);
```

---

## 5. Interpolator Tests — `DragSurfaceInterpolator`

### 5.1 — Gate G6: Exact Knot Recovery
Querying the interpolator at every grid point must return the stored value to within
double-precision floating-point tolerance.

```java
DragSurface s = DragGridEvaluator.evaluate(standardGeometry(), null);
DragSurfaceInterpolator interp = new DragSurfaceInterpolator(s);

for (int im = 0; im < s.machAxis.length; im++) {
    double mach = s.machAxis[im];
    for (int ir = 0; ir < s.logReAxis.length; ir++) {
        double re = Math.pow(10.0, s.logReAxis[ir]);
        for (int ia = 0; ia < s.alphaAxis.length; ia++) {
            double alpha = s.alphaAxis[ia];
            double expected = s.cdPlumeOff[im][ir][ia];
            double actual   = interp.queryCdPlumeOff(mach, re, alpha);
            assertRelativeError(actual, expected, 1e-8,
                "Knot recovery failed at mach=" + mach + " re=" + re + " alpha=" + alpha);
        }
    }
}
```

### 5.2 — Out-of-Bounds Clamping Produces Finite, Positive Values
```java
DragSurfaceInterpolator interp = new DragSurfaceInterpolator(
    DragGridEvaluator.evaluate(standardGeometry(), null));
// Below Mach axis minimum
assertTrue(interp.queryCdPlumeOff(0.0001, 1e6, 0.0) > 0);
// Above Mach axis maximum
assertTrue(interp.queryCdPlumeOff(99.0, 1e6, 0.0) > 0);
// Negative alpha (should use absolute value)
double cdPos = interp.queryCdPlumeOff(0.5, 1e6,  5.0);
double cdNeg = interp.queryCdPlumeOff(0.5, 1e6, -5.0);
assertEquals(cdPos, cdNeg, 1e-12);  // symmetric
```

### 5.3 — NaN / Infinity Guard
No query at any physically meaningful input should return NaN or infinity.

```java
DragSurfaceInterpolator interp = new DragSurfaceInterpolator(
    DragGridEvaluator.evaluate(standardGeometry(), null));
double[][] testPoints = {
    {0.5, 1e6, 0.0}, {1.0, 1e5, 5.0}, {2.5, 1e8, 15.0},
    {0.01, 1e4, 0.0}, {4.0, 1e8, 15.0}
};
for (double[] p : testPoints) {
    double cd = interp.queryCdPlumeOff(p[0], p[1], p[2]);
    assertTrue(Double.isFinite(cd), "Non-finite Cd at M=" + p[0]);
    assertTrue(cd >= 0.001, "Cd below floor at M=" + p[0]);
}
```

### 5.4 — Mid-Point Smoothness (No Ripple)
At a fixed (Re, alpha), the Cd vs Mach curve when queried at 500 dense points must
have no derivative discontinuity larger than 10× the average derivative magnitude.
This detects splicing artifacts between PCHIP segments.

```java
DragSurfaceInterpolator interp = new DragSurfaceInterpolator(
    DragGridEvaluator.evaluate(standardGeometry(), null));
int N = 500;
double re = 1e6;
double[] cd = new double[N];
for (int i = 0; i < N; i++) {
    double mach = 0.01 + i * (4.0 - 0.01) / (N - 1);
    cd[i] = interp.queryCdPlumeOff(mach, re, 0.0);
}
// Compute forward differences
double[] diff = new double[N - 1];
double avgAbsDiff = 0;
for (int i = 0; i < N - 1; i++) {
    diff[i] = cd[i + 1] - cd[i];
    avgAbsDiff += Math.abs(diff[i]);
}
avgAbsDiff /= (N - 1);
// Second differences: detect sudden slope reversals
for (int i = 1; i < N - 1; i++) {
    double jump = Math.abs(diff[i] - diff[i - 1]);
    assertTrue(jump < 10 * avgAbsDiff,
        "Excessive slope change at step " + i);
}
```

---

## 6. Serialization Round-Trip Tests — `DragSurfaceSerializer`

### 6.1 — Gate G5: Bit-Perfect Round-Trip
All grid values must survive a serialize/deserialize cycle without any floating-point
modification.

```java
DragSurface original = DragGridEvaluator.evaluate(standardGeometry(), null);
byte[] bytes = DragSurfaceSerializer.serialize(original);
DragSurface restored = DragSurfaceSerializer.deserialize(bytes);

// Compare every element
for (int im = 0; im < original.machAxis.length; im++) {
    assertEquals(original.machAxis[im], restored.machAxis[im], 0);  // exact
    for (int ir = 0; ir < original.logReAxis.length; ir++) {
        for (int ia = 0; ia < original.alphaAxis.length; ia++) {
            assertEquals(
                original.cdPlumeOff[im][ir][ia],
                restored.cdPlumeOff[im][ir][ia],
                0,  // zero tolerance = exact bit equality
                "Mismatch at im=" + im + " ir=" + ir + " ia=" + ia
            );
        }
    }
}
```

### 6.2 — Geometry Hash and Metadata Preserved
```java
DragSurface original = DragGridEvaluator.evaluate(standardGeometry(), null);
byte[] bytes = DragSurfaceSerializer.serialize(original);
DragSurface restored = DragSurfaceSerializer.deserialize(bytes);

assertEquals(original.geometryHash, restored.geometryHash);
assertEquals(original.machAxis.length, restored.machAxis.length);
assertEquals(original.logReAxis.length, restored.logReAxis.length);
assertEquals(original.alphaAxis.length, restored.alphaAxis.length);
```

### 6.3 — Corrupted Bytes Throw `IllegalStateException`
```java
DragSurface s = DragGridEvaluator.evaluate(standardGeometry(), null);
byte[] bytes = DragSurfaceSerializer.serialize(s);
bytes[10] ^= 0xFF;  // corrupt one byte
assertThrows(IllegalStateException.class,
    () -> DragSurfaceSerializer.deserialize(bytes));
```

### 6.4 — Empty Byte Array Throws
```java
assertThrows(IllegalStateException.class,
    () -> DragSurfaceSerializer.deserialize(new byte[0]));
```

---

## 7. Cross-Validation Tests — LOO Error Budget

Leave-one-out (LOO) cross-validation measures how well the ROM predicts a held-out
grid point from the surrounding points. It is computed purely from the analytic model
and does not require CFD data.

### Method

For each of the `N_MACH` Mach axis points:

1. Build a **reduced** Mach axis excluding index `k`.
2. Evaluate `computeCdPlumeOff` at all other Mach points.
3. Fit a `PchipInterpolator1D` to the reduced set.
4. Query the interpolant at the excluded Mach value.
5. Compute the relative error: `|predicted - actual| / actual`.

Do this for each (Re, alpha) slice and collect RMSE and max error across all nodes.

### 7.1 — Gate G7: LOO RMSE < 2%
```java
RomGeometryParameters g = standardGeometry();
double[] machFull = DragGridEvaluator.buildMachAxis();
double[] logReFull = DragGridEvaluator.buildLogReAxis();
double[] alphaFull = DragGridEvaluator.buildAlphaAxis();
int nM = machFull.length, nR = logReFull.length, nA = alphaFull.length;

double sumSq = 0;
int count = 0;

for (int k = 1; k < nM - 1; k++) {  // skip endpoints (no LOO for boundary)
    // Build reduced Mach axis (remove index k)
    double[] machReduced = removeIndex(machFull, k);

    for (int ir = 0; ir < nR; ir++) {
        double re = Math.pow(10.0, logReFull[ir]);
        for (int ia = 0; ia < nA; ia++) {
            double alpha = Math.toRadians(alphaFull[ia]);

            // Evaluate at all other Mach points
            double[] cdReduced = new double[machReduced.length];
            for (int j = 0; j < machReduced.length; j++) {
                cdReduced[j] = DragGridEvaluator.computeCdPlumeOff(machReduced[j], re, alpha, g);
            }

            // True value at held-out point
            double cdTrue = DragGridEvaluator.computeCdPlumeOff(machFull[k], re, alpha, g);

            // Predicted via interpolation
            double cdPred = new PchipInterpolator1D(machReduced, cdReduced)
                                .evaluate(machFull[k]);

            double relErr = Math.abs(cdPred - cdTrue) / cdTrue;
            sumSq += relErr * relErr;
            count++;
        }
    }
}

double rmse = Math.sqrt(sumSq / count) * 100.0;  // in percent
assertTrue(rmse < 2.0,
    "LOO RMSE = " + rmse + "% exceeds 2% gate");
```

### 7.2 — Gate G8: Max LOO Pointwise Error < 5%
Using the same loop as above, track the maximum relative error. Log the Mach, Re, and
alpha of the worst-case point for diagnostics.

```java
double maxErr = 0;
double worstMach = 0, worstRe = 0, worstAlpha = 0;

// ... (same loop structure as Gate G7, tracking max instead of sum-sq)

assertTrue(maxErr < 5.0,
    "Max LOO error = " + maxErr + "% exceeds 5% gate at M=" + worstMach
    + " Re=" + worstRe + " alpha=" + worstAlpha);
```

---

## 8. Regression (Golden-Value) Tests

These tests record the expected Cd at specific canonical flight conditions for a
fixed reference geometry. They detect unintended behavioral changes during refactoring.

The reference geometry is a representative high-power sounding rocket:
- `bodyLength` = 2.0 m, `maxDiameter` = 0.076 m, `finessRatio` = 26.3
- `noseLength` = 0.30 m, `noseShape` = OGIVE
- 4 fins: `finRootChord` = 0.15 m, `finTipChord` = 0.06 m, `finSpan` = 0.09 m,
  `finThickness` = 0.003 m, `finSweepAngle` = 45°
- `boattailLength` = 0.08 m, `boattailBaseDiameter` = 0.054 m
- `motorExitDiameter` = 0.038 m
- `surfaceRoughness` = 6.4×10⁻⁶ m (smooth paint)
- `referenceArea` = π×(0.038)² m²

| Mach | Re (body) | Alpha (°) | Mode | Expected Cd | Tolerance |
|------|-----------|-----------|------|-------------|-----------|
| 0.30 | 5.0×10⁵  | 0         | Off  | ~0.48       | ±5%       |
| 0.80 | 1.5×10⁶  | 0         | Off  | ~0.52       | ±5%       |
| 1.00 | 2.0×10⁶  | 0         | Off  | ~0.72       | ±5%       |
| 1.50 | 3.0×10⁶  | 0         | Off  | ~0.55       | ±5%       |
| 2.50 | 5.0×10⁶  | 0         | Off  | ~0.42       | ±5%       |
| 0.50 | 1.0×10⁶  | 5         | Off  | ~0.56       | ±5%       |
| 0.50 | 1.0×10⁶  | 0         | On   | ~0.42       | ±5%       |

> **Note:** Replace the `~` approximate values with your first confirmed run. After that,
> lock them in as the golden values. The tolerance is deliberately wide (5%) here to
> survive minor physics tuning; tighten to 1% once a stable baseline is established.

```java
@Test
void regressionCanonicalPoints() {
    RomGeometryParameters g = referenceGeometry();
    DragGridEvaluator.evaluate(g, null);  // warm-up

    assertCd("subsonic M=0.3",
        DragGridEvaluator.computeCdPlumeOff(0.3, 5e5, 0.0, g), 0.48, 0.05);
    assertCd("transonic peak M=1.0",
        DragGridEvaluator.computeCdPlumeOff(1.0, 2e6, 0.0, g), 0.72, 0.05);
    assertCd("supersonic M=2.5",
        DragGridEvaluator.computeCdPlumeOff(2.5, 5e6, 0.0, g), 0.42, 0.05);
    assertCd("AoA=5° M=0.5",
        DragGridEvaluator.computeCdPlumeOff(0.5, 1e6, Math.toRadians(5.0), g), 0.56, 0.05);
    assertCd("plume-on M=0.5",
        DragGridEvaluator.computeCdPlumeOn(0.5, 1e6, 0.0, g), 0.42, 0.05);
}
```

---

## 9. Helper Fixtures and Utilities

### 9.1 — Standard Geometry Factory (`standardGeometry()`)

```java
/** Reference geometry used across all tests. */
static RomGeometryParameters standardGeometry() {
    // Adjust the builder calls to match your actual RomGeometryParameters API
    return new RomGeometryParameters.Builder()
        .bodyLength(2.0)
        .maxDiameter(0.076)
        .noseLength(0.30)
        .noseShape(RomGeometryParameters.NoseShape.OGIVE)
        .finessRatio(26.3)
        .finCount(4)
        .finRootChord(0.15)
        .finTipChord(0.06)
        .finSpan(0.09)
        .finThickness(0.003)
        .finSweepAngle(45.0)
        .finWettedArea(4 * 0.5 * (0.15 + 0.06) * 0.09 * 2.0)
        .boattailLength(0.08)
        .boattailBaseDiameter(0.054)
        .motorExitDiameter(0.038)
        .surfaceRoughness(6.4e-6)
        .wetArea(Math.PI * 0.076 * 2.0)
        .referenceArea(Math.PI * Math.pow(0.038, 2))
        .build();
}
```

### 9.2 — Assertion Helpers

```java
/** Relative error assertion: |actual - expected| / |expected| < tolerance */
static void assertRelativeError(double actual, double expected,
                                 double tolerance) {
    assertRelativeError(actual, expected, tolerance, "");
}

static void assertRelativeError(double actual, double expected,
                                 double tolerance, String msg) {
    double relErr = Math.abs(actual - expected) / Math.max(Math.abs(expected), 1e-30);
    assertTrue(relErr <= tolerance,
        msg + " Relative error " + relErr + " exceeds tolerance " + tolerance
        + " (actual=" + actual + " expected=" + expected + ")");
}

static void assertCd(String label, double actual, double expected, double relTol) {
    assertRelativeError(actual, expected, relTol,
        "[Regression] " + label + ":");
}

static int argmax(double[] arr) {
    int idx = 0;
    for (int i = 1; i < arr.length; i++) {
        if (arr[i] > arr[idx]) idx = i;
    }
    return idx;
}

static double[] removeIndex(double[] arr, int k) {
    double[] out = new double[arr.length - 1];
    int j = 0;
    for (int i = 0; i < arr.length; i++) {
        if (i != k) out[j++] = arr[i];
    }
    return out;
}
```

---

## 10. Test Execution Guide

### Running the Full Suite

```bash
# JUnit 5 via Maven
mvn test -pl core -Dtest="**/rom/**Test" -Dsurefire.failIfNoSpecifiedTests=false

# JUnit 5 via Gradle
./gradlew :core:test --tests "info.openrocket.core.aerodynamics.rom.*"
```

### Run Only Physics Gates (fast, no grid build — ~1s)

```bash
mvn test -Dtest="SkinFrictionModelTest,BaseDragModelTest,WaveDragModelTest,\
InducedDragModelTest,TransonicBlendingModelTest,PchipInterpolatorTest"
```

### Run Integration Tests (requires grid build — ~5–15s)

```bash
mvn test -Dtest="DragGridEvaluatorTest,DragSurfaceInterpolatorTest,\
DragSurfaceSerializerTest"
```

### Run Cross-Validation Gate (LOO sweep — ~30–60s)

```bash
mvn test -Dtest="LooValidationTest"
```

### Expected Output Summary (all passing)

```
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0

Gate Summary:
  G1 Cd positivity      PASS  (16000 nodes)
  G2 Transonic rise     PASS  (20 Re slices)
  G3 Alpha monotone     PASS  (1600 (M,Re) nodes)
  G4 Plume-on ≤ off     PASS  (16000 nodes)
  G5 Serialization      PASS  (exact bit equality)
  G6 Knot recovery      PASS  (16000 interpolation points)
  G7 LOO RMSE           PASS  (x.xx% < 2%)
  G8 LOO max error      PASS  (x.xx% < 5%, worst at M=x.xx)
```

---

## 11. What These Tests Do NOT Cover

The following validation activities are deliberately out of scope for this document and
will be addressed in the CFD Validation Phase:

- Comparison of `cdPlumeOff` or `cdPlumeOn` against RANS simulation data
- Field-level L₂ / L∞ error norms for pressure, density, velocity, temperature
- Experimental drag database comparison (AGARD-B, SOCBT, Army Basic Finner)
- Coast-phase Cd reconstruction from flight telemetry
- Error propagation from Cd uncertainty to apogee prediction
- DPW-style benchmark compliance

These activities require the CFD database to be built first and are tracked in a
separate validation plan document.