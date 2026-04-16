package info.openrocket.core.simulation;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;

final class RomStageAerodynamicsHelper {

	private RomStageAerodynamicsHelper() {
	}

	static StageContext capture(SimulationStatus status) {
		AerodynamicCalculator calculator = status.getSimulationConditions().getAerodynamicCalculator();
		if (calculator instanceof RomAerodynamicCalculator romCalculator && romCalculator.hasSurface()) {
			return new StageContext(romCalculator, status.getSimulationTime(), romCalculator.getPlumeState());
		}
		return StageContext.inactive();
	}

	static double plumeStateAtStage(double stepStartPlumeState, boolean burning, double stageOffsetSeconds) {
		return RomAerodynamicCalculator.evolvePlumeState(stepStartPlumeState, burning, stageOffsetSeconds);
	}

	static boolean isBurning(SimulationStatus status) {
		for (MotorClusterState motorState : status.getActiveMotors()) {
			if (motorState.getThrust(status.getSimulationTime()) > 0.0) {
				return true;
			}
		}
		return false;
	}

	static final class StageContext {
		private static final StageContext INACTIVE = new StageContext(null, Double.NaN, 0.0);

		private final RomAerodynamicCalculator calculator;
		private final double stepStartTimeSeconds;
		private final double stepStartPlumeState;

		private StageContext(RomAerodynamicCalculator calculator, double stepStartTimeSeconds, double stepStartPlumeState) {
			this.calculator = calculator;
			this.stepStartTimeSeconds = stepStartTimeSeconds;
			this.stepStartPlumeState = stepStartPlumeState;
		}

		static StageContext inactive() {
			return INACTIVE;
		}

		void apply(SimulationStatus status) {
			if (calculator == null) {
				return;
			}
			double stageOffsetSeconds = Math.max(0.0, status.getSimulationTime() - stepStartTimeSeconds);
			boolean burning = RomStageAerodynamicsHelper.isBurning(status);
			calculator.setCurrentSimulationTime(status.getSimulationTime());
			calculator.setPlumeState(RomStageAerodynamicsHelper.plumeStateAtStage(
					stepStartPlumeState,
					burning,
					stageOffsetSeconds));
		}

		void commit(SimulationStatus status) {
			apply(status);
		}
	}
}
