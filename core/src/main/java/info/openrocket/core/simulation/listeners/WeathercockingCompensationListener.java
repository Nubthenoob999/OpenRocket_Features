package info.openrocket.core.simulation.listeners;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.masscalc.MassCalculation;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.masscalc.RigidBody;
import info.openrocket.core.simulation.SimulationStatus;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;

/**
 * Applies a bounded drag correction intended to account for weather-cocking effects
 * when a rocket is both highly statically stable and light enough for wind sensitivity
 * to materially influence trajectory energy.
 */
public class WeathercockingCompensationListener extends AbstractSimulationListener {
	private static final double MIN_EFFECTIVE_CD = 1.0e-6;
	private static final double MAX_TILT_RAD = Math.toRadians(35.0);
	private static final double MAX_AOA_RAD = Math.toRadians(15.0);
	private static final double MAX_GATE_AMPLIFICATION = 2.0;

	private final double stabilityWeightRatioReference;
	private final double cdGain;
	private final double maxCdDelta;

	private FlightConditions lastFlightConditions;

	public WeathercockingCompensationListener(double stabilityMinCalibers,
													  double stabilityMassRatioMin,
													  double cdGain,
													  double maxCdDelta) {
		this.stabilityWeightRatioReference = normalizePositive(stabilityMassRatioMin, 1.0);
		this.cdGain = Double.isFinite(cdGain) ? cdGain : 0.0;
		this.maxCdDelta = normalizePositive(maxCdDelta, 0.35);
	}

	@Override
	public FlightConditions postFlightConditions(SimulationStatus status, FlightConditions flightConditions)
			throws SimulationException {
		this.lastFlightConditions = flightConditions;
		return flightConditions;
	}

	@Override
	public AerodynamicForces postAerodynamicCalculation(SimulationStatus status, AerodynamicForces forces)
			throws SimulationException {
		if (forces == null || lastFlightConditions == null || Math.abs(cdGain) < MathUtil.EPSILON) {
			return forces;
		}

		CoordinateIF cp = forces.getCP();
		if (cp == null || cp.getWeight() <= MathUtil.EPSILON) {
			return forces;
		}

		double refLength = lastFlightConditions.getRefLength();
		if (!Double.isFinite(refLength) || refLength <= MathUtil.EPSILON) {
			return forces;
		}

		RigidBody massData = MassCalculator.calculate(MassCalculation.Type.LAUNCH, status);
		if (massData == null || massData.getCM() == null) {
			return forces;
		}

		double massKg = massData.getMass();
		if (!Double.isFinite(massKg) || massKg <= MathUtil.EPSILON) {
			return forces;
		}

		double stabilityCalibers = (cp.getX() - massData.getCM().getX()) / refLength;
		if (!Double.isFinite(stabilityCalibers) || stabilityCalibers <= MathUtil.EPSILON) {
			return forces;
		}

		double stabilityWeightRatio = stabilityCalibers / massKg;
		if (!Double.isFinite(stabilityWeightRatio) || stabilityWeightRatio <= MathUtil.EPSILON) {
			return forces;
		}

		double correction = computeCdCorrection(status.getRocketVelocity(), stabilityWeightRatio);
		if (!Double.isFinite(correction) || Math.abs(correction) < 1.0e-9) {
			return forces;
		}

		double baseCd = forces.getCD();
		if (!Double.isFinite(baseCd) || baseCd <= MIN_EFFECTIVE_CD) {
			return forces;
		}

		double updatedCd = Math.max(MIN_EFFECTIVE_CD, baseCd * (1.0 + correction));
		if (!Double.isFinite(updatedCd)) {
			return forces;
		}

		forces.setCD(updatedCd);
		double baseAxialCd = forces.getCDaxial();
		if (Double.isFinite(baseAxialCd) && baseAxialCd > MIN_EFFECTIVE_CD) {
			forces.setCDaxial(Math.max(MIN_EFFECTIVE_CD, baseAxialCd * (updatedCd / baseCd)));
		}

		return forces;
	}

	private double computeCdCorrection(CoordinateIF velocity,
								 double stabilityWeightRatio) {
		double tiltFactor = tiltFactor(velocity);
		double aoaFactor = normalizeFraction(Math.abs(lastFlightConditions.getAOA()), MAX_AOA_RAD);
		double windFactor = normalizeFraction(Math.abs(Math.sin(lastFlightConditions.getTheta())), 1.0);
		double gateFactor = gateAmplification(stabilityWeightRatio);

		double correction = cdGain * tiltFactor * (0.45 + 0.55 * aoaFactor) * (0.50 + 0.50 * windFactor)
				* (1.0 + gateFactor);
		return MathUtil.clamp(correction, -maxCdDelta, maxCdDelta);
	}

	private double gateAmplification(double stabilityWeightRatio) {
		double ratioNorm = (stabilityWeightRatio - stabilityWeightRatioReference)
				/ Math.max(stabilityWeightRatioReference, MathUtil.EPSILON);
		return MathUtil.clamp(ratioNorm, 0.0, MAX_GATE_AMPLIFICATION);
	}

	private static double tiltFactor(CoordinateIF velocity) {
		if (velocity == null) {
			return 0.0;
		}

		double speed = velocity.length();
		if (!Double.isFinite(speed) || speed <= MathUtil.EPSILON) {
			return 0.0;
		}

		double cosine = MathUtil.clamp(velocity.getZ() / speed, -1.0, 1.0);
		double tilt = Math.acos(cosine);
		return normalizeFraction(tilt, MAX_TILT_RAD);
	}

	private static double normalizeFraction(double value, double scale) {
		if (!Double.isFinite(value) || !Double.isFinite(scale) || scale <= MathUtil.EPSILON) {
			return 0.0;
		}
		return MathUtil.clamp(value / scale, 0.0, 1.0);
	}

	private static double normalizePositive(double value, double fallback) {
		if (!Double.isFinite(value) || value <= 0.0) {
			return fallback;
		}
		return value;
	}
}
