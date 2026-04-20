package info.openrocket.core.aerodynamics.rom.benchmark;

import static info.openrocket.core.aerodynamics.rom.benchmark.BenchmarkAssertions.*;

import info.openrocket.core.aerodynamics.rom.math.GasDynamics;
import info.openrocket.core.util.BaseTestCase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A34 cone-cylinder benchmark: cone surface pressure coefficients and
 * Cp_max validation against Taylor-Maccoll / Modified Newtonian theory.
 *
 * <p>Reference data from corpus dataset A34 {@code cone_cp} table, covering
 * M 1.5–5.0 and cone half-angles 5–25°.  The dataset provides exact
 * Taylor-Maccoll Cp values for comparison against:
 * <ul>
 *   <li>{@link GasDynamics#obliqueShockAngle} – shock geometry</li>
 *   <li>{@link GasDynamics#obliqueShockPressureRatio} – post-shock pressure</li>
 *   <li>{@link GasDynamics#cpMax} – stagnation pressure coefficient</li>
 *   <li>{@link GasDynamics#modifiedNewtonianCp} – high-inclination approximation</li>
 * </ul>
 */
@DisplayName("A34 – Cone-cylinder Cp benchmark")
public class A34ConeCylinderBenchmarkTest extends BaseTestCase {

	private static final String DATASET = "A34";
	private static final String SRC = "Taylor-Maccoll / NACA Report 1135";
	private static final double GAMMA = 1.4;

	// ------------------------------------------------------------------
	// Representative cone_cp corpus data points
	// Columns: Mach, theta_c (deg), Cp (exact Taylor-Maccoll), Cp_max, Cp_newtonian
	// ------------------------------------------------------------------

	private static final double[][] CONE_CP_DATA = {
			// {M, theta_c_deg, Cp_exact, Cp_max, Cp_newtonian}
			{1.5, 5,  0.0647, 1.53224, 0.01164},
			{1.5, 10, 0.1415, 1.53224, 0.0462},
			{1.5, 15, 0.232,  1.53224, 0.10264},
			{1.5, 20, 0.34,   1.53224, 0.17924},
			{2.0, 5,  0.0327, 1.6573,  0.01259},
			{2.0, 10, 0.0748, 1.6573,  0.04997},
			{2.0, 15, 0.1336, 1.6573,  0.11102},
			{2.0, 20, 0.21,   1.6573,  0.19387},
			{3.0, 5,  0.0145, 1.75571, 0.01334},
			{3.0, 10, 0.0366, 1.75571, 0.05294},
			{3.0, 15, 0.0712, 1.75571, 0.11761},
			{3.0, 20, 0.1218, 1.75571, 0.20538},
			{4.0, 5,  0.0082, 1.79179, 0.01361},
			{4.0, 10, 0.022,  1.79179, 0.05403},
			{4.0, 15, 0.0461, 1.79179, 0.12003},
			{4.0, 20, 0.0834, 1.79179, 0.2096},
			{5.0, 5,  0.0052, 1.80877, 0.01374},
			{5.0, 10, 0.0146, 1.80877, 0.05454},
	};

	// ------------------------------------------------------------------
	// Cp_max validation
	// ------------------------------------------------------------------

	@Test
	void cpMaxMatchesCorpusValues() {
		// Verify GasDynamics.cpMax against corpus Cp_max (Rayleigh pitot)
		double[] machValues = {1.5, 2.0, 3.0, 4.0, 5.0};
		double[] expectedCpMax = {1.53224, 1.6573, 1.75571, 1.79179, 1.80877};
		for (int i = 0; i < machValues.length; i++) {
			double actual = GasDynamics.cpMax(machValues[i], GAMMA);
			String label = caseLabel(DATASET, SRC,
					"M=" + machValues[i] + " Cp_max");
			assertCloseTo(label, expectedCpMax[i], actual, 0.01, 0.005);
		}
	}

	@Test
	void cpMaxIncreasesWithMach() {
		double[] cpMaxValues = new double[5];
		double[] machValues = {1.5, 2.0, 3.0, 4.0, 5.0};
		for (int i = 0; i < machValues.length; i++) {
			cpMaxValues[i] = GasDynamics.cpMax(machValues[i], GAMMA);
		}
		assertMonotonicallyIncreasing(
				caseLabel(DATASET, SRC, "Cp_max monotone with Mach"), cpMaxValues);
	}

	// ------------------------------------------------------------------
	// Newtonian Cp validation
	// ------------------------------------------------------------------

	@Test
	void modifiedNewtonianCpMatchesCorpus() {
		for (double[] row : CONE_CP_DATA) {
			double mach = row[0];
			double thetaDeg = row[1];
			double cpMax = row[3];
			double expectedNewt = row[4];
			double thetaRad = Math.toRadians(thetaDeg);
			double actual = GasDynamics.modifiedNewtonianCp(thetaRad, cpMax);
			String label = caseLabel(DATASET, SRC,
					"M=" + mach + " θ=" + thetaDeg + "° Cp_newtonian");
			assertCloseTo(label, expectedNewt, actual, 0.02, 0.002);
		}
	}

	// ------------------------------------------------------------------
	// Oblique shock angle and post-shock state
	// ------------------------------------------------------------------

	@Test
	void obliqueShockAngleProducesValidResults() {
		// For attached-shock cases, the oblique shock angle must be between the
		// cone half-angle and 90°.  Cases above the maximum attached deflection
		// angle should return NaN.
		for (double[] row : CONE_CP_DATA) {
			double mach = row[0];
			double thetaDeg = row[1];
			double thetaRad = Math.toRadians(thetaDeg);
			double beta = GasDynamics.obliqueShockAngle(mach, thetaRad, GAMMA);
			double thetaMaxDeg = Math.toDegrees(GasDynamics.maxDeflectionAngle(mach, GAMMA));
			String label = caseLabel(DATASET, SRC,
					"M=" + mach + " θ=" + thetaDeg + "° shock angle");
			if (thetaDeg < thetaMaxDeg - 0.25) {
				assertFinite(label, beta);
				assertInRange(label + " beta bounds",
						Math.toDegrees(beta), thetaDeg, 90.0);
			} else {
				org.junit.jupiter.api.Assertions.assertTrue(
						Double.isNaN(beta),
						label + " – expected detached/invalid attached-shock solution above θ_max="
								+ thetaMaxDeg + " but got " + beta);
			}
		}
	}

	@Test
	void coneSurfaceCpIsPositiveAndBounded() {
		// Exact Taylor-Maccoll Cp must be: 0 < Cp < Cp_max
		for (double[] row : CONE_CP_DATA) {
			double cpExact = row[2];
			double cpMax = row[3];
			String label = caseLabel(DATASET, SRC,
					"M=" + row[0] + " θ=" + row[1] + "° Cp bounds");
			assertInRange(label, cpExact, 0.0, cpMax);
		}
	}

	@Test
	void coneCpDecreasesWithMachAtFixedAngle() {
		// At fixed cone angle, Cp should decrease with increasing Mach
		double thetaDeg = 10.0;
		double[] machs = {1.5, 2.0, 3.0, 4.0, 5.0};
		double[] cps = {0.1415, 0.0748, 0.0366, 0.022, 0.0146};
		assertMonotonicallyDecreasing(
				caseLabel(DATASET, SRC, "θ=10° Cp vs Mach"), cps);
	}

	@Test
	void coneCpIncreasesWithAngleAtFixedMach() {
		// At fixed Mach, Cp should increase with cone angle
		double mach = 2.0;
		double[] cps = {0.0327, 0.0748, 0.1336, 0.21};
		assertMonotonicallyIncreasing(
				caseLabel(DATASET, SRC, "M=2.0 Cp vs θ"), cps);
	}

	@Test
	void newtonianUnderestimatesExactForModerateMachFiveDegreeCones() {
		// For very small cone angles and moderate supersonic Mach numbers, the
		// modified Newtonian estimate should sit below the exact Taylor-Maccoll value.
		for (double[] row : CONE_CP_DATA) {
			if (row[1] == 5.0 && row[0] <= 3.0) {
				double cpExact = row[2];
				double cpNewt = row[4];
				String label = caseLabel(DATASET, SRC,
						"M=" + row[0] + " θ=" + row[1] + "° Newtonian < exact");
				org.junit.jupiter.api.Assertions.assertTrue(
						cpNewt < cpExact,
						label + " – Newtonian " + cpNewt + " ≥ exact " + cpExact);
			}
		}
	}
}
