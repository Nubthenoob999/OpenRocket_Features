# Phase 3 — Testing and Tuning

## Overview and Goals

Phase 3 is a disciplined validation, profiling, and tuning campaign. No new physics modules
are added. The ROM produced by Phase 2 is systematically tested against public benchmark
data, profiled for runtime and memory, and tuned at the empirical constant level. Every
tuning change is anchored to experimental data, not free-parameter fitting. A permanent
regression test suite is established so that all future code changes are guarded against
accuracy regressions. The phase concludes with a final certification table confirming that
all accuracy and performance targets from Phases 1 and 2 are met simultaneously.

**Acceptance gate:** All Section 9 (Final Acceptance Criteria) rows must pass before Phase 3
is declared complete. No partial credit.

---

## Test Suite Architecture

All tests are JUnit 4. The suite is organized into four layers:

```
core/src/test/java/info/openrocket/core/aerodynamics/rom/
  validation/
    ANFValidationTest.java              (Army-Navy Basic Finner)
    ConeCylinderValidationTest.java     (Danberg ARL-SPP cone-cylinder)
    TangentOgiveFinValidationTest.java  (ARCAS-class finned rocket)
    BoattailPlumeValidationTest.java    (AGARD AR-226 boattailed body)
    RASAeroComparisonTest.java          (Rogers-Cooper 2011 dataset)
  regression/
    CAProfileRegressionTest.java
    CNAlphaRegressionTest.java
    XcpRegressionTest.java
    BaseDragRegressionTest.java
    BLThicknessRegressionTest.java
    TransitionLocationRegressionTest.java
  performance/
    SingleEvaluationTimingTest.java
    CacheBuildTimingTest.java
    CacheQueryLatencyTest.java
    MemoryFootprintTest.java
    FullSimulationTimingTest.java
  integration/
    MotorSwitchContinuityTest.java
    GeometryChangeInvalidationTest.java
    FallbackTransitionTest.java
    CFDImportRoundTripTest.java
    FullTrajectoryAccuracyTest.java
```

---

## Layer 1 — Benchmark Validation Tests

These tests compare ROM output against published wind-tunnel, flight, and RANS data.
Each test class is self-contained: it builds the geometry programmatically, runs the ROM,
and asserts against hardcoded reference values loaded from CSV files in `src/test/resources`.

### Benchmark Case 1 — Army-Navy Basic Finner (ANF)

**Source:** AEDC-TR-76-58, M = 0.6 to 4.8, α = 0°, 4°, 8°  
**Geometry:** L/D = 10, 10° conical nose, 4 wedge fins, span = D, root chord = D

**File:** `validation/ANFValidationTest.java`

```java
public class ANFValidationTest {

    private static PathlineRomCalculator rom;
    private static ANFGeometryBuilder geom;

    @BeforeClass
    public static void setup() {
        geom = new ANFGeometryBuilder();   // programmatic build matching AEDC-TR-76-58
        RomConfiguration config = new RomConfiguration();
        config.N_crit = 6.0;              // powered-flight environment
        rom = new PathlineRomCalculator(geom.build(), config);
    }

    // Test CA at alpha=0 across full Mach sweep
    // Reference: AEDC-TR-76-58 Table 3, zero-lift drag
    @Test
    public void testCA_alpha0_subsonicRange() {
        // M ∈ {0.6, 0.7} : CA within ±7% of AEDC reference
        assertCAWithinBand(0.6, 0.0, referenceCA_ANF(0.6, 0.0), 0.07);
        assertCAWithinBand(0.7, 0.0, referenceCA_ANF(0.7, 0.0), 0.07);
    }

    @Test
    public void testCA_alpha0_transonicRange() {
        // M ∈ {0.80, 0.90, 0.95, 1.00, 1.05, 1.10, 1.20} : CA within ±12%
        double[] transonic = {0.80, 0.90, 0.95, 1.00, 1.05, 1.10, 1.20};
        for (double M : transonic) {
            assertCAWithinBand(M, 0.0, referenceCA_ANF(M, 0.0), 0.12);
        }
    }

    @Test
    public void testCA_alpha0_supersonicRange() {
        // M ∈ {1.5, 2.0, 3.0, 4.0, 4.8} : CA within ±7%
        double[] supersonic = {1.5, 2.0, 3.0, 4.0, 4.8};
        for (double M : supersonic) {
            assertCAWithinBand(M, 0.0, referenceCA_ANF(M, 0.0), 0.07);
        }
    }

    @Test
    public void testCNAlpha_subsonicSupersonic() {
        // CNalpha at M ∈ {0.6, 2.0, 4.0}, alpha = 4 degrees
        // Reference: AEDC-TR-76-58 Table 4
        assertCNAlphaWithinBand(0.6, referenceDataset("ANF_CNa_M06.csv"), 0.08);
        assertCNAlphaWithinBand(2.0, referenceDataset("ANF_CNa_M20.csv"), 0.08);
        assertCNAlphaWithinBand(4.0, referenceDataset("ANF_CNa_M40.csv"), 0.10);
    }

    @Test
    public void testDragRisePeakPresent() {
        // CA at M=0.95 must be strictly greater than CA at M=0.75
        double CA_transonic = evaluateCA(0.95, 0.0);
        double CA_subsonic  = evaluateCA(0.75, 0.0);
        assertTrue("No transonic drag rise detected", CA_transonic > CA_subsonic);
    }

    // Helper: asserts CA within ±relativeTol of reference
    private void assertCAWithinBand(double mach, double alpha, double reference,
                                     double relativeTol) { ... }
}
```

