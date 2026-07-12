package info.openrocket.core.structures.loads;

public final class FlightLoadCase {
	private final double time;
	private final double velocity;
	private final double mach;
	private final double density;
	private final double dynamicPressure;
	private final double mass;
	private final double cg;
	private final double thrust;
	private final double drag;
	private final double axialAcceleration;
	private final double angleOfAttack;
	private final double airPressure;
	private final double speedOfSound;

	public FlightLoadCase(double time, double velocity, double mach, double density, double dynamicPressure,
			double mass, double cg, double thrust, double drag, double axialAcceleration, double angleOfAttack,
			double airPressure, double speedOfSound) {
		this.time = time;
		this.velocity = velocity;
		this.mach = mach;
		this.density = density;
		this.dynamicPressure = dynamicPressure;
		this.mass = mass;
		this.cg = cg;
		this.thrust = thrust;
		this.drag = drag;
		this.axialAcceleration = axialAcceleration;
		this.angleOfAttack = angleOfAttack;
		this.airPressure = airPressure;
		this.speedOfSound = speedOfSound;
	}

	public double getTime() {
		return time;
	}

	public double getVelocity() {
		return velocity;
	}

	public double getMach() {
		return mach;
	}

	public double getDensity() {
		return density;
	}

	public double getDynamicPressure() {
		return dynamicPressure;
	}

	public double getMass() {
		return mass;
	}

	public double getCg() {
		return cg;
	}

	public double getThrust() {
		return thrust;
	}

	public double getDrag() {
		return drag;
	}

	public double getAxialAcceleration() {
		return axialAcceleration;
	}

	public double getAngleOfAttack() {
		return angleOfAttack;
	}

	public double getAirPressure() {
		return airPressure;
	}

	public double getSpeedOfSound() {
		return speedOfSound;
	}
}
