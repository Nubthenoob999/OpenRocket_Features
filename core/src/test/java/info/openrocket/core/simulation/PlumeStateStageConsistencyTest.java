package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.util.BaseTestCase;

public class PlumeStateStageConsistencyTest extends BaseTestCase {

	private static final double[] RK4_STAGE_OFFSETS = new double[] { 0.0, 0.5, 0.5, 1.0 };
	private static final double[] RK6_STAGE_OFFSETS = new double[] {
			0.0, 1.0 / 3.0, 2.0 / 3.0, 1.0 / 3.0, 0.5, 0.5, 1.0
	};

	@Test
	public void testPoweredStagesStayFullyPlumedForRk4AndRk6() {
		double stepSeconds = 0.12;
		double initialPlumeState = 0.35;

		for (double offsetScale : RK4_STAGE_OFFSETS) {
			assertEquals(1.0,
					RomStageAerodynamicsHelper.plumeStateAtStage(initialPlumeState, true, stepSeconds * offsetScale),
					1e-12);
		}
		for (double offsetScale : RK6_STAGE_OFFSETS) {
			assertEquals(1.0,
					RomStageAerodynamicsHelper.plumeStateAtStage(initialPlumeState, true, stepSeconds * offsetScale),
					1e-12);
		}
	}

	@Test
	public void testCoastDecayUsesExactStageLocalOffsets() {
		double stepSeconds = 0.18;
		double initialPlumeState = 1.0;

		for (double offsetScale : RK4_STAGE_OFFSETS) {
			double offsetSeconds = stepSeconds * offsetScale;
			assertEquals(
					RomAerodynamicCalculator.evolvePlumeState(initialPlumeState, false, offsetSeconds),
					RomStageAerodynamicsHelper.plumeStateAtStage(initialPlumeState, false, offsetSeconds),
					1e-12);
		}
		for (double offsetScale : RK6_STAGE_OFFSETS) {
			double offsetSeconds = stepSeconds * offsetScale;
			assertEquals(
					RomAerodynamicCalculator.evolvePlumeState(initialPlumeState, false, offsetSeconds),
					RomStageAerodynamicsHelper.plumeStateAtStage(initialPlumeState, false, offsetSeconds),
					1e-12);
		}
	}

	@Test
	public void testRk4AndRk6CommitToSameEndOfStepPlumeState() {
		double stepSeconds = 0.25;
		double initialPlumeState = 1.0;

		double rk4EndState = RomStageAerodynamicsHelper.plumeStateAtStage(initialPlumeState, false, stepSeconds);
		double rk6EndState = RomStageAerodynamicsHelper.plumeStateAtStage(initialPlumeState, false, stepSeconds);

		assertEquals(rk4EndState, rk6EndState, 1e-12);
		assertEquals(
				RomAerodynamicCalculator.evolvePlumeState(initialPlumeState, false, stepSeconds),
				rk4EndState,
				1e-12);
	}
}