### Benchmark Case 2 — Cone-Cylinder Surface Pressure (Danberg ARL-SPP)

**Source:** Danberg, J.E. "Characteristics of the turbulent BL with heat and mass transfer
at M=6.7." ARL Special Publications. Also: secant-ogive-cylinder Cp from Reklis-Sturek
at M=2, 3.  
**Geometry:** tangent-ogive (L_nose = 3D) + cylinder body, no fins, no boattail

**File:** `validation/ConeCylinderValidationTest.java`

```java
public class ConeCylinderValidationTest {

    @Test
    public void testCpDistribution_M2_alpha0() {
        // Compare Cp(x/L) at 15 stations along windward meridian (phi=0)
        // Reference: Reklis-Sturek M=2 data, loaded from CSV
        // Acceptance: |Cp_ROM - Cp_ref| <= 0.05 at each station
        double[][] refData = loadCSV("reklis_sturek_M2_Cp.csv");  // [x/L, Cp_ref]
        for (double[] row : refData) {
            double x_norm = row[0];
            double Cp_ref = row[1];
            double Cp_rom = romCpAtX(2.0, 0.0, 0.0, x_norm);  // mach, alpha, phi, x/L
            assertEquals("Cp mismatch at x/L=" + x_norm, Cp_ref, Cp_rom, 0.05);
        }
    }

    @Test
    public void testCpDistribution_M3_alpha0() {
        // Same test at M=3
        double[][] refData = loadCSV("reklis_sturek_M3_Cp.csv");
        for (double[] row : refData) {
            double Cp_rom = romCpAtX(3.0, 0.0, 0.0, row[0]);
            assertEquals(row[1], Cp_rom, 0.05);
        }
    }

    @Test
    public void testShockLocation_M2() {
        // The nose shock should detach at the nose tip; bow shock standoff should be minimal
        // For a tangent ogive, surface Cp should begin at Cp_stag and decay smoothly
        // No jump in Cp of > 0.3 should appear at any interior station for M=2, alpha=0
        double[] CpDistribution = romCpDistribution(2.0, 0.0, 0.0);
        double maxJump = maxConsecutiveJump(CpDistribution);
        assertTrue("Unphysical Cp jump detected at interior station", maxJump < 0.30);
    }

    @Test
    public void testBLThickness_M2() {
        // BL momentum thickness theta(x) should grow monotonically from nose
        // Compare to Blasius-like growth on the cylinder section
        double[] thetaDistribution = romThetaDistribution(2.0, 0.0);
        for (int i = 1; i < thetaDistribution.length; i++) {
            assertTrue("BL thickness decreased at station " + i,
                       thetaDistribution[i] >= thetaDistribution[i-1] - 1e-8);
        }
    }
}
```

### Benchmark Case 3 — Tangent-Ogive + 4 Fins (ARCAS-class)

**Source:** NASA TN D-6068, TN D-6495 (force coefficients); TM X-1751 (detailed data)  
**Geometry:** tangent-ogive nose, 4-fin trapezoidal set, no boattail

**File:** `validation/TangentOgiveFinValidationTest.java`

```java
public class TangentOgiveFinValidationTest {

    @Test
    public void testCA_multiMach() {
        // M ∈ {0.3, 0.6, 0.8, 0.95, 1.05, 1.2, 1.5, 2.0, 3.0, 4.0, 5.0} at alpha=0
        // Reference: NASA TN D-6068 Table 2
        double[] machPoints = {0.3, 0.6, 0.8, 0.95, 1.05, 1.2, 1.5, 2.0, 3.0, 4.0, 5.0};
        for (double M : machPoints) {
            double CA_ref = referenceCA_ARCAS(M, 0.0);
            double CA_rom = evaluateCA(M, 0.0);
            double tol = (M > 0.80 && M < 1.20) ? 0.12 : 0.07;
            assertRelativeError("CA at M=" + M, CA_ref, CA_rom, tol);
        }
    }

    @Test
    public void testCNAlpha_multiMach() {
        // dCN/dalpha at M ∈ {0.6, 1.5, 2.0, 3.0, 4.0}
        // Computed by central difference: [CN(+2°) - CN(-2°)] / 4°
        // Reference: NASA TN D-6495 Table 4
        for (double M : new double[]{0.6, 1.5, 2.0, 3.0, 4.0}) {
            double CNa_ref = referenceCNalpha_ARCAS(M);
            double CNa_rom = computeCNalpha(M);
            assertRelativeError("CNalpha at M=" + M, CNa_ref, CNa_rom, 0.08);
        }
    }

    @Test
    public void testXcp_multiMach() {
        // Center of pressure xcp/L at alpha=2° across Mach sweep
        // Reference: NASA TN D-6495 Table 5
        for (double M : new double[]{0.6, 1.5, 2.0, 3.0}) {
            double xcp_ref = referenceXcp_ARCAS(M);
            double xcp_rom = evaluateXcp(M, 2.0);
            assertEquals("xcp mismatch at M=" + M,
                         xcp_ref, xcp_rom, 0.025);  // ±0.025 L
        }
    }

    @Test
    public void testFinsContributeToNormalForce() {
        // At M=2.0, alpha=5 degrees, CN_with_fins > CN_without_fins
        // (ROM with fins minus ROM with fins removed from geometry)
        double CN_fins = evaluateCN_withFins(2.0, 5.0);
        double CN_noFins = evaluateCN_noFins(2.0, 5.0);
        assertTrue("Fins not contributing to CN", CN_fins > CN_noFins);
    }
}
```

