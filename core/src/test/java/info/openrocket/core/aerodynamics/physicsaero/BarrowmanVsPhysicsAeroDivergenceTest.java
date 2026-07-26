package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroTableService;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.startup.OpenRocketCore;

/**
 * Behavioral guard that prevents the physics table from silently collapsing
 * back to the legacy Barrowman curve in the supersonic regime.
 */
@Tag("benchmark")
class BarrowmanVsPhysicsAeroDivergenceTest {
	private static final Path ROCKET_FILE = Path.of("src", "test", "java", "info", "openrocket", "core",
			"Rockets_NewStuff", "NASA_26_Huntsville_DOL_ROM.ork");

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void physicsCurveIsSaneSubsonicallyAndMateriallyDifferentSupersonically(
			@TempDir Path output) throws Exception {
		var document = new GeneralRocketLoader(ROCKET_FILE.toFile()).load();
		var geometry = new GeometryExtractor().extract(
				document.getRocket(), 0, "ADIABATIC", "barrowman-divergence-v1");
		PerfectGasAir air = new PerfectGasAir();
		double pressurePa = 101253.0311;
		double temperatureK = 299.55;
		AtmosphereState atmosphere = new AtmosphereState(pressurePa, temperatureK,
				pressurePa / (air.gasConstant() * temperatureK), air.viscosity(temperatureK));
		double[] mach = {0, .25, .3, .35, .7, .8, .85, .9, .95, 1, 1.05, 1.1, 1.2, 1.3,
				1.5, 2, 2.5, 3, 4, 5, 6, 7};
		double[] alpha = degrees(-15, -10, -5, 0, 5, 10, 15);
		double[] beta = degrees(-5, 0, 5);
		PhysicsAeroTableService.Result result = new PhysicsAeroTableService().build(
				new PhysicsAeroTableService.Request(geometry, mach, alpha, beta, atmosphere, air,
						"barrowman-divergence-v1", output.resolve("divergence.aero"),
						output.resolve("divergence.json")),
				new AtomicBoolean(), ignored -> {});

		var configuration = document.getRocket().getSelectedConfiguration();
		var legacy = new BarrowmanCalculator();
		AtmosphericConditions runtimeAtmosphere =
				new AtmosphericConditions(temperatureK, pressurePa);
		double maximumSubsonicRelativeDifference = 0;
		double maximumSupersonicRelativeDifference = 0;
		for (double comparisonMach : new double[] {.3, .8, 1.0, 1.5, 2.5, 4.0}) {
			int index = indexOf(mach, comparisonMach);
			double physicsCd = result.table().cell(index, 3, 1).coefficients().ca();
			FlightConditions conditions = new FlightConditions(configuration);
			conditions.setAtmosphericConditions(runtimeAtmosphere);
			conditions.setMach(comparisonMach);
			conditions.setAOA(0);
			double barrowmanCd = legacy.getAerodynamicForces(
					configuration, conditions, new WarningSet()).getCD();
			assertTrue(Double.isFinite(physicsCd) && physicsCd > 0);
			assertTrue(Double.isFinite(barrowmanCd) && barrowmanCd > 0);
			double relativeDifference = Math.abs(physicsCd - barrowmanCd) / barrowmanCd;
			if (comparisonMach <= 0.8) {
				maximumSubsonicRelativeDifference = Math.max(
						maximumSubsonicRelativeDifference, relativeDifference);
			}
			if (comparisonMach >= 1.5) {
				maximumSupersonicRelativeDifference = Math.max(
						maximumSupersonicRelativeDifference, relativeDifference);
			}
		}

		assertTrue(maximumSubsonicRelativeDifference < 0.45,
				"subsonic sanity-floor difference=" + maximumSubsonicRelativeDifference);
		assertTrue(maximumSupersonicRelativeDifference > 0.10,
				"supersonic physics curve became indistinguishable from Barrowman: "
						+ maximumSupersonicRelativeDifference);

		double cd4 = result.table().cell(indexOf(mach, 4), 3, 1).coefficients().ca();
		double cd5 = result.table().cell(indexOf(mach, 5), 3, 1).coefficients().ca();
		double cd6 = result.table().cell(indexOf(mach, 6), 3, 1).coefficients().ca();
		assertTrue(cd4 > cd5 && cd5 > cd6,
				"high-Mach handoff must continue a smooth decreasing drag curve");
	}

	private static int indexOf(double[] values, double target) {
		for (int index = 0; index < values.length; index++) {
			if (values[index] == target) return index;
		}
		throw new IllegalArgumentException("missing axis value " + target);
	}

	private static double[] degrees(double... values) {
		return Arrays.stream(values).map(Math::toRadians).toArray();
	}
}
