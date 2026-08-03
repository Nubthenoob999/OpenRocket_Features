package info.openrocket.core.tuning;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.config.SamplingConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCalculator;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.table.CertificationState;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.util.TestRockets;

class PhysicsAeroTuningSupportTest {
	@Test
	void disabledSimulationDoesNotBuildOrMutateTableIdentity() {
		Simulation simulation = new Simulation(TestRockets.makeEstesAlphaIII());

		assertDoesNotThrow(() ->
				PhysicsAeroTuningSupport.prepareTableIfEnabled(simulation));
		assertEquals(PhysicsAeroMode.OFF,
				simulation.getOptions().getPhysicsAeroMode());
		assertEquals("",
				simulation.getOptions().getPhysicsAeroSettings().getTableContentHash());
	}

	@Test
	void enabledMultiStageSimulationFailsBeforeAttemptingATableBuild() {
		Simulation simulation =
				new Simulation(TestRockets.makeMultiStageEventTestRocket());
		simulation.getOptions().setPhysicsAeroMode(PhysicsAeroMode.STRICT);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> PhysicsAeroTuningSupport.prepareTableIfEnabled(simulation));
		assertEquals("PHYSICS_AERO_TUNING_REQUIRES_SINGLE_STAGE_CONFIGURATION",
				failure.getMessage());
	}

	@Test
	void poweredTuningAxisCarriesNozzleAreaAndChangesTheTableIdentity() {
		Simulation simulation = new Simulation(TestRockets.makeEstesAlphaIII());
		simulation.getOptions().setNozzleExitDiameterForStage(0, 0.024);

		PoweredFlowState[] states = PhysicsAeroTuningSupport.poweredStatesForTuning(
				simulation.getOptions());
		assertEquals(2, states.length);
		assertEquals(0, states[0].poweredFraction());
		assertEquals(1, states[1].poweredFraction());
		assertEquals(PoweredFlowState.Resolution.NOZZLE_GEOMETRY_ONLY,
				states[1].resolution());
		assertEquals(Math.PI * 0.024 * 0.024 / 4, states[1].nozzleExitAreaM2(), 1e-15);

		String withFirstNozzle = fingerprint(states);
		simulation.getOptions().setNozzleExitDiameterForStage(0, 0.030);
		String withSecondNozzle = fingerprint(
				PhysicsAeroTuningSupport.poweredStatesForTuning(simulation.getOptions()));
		assertNotEquals(withFirstNozzle, withSecondNozzle);
	}

	@Test
	void missingNozzleBuildsAnExplicitPoweredUnmodeledCellInsteadOfPoweredOod() {
		Simulation simulation = new Simulation(TestRockets.makeEstesAlphaIII());
		PoweredFlowState[] states = PhysicsAeroTuningSupport.poweredStatesForTuning(
				simulation.getOptions());
		assertEquals(PoweredFlowState.Resolution.NONE, states[1].resolution());

		PerfectGasAir air = new PerfectGasAir();
		AtmosphereState atmosphere = new AtmosphereState(101325, 288.15,
				101325 / (air.gasConstant() * 288.15), air.viscosity(288.15));
		var geometry = new GeometryExtractor().extractWithComponentRoughness(
				simulation.getActiveConfiguration(), "ADIABATIC", "unmodeled-powered");
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA,
				geometry.geometryHash(), "unmodeled-powered", "test", "test",
				TableMetadata.REQUIRED_UNITS, TableMetadata.REQUIRED_AXIS_CONVENTION,
				java.util.Map.of(), java.util.Map.of(),
				CertificationState.EXPERIMENTAL_FLIGHT_PENDING);
		var table = new FullRegimeTableBuilder().build(geometry, new double[] {0.5},
				new double[] {0}, new double[] {0}, states, atmosphere, air, metadata);

		assertTrue(table.cell(0, 0, 0, 1).validityFlags().contains("POWERED_INCREMENT_UNMODELED"));
		assertDoesNotThrow(() -> new PhysicsAeroTableCalculator(table,
				geometry.geometryHash(), "unmodeled-powered").query(0.5, 0, 0, 1));
	}

	private static String fingerprint(PoweredFlowState[] states) {
		return info.openrocket.core.aerodynamics.physicsaero.config.PhysicsAeroSettingsFingerprint.hash(
				PhysicsAeroTuningSupport.tuningFingerprintInput(
						SamplingConfiguration.flightDomainDefaults(), false, states));
	}
}