### Benchmark Case 4 — Boattailed Body + Cold-Air Plume (AGARD AR-226)

**Source:** AGARD AR-226 afterbody database; Herrin-Dutton AIAA J. 32(1) base flow;
Kayser BRL MR-3353 transonic base pressure.  
**Geometry:** secant-ogive + cylinder + 15° boattail + central cold-air jet port

**File:** `validation/BoattailPlumeValidationTest.java`

```java
public class BoattailPlumeValidationTest {

    @Test
    public void testCpBase_coast_multiMach() {
        // Coast (jet off): Cp_b at M ∈ {0.6, 0.8, 0.95, 1.05, 1.2, 1.5, 2.5, 4.0}
        // Reference: Herrin-Dutton experimental Cp_b
        // Acceptance: |Cp_b_ROM - Cp_b_ref| <= 0.06 absolute
        double[] machPoints = {0.6, 0.8, 0.95, 1.05, 1.2, 1.5, 2.5, 4.0};
        for (double M : machPoints) {
            double Cpb_ref = referenceBaseCp_AGARD(M, false);
            double Cpb_rom = evaluateCpBase(M, false);
            assertEquals("Cp_base mismatch at M=" + M + " (coast)",
                         Cpb_ref, Cpb_rom, 0.06);
        }
    }

    @Test
    public void testCpBase_powered_M25() {
        // Jet on at M=2.5: base Cp should be less negative than jet-off case
        // (plume shields base, reducing suction)
        double Cpb_off = evaluateCpBase(2.5, false);
        double Cpb_on  = evaluateCpBase(2.5, true);
        assertTrue("Jet-on base Cp should be less negative (favorable)",
                   Cpb_on > Cpb_off);  // less negative = less drag
    }

    @Test
    public void testDeltaCA_jetOnOff_M25() {
        // |CA_jet_off - CA_jet_on| is within ±15% of AGARD AR-226 measured Delta_CA
        double deltaCA_ref = referenceDeltaCA_AGARD(2.5);
        double deltaCA_rom = Math.abs(evaluateCA(2.5, false) - evaluateCA(2.5, true));
        assertRelativeError("Delta_CA (jet on/off) at M=2.5", deltaCA_ref, deltaCA_rom, 0.15);
    }

    @Test
    public void testBoattailSeparationFlag() {
        // 15 degree boattail at M=1.5 should exceed critical angle and trigger separation flag
        // beta_crit(1.5) ≈ 11 + 5*(1 - 0.5) = 13.5 degrees; 15 > 13.5
        AerodynamicForces result = evaluateWithWarnings(1.5, 0.0);
        assertTrue("Boattail separation not flagged at M=1.5, 15 deg boattail",
                   result.getWarnings().contains("boattail separation"));
    }

    @Test
    public void testTransonicBasePeak() {
        // Kayser BRL MR-3353: peak Cp_base at M ≈ 1.0 should be -0.30 to -0.38
        double Cpb_peak = evaluateCpBase(1.0, false);
        assertTrue("Transonic base Cp peak out of range", Cpb_peak < -0.28 && Cpb_peak > -0.42);
    }
}
```

### Benchmark Case 5 — RASAero II Comparison

**Source:** Rogers-Cooper 2011 RASAero II validation report (33-flight dataset)  
**Method:** Run the same 5 representative rockets through ROM and RASAero II. Compare CA(M)
curves. ROM must be closer to experiment than RASAero II at ≥ 60% of Mach points.

**File:** `validation/RASAeroComparisonTest.java`

