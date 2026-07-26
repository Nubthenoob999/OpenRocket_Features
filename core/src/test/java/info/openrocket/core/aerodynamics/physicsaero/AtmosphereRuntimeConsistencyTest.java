package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;

class AtmosphereRuntimeConsistencyTest {
	private static final double PRESSURE_PA = 101325.0;
	private static final double RELATIVE_TOLERANCE = 1.0e-6;

	private final PerfectGasAir tableBuildAir = new PerfectGasAir();

	@ParameterizedTest
	@ValueSource(doubles = { 200.0, 220.0, 250.0, 273.15, 300.0, 350.0, 400.0 })
	void runtimeThermodynamicsAgreeWithTableBuildModel(double temperatureK) {
		AtmosphericConditions runtimeAtmosphere =
				new AtmosphericConditions(temperatureK, PRESSURE_PA, 0.0);

		double expectedSoundSpeed = tableBuildAir.speedOfSound(temperatureK);
		double expectedDynamicViscosity = tableBuildAir.viscosity(temperatureK);
		double expectedKinematicViscosity =
				expectedDynamicViscosity / runtimeAtmosphere.getDensity();

		assertRelativeEquals(expectedSoundSpeed, runtimeAtmosphere.getMachSpeed());
		assertRelativeEquals(expectedDynamicViscosity, runtimeAtmosphere.getDynamicViscosity());
		assertRelativeEquals(expectedKinematicViscosity, runtimeAtmosphere.getKinematicViscosity());
	}

	private static void assertRelativeEquals(double expected, double actual) {
		assertEquals(expected, actual, Math.abs(expected) * RELATIVE_TOLERANCE);
	}
}
