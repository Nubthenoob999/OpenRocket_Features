package info.openrocket.core.aerodynamics.physicsaero.interpolation;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.DiagnosticFlag;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicDerivatives;
import info.openrocket.core.aerodynamics.physicsaero.math.MultilinearInterpolator;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.RuntimeCorrectionData;

public final class EventAwareInterpolator {
	private static final double MINIMUM_CORRECTION_CORNER_WEIGHT = 1.0e-2;

	public QueryResult interpolate(List<TableCell> corners, double tx, double ta, double tb) {
		if (corners.size() != 8) throw new IllegalArgumentException("eight corners required");
		Set<Set<String>> stateEvents = new HashSet<>();
		for (TableCell c : corners) stateEvents.add(TableQueryEngine.eventFlags(c.validityFlags()));
		if (stateEvents.size() > 1) throw new IllegalStateException("interpolation across a topology/separation/transition event is disabled");
		double[] result = new double[6];
		for (int coefficient = 0; coefficient < 6; coefficient++) { double[] values = new double[8]; for (int i = 0; i < 8; i++) values[i] = corners.get(i).coefficients().toArray()[coefficient]; result[coefficient] = MultilinearInterpolator.trilinear(values, tx, ta, tb); }
		Map<String, AerodynamicCoefficients> components = interpolateMaps(corners, true, tx, ta, tb);
		Map<String, AerodynamicCoefficients> owners = interpolateMaps(corners, false, tx, ta, tb);
		double[] derivatives = new double[3];
		for (int derivative = 0; derivative < derivatives.length; derivative++) {
			double[] values = new double[8];
			for (int i = 0; i < values.length; i++) values[i] = corners.get(i).derivatives().toArray()[derivative];
			derivatives[derivative] = MultilinearInterpolator.trilinear(values, tx, ta, tb);
		}
		Set<DiagnosticFlag> flags = new HashSet<>(); Set<String> validity = new TreeSet<>(); Set<String> methods = new TreeSet<>();
		Set<info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason> reasons = new HashSet<>();
		for (TableCell c : corners) { flags.addAll(c.diagnostics().flags()); validity.addAll(c.validityFlags()); methods.addAll(c.methodIds()); reasons.addAll(c.diagnostics().reasonCodes()); }
		flags.add(DiagnosticFlag.INTERPOLATED);
		double lowestConfidence = corners.stream()
				.flatMapToDouble(corner -> Arrays.stream(corner.confidence())).min().orElse(Double.NaN);
		double[] weights = {(1-tx)*(1-ta)*(1-tb), (1-tx)*(1-ta)*tb,
				(1-tx)*ta*(1-tb), (1-tx)*ta*tb, tx*(1-ta)*(1-tb), tx*(1-ta)*tb,
				tx*ta*(1-tb), tx*ta*tb};
		RuntimeCorrectionData correction = interpolateCorrection(
				corners.stream().map(TableCell::runtimeCorrection).toList(), weights);
		return new QueryResult(AerodynamicCoefficients.fromArray(result), flags, List.copyOf(validity),
				List.copyOf(methods), true, components, owners, AerodynamicDerivatives.fromArray(derivatives),
				reasons, lowestConfidence, correction);
	}

	static RuntimeCorrectionData interpolateCorrection(
			List<RuntimeCorrectionData> corrections, double[] weights) {
		if (corrections.size() != weights.length || corrections.isEmpty()) {
			throw new IllegalArgumentException("correction weights must match corners");
		}
		double totalWeight = 0;
		double exactReferenceReynolds = 0;
		for (int index = 0; index < weights.length; index++) {
			totalWeight += weights[index];
			exactReferenceReynolds +=
					weights[index] * corrections.get(index).referenceReynolds();
		}
		if (!(totalWeight > 0) || !Double.isFinite(totalWeight)
				|| !Double.isFinite(exactReferenceReynolds)) {
			throw new IllegalArgumentException(
					"correction weights must define a finite positive interpolation");
		}
		exactReferenceReynolds /= totalWeight;
		/*
		 * Coefficients retain every multilinear corner. Correction metadata is
		 * categorical: a topology-sensitive remote corner used to invalidate a
		 * near-exact central query even when its coefficient weight was numerical
		 * noise. Ignore only corners below 1% influence for the categorical domain
		 * and sensitivity interpolation, which bounds the omitted sensitivity
		 * contribution by the same fraction.
		 *
		 * Reference Reynolds is a continuous coordinate, however, and must retain
		 * every corner. In particular, near Mach zero each positive-Mach corner can
		 * have less than 1% individual authority while their sum carries the entire
		 * nonzero reference. Dropping all but the first such corner understates
		 * Re_ref and creates a false runtime Reynolds-ratio failure.
		 */
		int first = -1;
		for (int index = 0; index < weights.length; index++) {
			if (weights[index] >= MINIMUM_CORRECTION_CORNER_WEIGHT) {
				first = index;
				break;
			}
		}
		if (first < 0) {
			throw new IllegalArgumentException("interpolation weights have no positive authority");
		}
		RuntimeCorrectionData correction = corrections.get(first);
		double accumulated = weights[first];
		for (int index = first + 1; index < corrections.size(); index++) {
			double next = weights[index];
			boolean establishesPositiveReference = correction.referenceReynolds() == 0
					&& next > 0 && corrections.get(index).referenceReynolds() > 0;
			if (next < MINIMUM_CORRECTION_CORNER_WEIGHT
					&& !establishesPositiveReference) continue;
			correction = TableQueryEngine.blendCorrection(correction, corrections.get(index),
					next / (accumulated + next));
			accumulated += next;
		}
		return new RuntimeCorrectionData(exactReferenceReynolds,
				correction.minimumRatio(), correction.maximumRatio(),
				correction.dCoefficientDLogRe(),
				correction.dCoefficientDLogReSquared(),
				correction.dCoefficientDLogReCubed(),
				correction.topologySensitive(), correction.methodId());
	}

	private static Map<String, AerodynamicCoefficients> interpolateMaps(List<TableCell> corners,
			boolean components, double tx, double ta, double tb) {
		Set<String> keys = new TreeSet<>();
		for (TableCell corner : corners) {
			Set<String> current = components ? corner.componentTotals().keySet() : corner.ownerTotals().keySet();
			keys.addAll(current);
		}
		Map<String, AerodynamicCoefficients> result = new TreeMap<>();
		AerodynamicCoefficients zero = new AerodynamicCoefficients(0, 0, 0, 0, 0, 0);
		for (String key : keys) {
			double[] coefficients = new double[6];
			for (int coefficient = 0; coefficient < coefficients.length; coefficient++) {
				double[] values = new double[8];
				for (int index = 0; index < values.length; index++) {
					Map<String, AerodynamicCoefficients> totals = components
							? corners.get(index).componentTotals() : corners.get(index).ownerTotals();
					AerodynamicCoefficients value = totals.getOrDefault(key, zero);
					values[index] = value.toArray()[coefficient];
				}
				coefficients[coefficient] = MultilinearInterpolator.trilinear(values, tx, ta, tb);
			}
			result.put(key, AerodynamicCoefficients.fromArray(coefficients));
		}
		return result;
	}
}
