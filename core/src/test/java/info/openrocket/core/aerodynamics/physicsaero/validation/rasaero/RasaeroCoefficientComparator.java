package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

import java.util.List;

public final class RasaeroCoefficientComparator {
	public ComparisonResult compare(List<Double> actual, List<Double> reference, double floor) {
		if (actual.isEmpty() || actual.size() != reference.size() || floor <= 0) throw new IllegalArgumentException("invalid comparison arrays");
		double sumAbs = 0, sumSq = 0, sum = 0, maxAbs = -1; int worst = -1; double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < actual.size(); i++) {
			double error = actual.get(i) - reference.get(i), absolute = Math.abs(error); sumAbs += absolute; sumSq += error * error; sum += error;
			if (absolute > maxAbs) { maxAbs = absolute; worst = i; } min = Math.min(min, reference.get(i)); max = Math.max(max, reference.get(i));
		}
		double rmse = Math.sqrt(sumSq / actual.size());
		return new ComparisonResult(sumAbs / actual.size(), rmse, sum / actual.size(), rmse / Math.max(max - min, floor), maxAbs, worst,
				maxAbs / Math.max(Math.abs(reference.get(worst)), floor));
	}
	public record Tolerance(double absolute, double scaled) {
		public Tolerance { if (absolute < 0 || scaled < 0) throw new IllegalArgumentException(); }
	}
	public record ComparisonResult(double mae, double rmse, double bias, double nrmse, double maximumAbsoluteError,
			int worstIndex, double worstScaledError) {
		public boolean passes(Tolerance tolerance) { return maximumAbsoluteError <= tolerance.absolute() || worstScaledError <= tolerance.scaled(); }
		public String toDiagnosticMessage() { return "maxAbs=" + maximumAbsoluteError + ", scaled=" + worstScaledError + ", index=" + worstIndex + ", bias=" + bias; }
	}
}