```java
public class RASAeroComparisonTest {

    // Reference rocket geometries and RASAero II reference curves loaded from resources
    // 5 rockets: Apogee Aspire, Nike-X, Talos, LOC Precision Expediter, generic sounding rocket

    @Test
    public void testROM_vs_RASAero_ASpire() {
        // Load RASAero II CA(M) curve and experimental flight data for Aspire
        // Count Mach points where ROM is closer to flight than RASAero II
        int[] result = compareToRASAeroAndFlight("Aspire");
        // result[0] = points where ROM wins; result[1] = total points
        assertTrue("ROM not better than RASAero II at >= 60% of points",
                   result[0] >= 0.60 * result[1]);
    }

    @Test
    public void testApogeeAccuracy_vsFlightData() {
        // For each rocket in the dataset, simulate a full trajectory
        // Compare simulated apogee to measured flight apogee
        // Acceptance: |apogee_ROM - apogee_flight| / apogee_flight <= 3%
        String[] rockets = {"Aspire", "Nike-X", "Talos", "LOC_Expediter", "SoundingRocket"};
        for (String rocket : rockets) {
            double apogee_flight = flightApogee(rocket);
            double apogee_rom    = simulateApogee(rocket);
            double error = Math.abs(apogee_rom - apogee_flight) / apogee_flight;
            assertTrue("Apogee error > 3% for " + rocket, error <= 0.03);
        }
    }
}
```

---

## Layer 2 — Regression Tests

Regression tests establish the "golden baseline" from the Phase 2 ROM. Any subsequent code
change that shifts a regression output by more than the specified tolerance triggers a test
failure. This prevents silent accuracy regressions from code refactoring or constant changes.

Golden baseline values are stored as CSV files in `src/test/resources/regression/`. They
are generated once at the start of Phase 3 and committed to the repository.

### `CAProfileRegressionTest.java`

```java
public class CAProfileRegressionTest {

    // Standard test rocket: tangent-ogive-cylinder with 4 trapezoidal fins, no boattail
    // Mach sweep: M ∈ {0.3, 0.5, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.5, 2.0, 3.0, 4.0, 5.0}
    // alpha = 0 degrees

    @Test
    public void testCAProfileUnchanged() {
        double[] CA_golden = loadGolden("CA_profile.csv");
        double[] CA_current = evaluateCAProfile(MACH_SWEEP, 0.0);
        for (int i = 0; i < MACH_SWEEP.length; i++) {
            assertEquals("CA regression at M=" + MACH_SWEEP[i],
                         CA_golden[i], CA_current[i], CA_golden[i] * 0.001);  // ±0.1%
        }
    }
}
```

### `CNAlphaRegressionTest.java`

```java
public class CNAlphaRegressionTest {
    // CNalpha computed by central difference: [CN(+1°) - CN(-1°)] / 2°
    // Mach sweep: same as CA regression

    @Test
    public void testCNAlphaProfileUnchanged() {
        double[] CNa_golden  = loadGolden("CNalpha_profile.csv");
        double[] CNa_current = evaluateCNalphaProfile(MACH_SWEEP);
        for (int i = 0; i < MACH_SWEEP.length; i++) {
            assertEquals("CNalpha regression at M=" + MACH_SWEEP[i],
                         CNa_golden[i], CNa_current[i], CNa_golden[i] * 0.001);
        }
    }
}
```

### `XcpRegressionTest.java`

```java
public class XcpRegressionTest {
    // xcp in calibers from nose tip, at alpha = 2 degrees

    @Test
    public void testXcpProfileUnchanged() {
        double[] xcp_golden  = loadGolden("xcp_profile.csv");
        double[] xcp_current = evaluateXcpProfile(MACH_SWEEP, 2.0);
        for (int i = 0; i < MACH_SWEEP.length; i++) {
            assertEquals("xcp regression at M=" + MACH_SWEEP[i],
                         xcp_golden[i], xcp_current[i], 0.005);  // ±0.005 calibers
        }
    }
}
```

### `BaseDragRegressionTest.java`

```java
public class BaseDragRegressionTest {
    // Cd_base at alpha=0, coast and powered (M=2.0 powered)

    @Test
    public void testBaseDragCoast() {
        double[] Cdb_golden  = loadGolden("base_drag_coast.csv");
        double[] Cdb_current = evaluateBaseDragProfile(MACH_SWEEP, false);
        for (int i = 0; i < MACH_SWEEP.length; i++) {
            assertEquals(Cdb_golden[i], Cdb_current[i], Cdb_golden[i] * 0.001);
        }
    }

    @Test
    public void testBaseDragPowered_M2() {
        double Cdb_coast_golden   = loadGoldenScalar("base_drag_powered_M2.csv", "coast");
        double Cdb_powered_golden = loadGoldenScalar("base_drag_powered_M2.csv", "powered");
        assertEquals(Cdb_coast_golden,   evaluateCdBase(2.0, false), 0.001);
        assertEquals(Cdb_powered_golden, evaluateCdBase(2.0, true),  0.001);
    }
}
```

### `BLThicknessRegressionTest.java`

```java
public class BLThicknessRegressionTest {
    // BL momentum thickness theta(x/L) along windward meridian at M=2, alpha=0

    @Test
    public void testBLThicknessProfile() {
        double[] theta_golden  = loadGolden("BL_thickness_M2.csv");
        double[] theta_current = romThetaProfile(2.0, 0.0);
        for (int i = 0; i < theta_golden.length; i++) {
            assertEquals(theta_golden[i], theta_current[i], theta_golden[i] * 0.001);
        }
    }
}
```

### `TransitionLocationRegressionTest.java`

