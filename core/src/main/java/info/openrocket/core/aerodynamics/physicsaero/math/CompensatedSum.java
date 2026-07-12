package info.openrocket.core.aerodynamics.physicsaero.math;

/** Neumaier compensated scalar sum. */
public final class CompensatedSum {
	private double sum;
	private double correction;
	public void add(double value) {
		double t = sum + value;
		if (Math.abs(sum) >= Math.abs(value)) correction += (sum - t) + value;
		else correction += (value - t) + sum;
		sum = t;
	}
	public double value() { return sum + correction; }
}
