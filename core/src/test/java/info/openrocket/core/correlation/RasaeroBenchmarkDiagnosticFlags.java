package info.openrocket.core.correlation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import info.openrocket.core.correlation.RasaeroBenchmarkData.BenchmarkCase;
import info.openrocket.core.correlation.RasaeroBenchmarkSolverAdapter.Prediction;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;

/**
 * Post-prediction explanations for benchmark errors.  These flags never
 * participate in producing a coefficient or center-of-pressure prediction.
 */
final class RasaeroBenchmarkDiagnosticFlags {
	static final double ONE_PERCENT = 1.0;

	private RasaeroBenchmarkDiagnosticFlags() {
	}

	static double relativeErrorPercent(BenchmarkCase benchmark, Prediction prediction) {
		if (prediction.actual().isEmpty()) {
			return Double.NaN;
		}
		double expected = benchmark.expectedValue();
		double residual = prediction.actual().getAsDouble() - expected;
		if (expected == 0) {
			return residual == 0 ? 0 : Double.POSITIVE_INFINITY;
		}
		return 100 * Math.abs(residual) / Math.abs(expected);
	}

	static List<String> explain(BenchmarkCase benchmark, Prediction prediction) {
		Set<String> flags = new LinkedHashSet<>();
		if (prediction.unsupported()) {
			flags.add("NO_NUMERIC_PREDICTION");
			addUnsupportedCause(flags, prediction);
			return List.copyOf(flags);
		}

		double errorPercent = relativeErrorPercent(benchmark, prediction);
		if (!(errorPercent > ONE_PERCENT)) {
			return List.of("ERROR_WITHIN_ONE_PERCENT");
		}

		flags.add("ERROR_OUTSIDE_ONE_PERCENT");
		if (prediction.reasonCodes().contains(FailureReason.BODY_INCIDENCE_UNOWNED)) {
			flags.add("LIKELY_CAUSE_BODY_INCIDENCE_PHYSICS_UNOWNED");
		}
		if (prediction.reasonCodes().contains(FailureReason.GEOMETRY_FEATURE_UNREPRESENTED)) {
			flags.add("LIKELY_CAUSE_ARCAS_GEOMETRY_FEATURES_UNREPRESENTED");
		}
		if (prediction.reasonCodes().contains(FailureReason.GENERIC_HIGH_MACH_CLOSURE)) {
			flags.add("LIKELY_CAUSE_GENERIC_HIGH_MACH_CLOSURE");
		}
		if (prediction.reasonCodes().contains(FailureReason.TRANSONIC_EMPIRICAL_CLOSURE)) {
			flags.add("LIKELY_CAUSE_TRANSONIC_EMPIRICAL_CLOSURE");
		}
		if (prediction.reasonCodes().contains(FailureReason.VISCOUS_COUPLING_FALLBACK)) {
			flags.add("LIKELY_CAUSE_VISCOUS_COUPLING_FALLBACK");
		}
		if (prediction.reasonCodes().contains(FailureReason.ENGINEERING_SKIN_FRICTION)) {
			flags.add("LIKELY_CAUSE_ENGINEERING_SKIN_FRICTION");
		}
		if (prediction.reasonCodes().contains(FailureReason.PNK_INTERFERENCE_DISABLED)) {
			flags.add("LIKELY_CAUSE_PNK_INTERFERENCE_DISABLED");
		}
		if (prediction.reasonCodes().contains(FailureReason.PRESCRIBED_TRANSITION)
				|| prediction.reasonCodes().contains(FailureReason.PRESCRIBED_WALL_TEMPERATURE)) {
			flags.add("LIKELY_CAUSE_PRESCRIBED_TRANSITION_OR_WALL_TEMPERATURE");
		}
		if (prediction.reasonCodes().contains(FailureReason.FIN_PROFILE_FALLBACK)) {
			flags.add("LIKELY_CAUSE_FIN_PROFILE_DRAG_FALLBACK");
		}
		if (flags.size() == 1) {
			flags.add("LIKELY_CAUSE_UNATTRIBUTED_MODEL_FORM_OR_FIXTURE_DELTA");
		}
		return List.copyOf(flags);
	}

	private static void addUnsupportedCause(Set<String> flags, Prediction prediction) {
		if (prediction.reasonCodes().contains(FailureReason.MACH_DOMAIN_EXCEEDED)) {
			flags.add("CAUSE_PRODUCTION_MACH_DOMAIN_EXCEEDED");
		} else if (prediction.reasonCodes().contains(FailureReason.POWERED_FLOW_UNIMPLEMENTED)) {
			flags.add("CAUSE_POWERED_BASE_MODEL_NOT_IMPLEMENTED");
		} else if (prediction.reasonCodes().contains(FailureReason.NUMERICAL_FAILURE)) {
			flags.add("CAUSE_NONFINITE_PRODUCTION_OUTPUT");
		} else {
			flags.add("CAUSE_UNCLASSIFIED_UNSUPPORTED_RESULT");
		}
	}
}