```java
public class TransitionLocationRegressionTest {
    // Transition arc-length s_tr/L at M=0.5, M=2.0 with N_crit=9 and N_crit=6

    @Test
    public void testTransitionLocation() {
        for (double M : new double[]{0.5, 2.0}) {
            for (double Ncrit : new double[]{6.0, 9.0}) {
                double s_tr_golden  = loadTransitionGolden(M, Ncrit);
                double s_tr_current = computeTransitionLocation(M, Ncrit);
                assertEquals("Transition at M=" + M + " Ncrit=" + Ncrit,
                             s_tr_golden, s_tr_current, s_tr_golden * 0.01);  // ±1%
            }
        }
    }
}
```

---

## Layer 3 — Performance Tests

### `SingleEvaluationTimingTest.java`

```java
public class SingleEvaluationTimingTest {

    // Warm up JIT with 10 evaluations, then time 100 evaluations, report median
    @Test
    public void testSingleEvalUnder500ms() {
        PathlineRomCalculator rom = buildStandardROM();
        FlightConditions fc = buildConditions(2.0, 5.0);   // M=2, alpha=5 deg

        // JIT warmup
        for (int i = 0; i < 10; i++) rom.getAerodynamicForces(...);

        long total = 0;
        for (int i = 0; i < 100; i++) {
            long t0 = System.nanoTime();
            rom.getAerodynamicForces(...);
            total += System.nanoTime() - t0;
        }
        long median_ms = (total / 100) / 1_000_000;
        assertTrue("Single evaluation median > 500 ms: " + median_ms, median_ms <= 500);
    }
}
```

### `CacheBuildTimingTest.java`

```java
public class CacheBuildTimingTest {

    @Test
    public void testBalancedPresetUnder3Min() {
        PathlineRomCalculator rom = buildStandardROM();
        RomCache cache = new RomCache(RomCache.Preset.BALANCED);

        long t0 = System.currentTimeMillis();
        cache.buildCache(rom, rom.getGeometry(), rom.getConfig(), null);
        long elapsed_ms = System.currentTimeMillis() - t0;

        assertTrue("Cache build > 3 min: " + elapsed_ms + " ms", elapsed_ms <= 180_000);
    }

    @Test
    public void testFastPresetUnder30s() {
        PathlineRomCalculator rom = buildStandardROM();
        RomCache cache = new RomCache(RomCache.Preset.FAST);
        long t0 = System.currentTimeMillis();
        cache.buildCache(rom, rom.getGeometry(), rom.getConfig(), null);
        assertTrue(System.currentTimeMillis() - t0 <= 30_000);
    }
}
```

### `CacheQueryLatencyTest.java`

```java
public class CacheQueryLatencyTest {

    @Test
    public void test1000QueriesUnder100ms() {
        PathlineRomCalculator rom = buildStandardROM();
        RomCache cache = buildWarmCache(rom);

        long t0 = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            cache.query(0.5 + i * 0.004, Math.toRadians(i % 10), false);
        }
        long elapsed_us = (System.nanoTime() - t0) / 1000;

        assertTrue("1000 queries > 100 ms: " + elapsed_us + " µs", elapsed_us <= 100_000);
    }

    @Test
    public void testSingleQueryUnder200us() {
        RomCache cache = buildWarmCache(buildStandardROM());
        long t0 = System.nanoTime();
        cache.query(2.0, Math.toRadians(5.0), false);
        long elapsed_us = (System.nanoTime() - t0) / 1000;
        assertTrue("Single query > 200 µs: " + elapsed_us, elapsed_us <= 200);
    }
}
```

### `MemoryFootprintTest.java`

```java
public class MemoryFootprintTest {

    @Test
    public void testHeapUnder500MB() {
        // Measure heap after building a warm cache for the standard rocket
        System.gc();
        long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        PathlineRomCalculator rom = buildStandardROM();
        RomCache cache = buildWarmCache(rom);
        System.gc();
        long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long used_MB = (after - before) / (1024 * 1024);
        assertTrue("Heap usage > 500 MB: " + used_MB + " MB", used_MB <= 500);
    }
}
```

### `FullSimulationTimingTest.java`

```java
public class FullSimulationTimingTest {

    @Test
    public void test1000StepSimulationUnder5s() {
        // Run a complete OR simulation (1000 time steps) with ROM calculator
        // Uses SimulationRunner and a standard test rocket + motor
        SimulationConditions sc = buildStandardSimulationConditions();  // with ROM calculator
        long t0 = System.currentTimeMillis();
        new BasicEventSimulationEngine().simulate(sc);
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue("Full simulation > 5 s: " + elapsed + " ms", elapsed <= 5000);
    }
}
```

---

## Layer 4 — Integration Tests

### `MotorSwitchContinuityTest.java`

Verifies that the transition from coast to powered flight (motor ignition) produces
smooth CA (no step discontinuity > 1%).

