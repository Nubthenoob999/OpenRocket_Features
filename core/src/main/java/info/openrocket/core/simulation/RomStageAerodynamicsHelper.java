package info.openrocket.core.simulation;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator.BoundaryEvent;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator.FlightRegime;

final class RomStageAerodynamicsHelper {

	private RomStageAerodynamicsHelper() {
	}

	static StageContext capture(SimulationStatus status) {
		AerodynamicCalculator calculator = status.getSimulationConditions().getAerodynamicCalculator();
		if (calculator instanceof RomAerodynamicCalculator romCalculator && romCalculator.hasSurface()) {
			return new StageContext(
					romCalculator,
					status.getSimulationTime(),
					romCalculator.getPlumeState(),
					detectFlightRegime(status),
					detectNextBoundary(status.getEventQueue(), status.getSimulationTime()));
		}
		return StageContext.inactive();
	}

	static FlightRegime detectFlightRegime(boolean liftoff, boolean apogeeReached, boolean landed,
			boolean recoveryDeployed, boolean burning) {
		if (landed) {
			return FlightRegime.LANDED;
		}
		if (!liftoff) {
			return FlightRegime.PRE_LAUNCH;
		}
		if (recoveryDeployed) {
			return FlightRegime.RECOVERY;
		}
		if (burning) {
			return FlightRegime.POWERED_ASCENT;
		}
		if (apogeeReached) {
			return FlightRegime.POST_APOGEE;
		}
		return FlightRegime.COAST_ASCENT;
	}

	static FlightRegime detectFlightRegime(SimulationStatus status) {
		return detectFlightRegime(
				status.isLiftoff(),
				status.isApogeeReached(),
				status.isLanded(),
				!status.getDeployedRecoveryDevices().isEmpty(),
				isBurning(status));
	}

	static BoundaryMarker detectNextBoundary(Iterable<FlightEvent> events, double currentTimeSeconds) {
		BoundaryMarker best = BoundaryMarker.none();
		for (FlightEvent event : events) {
			BoundaryEvent boundaryEvent = boundaryEventFor(event);
			if (boundaryEvent == BoundaryEvent.NONE) {
				continue;
			}
			double eventTime = event.getTime();
			if (!Double.isFinite(eventTime) || eventTime + 1e-9 < currentTimeSeconds) {
				continue;
			}
			if (best.isNone() || eventTime < best.boundaryTimeSeconds()) {
				best = new BoundaryMarker(boundaryEvent, eventTime);
			}
		}
		return best;
	}

	static BoundaryEvent boundaryEventFor(FlightEvent event) {
		return switch (event.getType()) {
			case BURNOUT -> BoundaryEvent.BURNOUT;
			case APOGEE -> BoundaryEvent.APOGEE;
			case RECOVERY_DEVICE_DEPLOYMENT -> BoundaryEvent.RECOVERY_DEVICE_DEPLOYMENT;
			default -> BoundaryEvent.NONE;
		};
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
		private static final StageContext INACTIVE = new StageContext(null, Double.NaN, 0.0,
				FlightRegime.PRE_LAUNCH, BoundaryMarker.none());

		private final RomAerodynamicCalculator calculator;
		private final double stepStartTimeSeconds;
		private final double stepStartPlumeState;
		private final FlightRegime flightRegime;
		private final BoundaryMarker boundaryMarker;

		private StageContext(RomAerodynamicCalculator calculator, double stepStartTimeSeconds, double stepStartPlumeState,
				FlightRegime flightRegime, BoundaryMarker boundaryMarker) {
			this.calculator = calculator;
			this.stepStartTimeSeconds = stepStartTimeSeconds;
			this.stepStartPlumeState = stepStartPlumeState;
			this.flightRegime = flightRegime;
			this.boundaryMarker = boundaryMarker;
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
			FlightRegime liveFlightRegime = RomStageAerodynamicsHelper.detectFlightRegime(status);
			BoundaryMarker liveBoundaryMarker = RomStageAerodynamicsHelper.detectNextBoundary(
					status.getEventQueue(),
					status.getSimulationTime());
			calculator.setCurrentSimulationTime(status.getSimulationTime());
			calculator.setFlightRegime(liveFlightRegime);
			calculator.setBoundaryEvent(liveBoundaryMarker.boundaryEvent(), liveBoundaryMarker.boundaryTimeSeconds());
			calculator.setPlumeState(RomStageAerodynamicsHelper.plumeStateAtStage(
					stepStartPlumeState,
					burning,
					stageOffsetSeconds));
		}

		void commit(SimulationStatus status) {
			apply(status);
		}
	}

	static final class BoundaryMarker {
		private static final BoundaryMarker NONE = new BoundaryMarker(BoundaryEvent.NONE, Double.NaN);

		private final BoundaryEvent boundaryEvent;
		private final double boundaryTimeSeconds;

		private BoundaryMarker(BoundaryEvent boundaryEvent, double boundaryTimeSeconds) {
			this.boundaryEvent = boundaryEvent;
			this.boundaryTimeSeconds = boundaryTimeSeconds;
		}

		static BoundaryMarker none() {
			return NONE;
		}

		boolean isNone() {
			return boundaryEvent == BoundaryEvent.NONE;
		}

		BoundaryEvent boundaryEvent() {
			return boundaryEvent;
		}

		double boundaryTimeSeconds() {
			return boundaryTimeSeconds;
		}
	}
}
