package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.PhysicsAeroAerodynamicCalculator;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroSettings;
import info.openrocket.core.startup.OpenRocketCore;

class PhysicsAeroSimulationContractTest {
	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void oneAdapterMapsAoaAndThetaToSignedBodyAlphaBeta() {
		FlightConditions conditions = new FlightConditions(null);
		conditions.setAOA(Math.toRadians(5));
		conditions.setTheta(Math.toRadians(30));
		double[] angles = PhysicsAeroAerodynamicCalculator.bodyAngles(conditions);
		assertEquals(Math.toRadians(5) * Math.cos(Math.toRadians(30)), angles[0], 1e-15);
		assertEquals(Math.toRadians(5) * Math.sin(Math.toRadians(30)), angles[1], 1e-15);

		conditions.setTheta(-Math.toRadians(90));
		angles = PhysicsAeroAerodynamicCalculator.bodyAngles(conditions);
		assertEquals(0, angles[0], 1e-15);
		assertEquals(-Math.toRadians(5), angles[1], 1e-15);
	}

	@Test
	void portableSettingsCopyByValueAndDoNotSubstituteForAResolvedTable() {
		SimulationOptions options = new SimulationOptions();
		PhysicsAeroSettings physics = options.getPhysicsAeroSettings();
		physics.setMode(PhysicsAeroMode.DIAGNOSTIC_HYBRID);
		physics.setGeometryHash("geometry");
		physics.setSettingsHash("settings");
		physics.setTableContentHash("content");
		options.setPhysicsAeroSettings(physics);
		assertEquals(physics, options.clone().getPhysicsAeroSettings());

		options.setPhysicsAeroMode(PhysicsAeroMode.STRICT);
		assertThrows(IllegalStateException.class, options::toSimulationConditions,
				"persisted identities do not substitute for a missing local cached table");
	}

	@Test
	void enablingExperimentalAerodynamicsDefaultsToStrictUnlessHybridIsExplicit() {
		SimulationOptions options = new SimulationOptions();
		options.setPhysicsAeroMode(PhysicsAeroMode.STRICT);
		assertEquals(PhysicsAeroMode.STRICT, options.getPhysicsAeroMode());
		options.setPhysicsAeroMode(PhysicsAeroMode.DIAGNOSTIC_HYBRID);
		assertEquals(PhysicsAeroMode.DIAGNOSTIC_HYBRID, options.getPhysicsAeroMode());
	}

	@Test
	void experimentalModeIsOffByDefault() {
		SimulationOptions options = new SimulationOptions();
		assertEquals(PhysicsAeroMode.OFF, options.getPhysicsAeroMode());
		assertTrue(options.getPhysicsAeroSettings().getTableContentHash().isEmpty());
	}
}