```java
public class MotorSwitchContinuityTest {

    @Test
    public void testIgnitionContinuity() {
        double CA_coast   = evaluateCA_coast(2.0, 0.0);
        double CA_powered = evaluateCA_powered(2.0, 0.0);
        // Step is expected but must be < 20% (plume reduces base drag, not a discontinuity)
        double step = Math.abs(CA_powered - CA_coast) / CA_coast;
        assertTrue("CA discontinuity at ignition > 20%: " + step, step < 0.20);
    }
}
```

### `GeometryChangeInvalidationTest.java`

```java
public class GeometryChangeInvalidationTest {

    @Test
    public void testCacheInvalidatedOnResize() {
        Rocket rocket = buildStandardRocket();
        PathlineRomCalculator rom = new PathlineRomCalculator(rocket, new RomConfiguration());
        RomCache cache = buildWarmCache(rom);
        assertTrue(cache.isValid(rom.getGeometry(), null, rom.getConfig()));

        // Resize a body tube
        rocket.getBodyTube(0).setLength(0.5);   // change geometry

        assertFalse("Cache not invalidated after geometry change",
                    cache.isValid(rom.getGeometry(), null, rom.getConfig()));
    }
}
```

### `FallbackTransitionTest.java`

Verifies that the Barrowman fallback activates smoothly at UNRELIABLE confidence and
produces no force discontinuity at the transition boundary.

```java
public class FallbackTransitionTest {

    @Test
    public void testFallbackActivatesAtHighAlpha() {
        // alpha=20 degrees should trigger UNRELIABLE for typical rocket
        // Force should not be NaN or zero
        AerodynamicForces forces = evaluateForces(2.0, 20.0);
        assertNotNull(forces);
        assertFalse(Double.isNaN(forces.getCaxial()));
        assertTrue(forces.getCaxial() > 0);
    }

    @Test
    public void testFallbackTransitionSmooth() {
        // CA should vary smoothly between alpha = 14 degrees and alpha = 16 degrees
        // (spanning the fallback boundary at ~15 degrees for typical nose)
        double CA_14 = evaluateCA(2.0, 14.0);
        double CA_16 = evaluateCA(2.0, 16.0);
        double step  = Math.abs(CA_16 - CA_14) / CA_14;
        assertTrue("Discontinuity at fallback boundary: " + step, step < 0.10);
    }
}
```

### `CFDImportRoundTripTest.java`

```java
public class CFDImportRoundTripTest {

    @Test
    public void testCSVRoundTrip() throws IOException {
        CalibrationOverlay overlay = new CalibrationOverlay();
        overlay.loadFromCSV(testResourceFile("test_calibration.csv"));
        assertTrue(overlay.isLoaded());

        File tmp = File.createTempFile("rom_cal", ".csv");
        overlay.saveToCSV(tmp);

        CalibrationOverlay reloaded = new CalibrationOverlay();
        reloaded.loadFromCSV(tmp);
        assertTrue(reloaded.isLoaded());

        // Verify a specific residual matches after round-trip
        double r_original = overlay.applyResidualCp(1.0, 0.95, 0.0, 0.3);
        double r_reloaded = reloaded.applyResidualCp(1.0, 0.95, 0.0, 0.3);
        assertEquals("Residual not preserved after round-trip", r_original, r_reloaded, 1e-6);
    }
}
```

### `FullTrajectoryAccuracyTest.java`

```java
public class FullTrajectoryAccuracyTest {

    // Reference apogee data from SLU Student Launch altimeter datasets
    // and Cambridge BBH 2009 dispersion tables
    @Test
    public void testApogee_StandardHPR() {
        // Standard L-motor HPR rocket: flight apogee known from altimeter
        double apogee_flight = 1524.0;  // meters, from altimeter data
        double apogee_rom    = simulateApogee("HPR_Standard.ork");
        double error = Math.abs(apogee_rom - apogee_flight) / apogee_flight;
        assertTrue("Apogee error > 3%: " + (error*100) + "%", error <= 0.03);
    }
}
```

---

## Tuning Passes

Tuning is performed after all validation tests have been run, diagnostics reviewed, and
systematic error patterns identified. Each tuning pass adjusts one empirical constant or
one model parameter, re-runs the full validation suite, and documents the result.
**No constant is tuned without a corresponding experimental reference.**

### Tuning 1 — N_crit Calibration

**Objective:** Find the N_crit value(s) that best predict transition location for the ANF
and ARCAS geometries against AEDC-TR-76-58 and NASA TN D-6068 data.

**Procedure:**
1. Extract measured transition locations (x_tr/L) from the reference datasets.
2. Run the ROM over N_crit ∈ {3, 4, 5, 6, 7, 8, 9, 10, 11} for each geometry and Mach.
3. Compute RMS error in x_tr/L between ROM and measured, per N_crit.
4. Set default N_crit to the minimizing value for each flight environment class:
   - Quiet (clean free-flight): best N_crit from wind-tunnel polished data
   - Standard flight (ROM default): best N_crit from flight environment data
5. Document: ΔCA per ΔN_crit (sensitivity), expected uncertainty band from N_crit range 6–9.

**Acceptance:** After tuning, transition location RMS error ≤ 20% of body length for all
tested geometries and Mach numbers.

