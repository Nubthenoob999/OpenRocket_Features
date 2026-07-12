package info.openrocket.core.aerodynamics.physicsaero.body;

public interface BasePressureCorrelation {
	String methodId();
	String source();
	Validity validity(double mach, boolean powered);
	double basePressureCoefficient(double mach, double gamma);
	record Validity(boolean valid, String reason, double uncertainty) {}
}
