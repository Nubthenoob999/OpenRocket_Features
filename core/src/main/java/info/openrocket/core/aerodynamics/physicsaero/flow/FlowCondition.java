package info.openrocket.core.aerodynamics.physicsaero.flow;

import java.util.Objects;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;

/** Immutable SI/radian freestream request. */
public record FlowCondition(double mach, double alphaRad, double betaRad, CoordinateIF velocityBody,
		CoordinateIF velocityUnitBody, AtmosphereState atmosphere, ThermodynamicModel thermodynamics,
		boolean powered, String configurationId) {
	private static final double DEFAULT_TOLERANCE = 1e-8;
	public FlowCondition {
		Objects.requireNonNull(velocityBody, "velocityBody");
		Objects.requireNonNull(velocityUnitBody, "velocityUnitBody");
		Objects.requireNonNull(atmosphere, "atmosphere");
		Objects.requireNonNull(thermodynamics, "thermodynamics");
		Objects.requireNonNull(configurationId, "configurationId");
		if (mach < 0 || !Double.isFinite(mach + alphaRad + betaRad)) throw new IllegalArgumentException("invalid flow scalars");
		double speed = velocityBody.length();
		if (Math.abs(velocityUnitBody.length() - 1.0) > DEFAULT_TOLERANCE) throw new IllegalArgumentException("velocityUnitBody is not unit length");
		double expectedMach = speed / thermodynamics.speedOfSound(atmosphere.temperatureK());
		if (Math.abs(expectedMach - mach) > DEFAULT_TOLERANCE * Math.max(1, mach)) throw new IllegalArgumentException("Mach disagrees with velocity");
		double derivedAlpha = Math.atan2(velocityBody.getZ(), velocityBody.getX());
		double derivedBeta = Math.atan2(velocityBody.getY(), Math.hypot(velocityBody.getX(), velocityBody.getZ()));
		if (Math.abs(derivedAlpha - alphaRad) > DEFAULT_TOLERANCE || Math.abs(derivedBeta - betaRad) > DEFAULT_TOLERANCE) {
			throw new IllegalArgumentException("angles disagree with body velocity");
		}
	}

	public static FlowCondition fromAngles(double mach, double alphaRad, double betaRad,
			AtmosphereState atmosphere, ThermodynamicModel model, boolean powered, String configurationId) {
		double speed = mach * model.speedOfSound(atmosphere.temperatureK());
		double cb = Math.cos(betaRad);
		Coordinate unit = new Coordinate(cb * Math.cos(alphaRad), Math.sin(betaRad), cb * Math.sin(alphaRad));
		return new FlowCondition(mach, alphaRad, betaRad, unit.multiply(speed), unit, atmosphere, model, powered, configurationId);
	}

	public double dynamicPressurePa() { return 0.5 * atmosphere.densityKgM3() * velocityBody.length() * velocityBody.length(); }
}