### Tuning 2 — Transonic Gaussian Hump Parameters

**Objective:** Calibrate the transonic drag-rise hump amplitude `A_transonic`, peak Mach
`M_peak`, and width `sigma_M` to minimize CA error through the transonic band.

**Reference data:**
- ANF: AEDC-TR-76-58 zero-lift drag through M = 0.80–1.20
- ARCAS: NASA TN D-6068 CA through transonic
- Kayser BRL MR-3353: base pressure transonic bucket

**Procedure:**
1. Compute ROM CA(M) at M ∈ {0.80, 0.85, 0.90, 0.95, 1.00, 1.05, 1.10, 1.15, 1.20} for
   ANF and ARCAS geometries.
2. Compute CA_ref from AEDC-TR-76-58.
3. Compute error = (CA_ROM - CA_ref) / CA_ref at each M.
4. Adjust A_transonic to minimize RMS error across all Mach points.
5. Adjust M_peak to align the drag rise peak with the measured peak Mach.
6. Adjust sigma_M to match the measured drag-rise width at half-maximum.

**Grid search:**
```
A_transonic ∈ [0.02, 0.10]   step 0.01
M_peak      ∈ [0.93, 1.05]   step 0.01
sigma_M     ∈ [0.06, 0.12]   step 0.005
```

**Acceptance:** After tuning, CA RMS error in the transonic band ≤ 10% for both ANF and ARCAS.

### Tuning 3 — Base Drag Empirical Constants

**Objective:** Calibrate the Hoerner/Stoney piecewise formula constants and the Gaussian
bucket peak value against Herrin-Dutton and Kayser experimental base pressure data.

**Parameters to tune:**
- Subsonic formula slope: `b` in `-Cp_b = a + b*M²` (currently b=0.13, a=0.12)
- Transonic peak: amplitude of Gaussian bucket (currently -0.34 at M=1.0)
- Supersonic formula: coefficients in `1/[γM²(0.88 + 0.165M²)]`

**Procedure:**
1. For each Mach anchor in {0.5, 0.7, 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.2, 1.5, 2.5, 4.0}:
   compare ROM Cp_b to Herrin-Dutton Table 1 and Kayser BRL MR-3353.
2. Adjust constants to minimize |Cp_b_ROM - Cp_b_ref| at each anchor.
3. Maintain monotonicity of the Cp_b(M) curve and continuity at breakpoints.

**Acceptance:** |Cp_b_ROM - Cp_b_ref| ≤ 0.06 at all 12 anchor Mach points.

### Tuning 4 — Korst Mixing Coefficient σ

**Objective:** Calibrate σ in the Korst-Chow-Nash analytical base pressure model against
AGARD AR-226 jet-off data at multiple Mach numbers.

**Current formula:** σ ≈ 12 + 2.758·M_exit  
**Tuning:** Adjust coefficients of linear fit to σ(M_exit).

**Procedure:**
1. Run Korst-Chow-Nash at M ∈ {1.5, 2.0, 2.5, 3.0, 4.0} for AGARD AR-226 geometry.
2. Find σ(M) that minimizes |Cp_b_Korst - Cp_b_AGARD| at each M.
3. Fit a linear σ(M_exit) = a + b·M_exit to the optimal σ values.

**Acceptance:** After tuning, Korst base Cp within ±0.05 absolute of AGARD AR-226 data.

### Tuning 5 — Boattail Critical Angle β_crit

**Objective:** Verify the empirical β_crit(M) formula against separation onset data in
AGARD AR-226 and Délery-Marvin AGARD AG-280.

**Current formula:** `β_crit = 11 + 5*(1 - |M-1|)` degrees  
**Tuning:** Adjust the constant 11° and slope 5° based on data.

**Procedure:**
1. From AGARD AR-226: identify the boattail angle at which separation is first observed
   at M ∈ {0.8, 1.0, 1.5, 2.0, 3.0}.
2. Fit the empirical β_crit(M) to these separation onset angles.

**Acceptance:** β_crit(M) within ±2° of measured onset for all reference Mach points.

### Tuning 6 — Pitts-Nielsen-Kaattari K-Factor Tables

**Objective:** Verify the NACA 1307 K_W(B) and K_B(W) implementation produces the
correct total CN for Cases 3 and 5 (ARCAS and ANF).

**Procedure:**
1. Compute CN_fins_ROM and compare to NASA TN D-6495 measured CN.
2. Check CN_body (body lift alone) against slender-body theory limit.
3. Check that at s/a → 1 (fin span << body radius): K_W(B) → 1.0, K_B(W) → 0.
4. If systematic error > 5% of CNalpha: review the NACA 1307 Figure 35/36 interpolation.

**Acceptance:** CNalpha from fin-body system within ±8% of NASA TN D-6495 for all tested
geometries and Mach numbers.

### Tuning 7 — Addy/Brazzel Plume Constants

**Objective:** Calibrate Addy recompression factor and Brazzel boattail modifier against
AGARD AR-226 jet-on data.

