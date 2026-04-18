package info.openrocket.core.aerodynamics.rom.flow;

public class FlowState {
	private final double mach;
	private final double reynoldsNumber;
	private final double dynamicPressure;
	private final double staticPressure;
	private final double staticTemperature;
	private final double density;
	private final double speedOfSound;
	private final double viscosity;
	private final double velocity;
	private final double angleOfAttackRad;
	private final double angleOfAttackDeg;
	private final double sideslipRad;
	private final double sideslipDeg;
	private final boolean powered;
	private final double plumeState;
	private final double referenceLength;

	public FlowState(double mach, double reynoldsNumber, double dynamicPressure, double staticPressure,
			double staticTemperature, double density, double speedOfSound, double viscosity, double velocity,
			double angleOfAttackRad, double angleOfAttackDeg, double sideslipRad, double sideslipDeg,
			boolean powered, double plumeState, double referenceLength) {
		this.mach = mach;
		this.reynoldsNumber = reynoldsNumber;
		this.dynamicPressure = dynamicPressure;
		this.staticPressure = staticPressure;
		this.staticTemperature = staticTemperature;
		this.density = density;
		this.speedOfSound = speedOfSound;
		this.viscosity = viscosity;
		this.velocity = velocity;
		this.angleOfAttackRad = angleOfAttackRad;
		this.angleOfAttackDeg = angleOfAttackDeg;
		this.sideslipRad = sideslipRad;
		this.sideslipDeg = sideslipDeg;
		this.powered = powered;
		this.plumeState = plumeState;
		this.referenceLength = referenceLength;
	}

	public double getMach() {
		return mach;
	}

	public double getReynoldsNumber() {
		return reynoldsNumber;
	}

	public double getDynamicPressure() {
		return dynamicPressure;
	}

	public double getStaticPressure() {
		return staticPressure;
	}

	public double getStaticTemperature() {
		return staticTemperature;
	}

	public double getDensity() {
		return density;
	}

	public double getSpeedOfSound() {
		return speedOfSound;
	}

	public double getViscosity() {
		return viscosity;
	}

	public double getVelocity() {
		return velocity;
	}

	public double getAngleOfAttackRad() {
		return angleOfAttackRad;
	}

	public double getAngleOfAttackDeg() {
		return angleOfAttackDeg;
	}

	public double getSideslipRad() {
		return sideslipRad;
	}

	public double getSideslipDeg() {
		return sideslipDeg;
	}

	public boolean isPowered() {
		return powered;
	}

	public double getPlumeState() {
		return plumeState;
	}

	public double getReferenceLength() {
		return referenceLength;
	}
}
