package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.*;

/** Deterministic holdout-driven refinement for the Phase-II zero-incidence Mach slice. */
public final class AdaptiveMachRefiner {
	@FunctionalInterface public interface Evaluator { Sample evaluate(double mach); }
	public record Sample(double mach, double axialCoefficient, String methodRegion) {}
	public double[] refine(double[] initialMach, Evaluator evaluator, double relativeHoldoutTolerance,
			int maximumPasses) {
		TreeMap<Double, Sample> samples = new TreeMap<>(); for (double mach : initialMach) samples.put(mach, evaluator.evaluate(mach));
		for (int pass = 0; pass < maximumPasses; pass++) {
			List<Double> insert = new ArrayList<>(); List<Sample> ordered = new ArrayList<>(samples.values());
			for (int i = 1; i < ordered.size(); i++) {
				Sample left = ordered.get(i - 1), right = ordered.get(i); double midpoint = 0.5 * (left.mach() + right.mach());
				Sample direct = evaluator.evaluate(midpoint); double interpolated = 0.5 * (left.axialCoefficient() + right.axialCoefficient());
				double scale = Math.max(1e-8, Math.abs(direct.axialCoefficient()));
				boolean methodEvent = !left.methodRegion().equals(right.methodRegion()) || !direct.methodRegion().equals(left.methodRegion());
				if (methodEvent || Math.abs(direct.axialCoefficient() - interpolated) / scale > relativeHoldoutTolerance) {
					insert.add(midpoint); samples.put(midpoint, direct);
				}
			}
			if (insert.isEmpty()) break;
		}
		return samples.keySet().stream().mapToDouble(Double::doubleValue).toArray();
	}
}