**Parameters:**
- Addy: `N_Addy = 0.21 + 0.18*(R_a/R_j)` — check coefficients 0.21 and 0.18
- Brazzel: `[1 + 0.6*(1 - D_b/D_max)]` and `[1 + 0.4*sin(delta_p)*(M-1)/M²]` — check
  coefficients 0.6 and 0.4

**Procedure:**
1. At M=2.5 with jet on (AGARD AR-226): compute CA_jet_on vs measured.
2. Compute plume expansion angle δ_p from motor conditions.
3. Adjust Brazzel coefficients to minimize |ΔCA_jet| error.

**Acceptance:** |CA_jet_on - CA_jet_on_ref| ≤ 15% for AGARD AR-226 jet-on condition.

---

## Tuning Documentation Protocol

For each tuning pass, record in a `TUNING_LOG.md` file:

```markdown
## Tuning Pass N — [Parameter Name]
**Date:** YYYY-MM-DD  
**Author:** [name]  
**Old value:** [old constant value(s)]  
**New value:** [new constant value(s)]  
**Reference data:** [paper/report citation and table/figure number]  
**Before CA RMS error:** X.X%  
**After CA RMS error:** X.X%  
**Regression test delta:** [list of regression tests that shifted, by how much]  
**Conclusion:** [accepted / reverted / needs further investigation]
```

---

## Final Acceptance Criteria

All rows must be met simultaneously. This table is the Phase 3 completion gate.

### Accuracy

| Test | Quantity | Condition | Target | Benchmark source |
|---|---|---|---|---|
| ANF subsonic | CA | M ≤ 0.75, α=0 | ±7% | AEDC-TR-76-58 |
| ANF transonic | CA | 0.80≤M≤1.20, α=0 | ±12% | AEDC-TR-76-58 |
| ANF supersonic | CA | M ≥ 1.5, α=0 | ±7% | AEDC-TR-76-58 |
| ANF fin-body | CNα | M=0.6, 2.0, 4.0 | ±8% | AEDC-TR-76-58 |
| Cone-cylinder | Cp(x) | M=2, 3, α=0 | ±0.05 abs | Reklis-Sturek |
| ARCAS | CA | all tested M | ±8% attached, ±12% transonic | NASA TN D-6068 |
| ARCAS | CNα | M=0.6, 1.5, 2.0, 3.0, 4.0 | ±8% | NASA TN D-6495 |
| ARCAS | xcp/L | M=0.6, 1.5, 2.0, 3.0 | ±0.025 | NASA TN D-6495 |
| AGARD AR-226 | Cp_base coast | M=0.6–4.0 | ±0.06 abs | Herrin-Dutton / Kayser |
| AGARD AR-226 | ΔCA jet on/off | M=2.5 | ±15% | AGARD AR-226 |
| RASAero comp | CA curve | 5 rockets | closer than RASAero at ≥60% M points | Rogers-Cooper 2011 |
| Trajectory | Apogee | 3 real flights | ±3% | Altimeter data |

### Performance

| Test | Metric | Target |
|---|---|---|
| Single evaluation (no cache) | Wall time, JIT-warm | ≤ 500 ms |
| Cache build, BALANCED preset | Wall time, 16 GB RAM | ≤ 3 minutes |
| Cache build, FAST preset | Wall time | ≤ 30 s |
| Cache query | Median latency | ≤ 200 µs |
| 1000 cache queries | Total time | ≤ 100 ms |
| Full trajectory simulation | Wall time, warm cache | ≤ 5 s per trajectory |
| Heap usage | Post-build, post-GC | ≤ 500 MB |

### Regression

| Test | Tolerance |
|---|---|
| CA profile | ±0.1% relative |
| CNα profile | ±0.1% relative |
| xcp profile | ±0.005 calibers |
| Base drag coast | ±0.1% relative |
| BL thickness | ±0.1% relative |
| Transition location | ±1% relative |

### Integration

| Test | Pass condition |
|---|---|
| No NPE on edge-case geometries | All 5 edge cases pass |
| Motor ignition CA step < 20% | Pass |
| Geometry change invalidates cache | Pass |
| Fallback at UNRELIABLE confidence | CA non-NaN, non-negative |
| Fallback boundary smooth (step < 10%) | Pass |
| CFD overlay CSV round-trip | Residual preserved to 1e-6 |
| Simulation dialog renders correctly | Pass (1920×1080 and 1366×768) |
| OR simulation does not crash on any benchmark geometry | Pass (all 5 geometries) |

### Tuning Sign-off

| Tuning pass | Completed | Accepted |
|---|---|---|
| T1 — N_crit | ☐ | ☐ |
| T2 — Transonic hump | ☐ | ☐ |
| T3 — Base drag constants | ☐ | ☐ |
| T4 — Korst mixing σ | ☐ | ☐ |
| T5 — Boattail β_crit | ☐ | ☐ |
| T6 — Pitts-Nielsen K factors | ☐ | ☐ |
| T7 — Addy/Brazzel plume | ☐ | ☐ |

All tuning passes must be checked before the Phase 3 completion gate is declared passed.
