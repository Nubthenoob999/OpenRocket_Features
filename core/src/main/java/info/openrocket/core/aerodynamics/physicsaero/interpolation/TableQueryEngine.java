package info.openrocket.core.aerodynamics.physicsaero.interpolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import info.openrocket.core.aerodynamics.physicsaero.table.*;
import info.openrocket.core.aerodynamics.physicsaero.math.MonotonePchip1D;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicDerivatives;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.DiagnosticFlag;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;

public final class TableQueryEngine {
	public QueryResult query(AerodynamicTable table, double mach, double alphaRad, double betaRad) {
		return query(table, mach, alphaRad, betaRad, 0);
	}
	public QueryResult query(AerodynamicTable table, double mach, double alphaRad, double betaRad,
			double poweredFraction) {
		Bracket powered = bracket(table.axes().poweredFraction(), poweredFraction);
		QueryResult lower = queryAtPoweredIndex(table, mach, alphaRad, betaRad, powered.lo);
		if (powered.exact) return lower;
		QueryResult upper = queryAtPoweredIndex(table, mach, alphaRad, betaRad, powered.hi);
		if (!eventFlags(lower.validityFlags()).equals(eventFlags(upper.validityFlags()))) {
			throw new IllegalStateException("interpolation across a powered-state event is disabled");
		}
		return linear(lower, upper, powered.t);
	}
	private QueryResult queryAtPoweredIndex(AerodynamicTable table, double mach, double alphaRad,
			double betaRad, int poweredIndex) {
		Bracket m = bracket(table.axes().mach(), mach), a = bracket(table.axes().alphaRad(), alphaRad), b = bracket(table.axes().betaRad(), betaRad);
		if (m.exact && a.exact && b.exact) {
			TableCell cell = table.cell(m.lo, a.lo, b.lo, poweredIndex);
			return new QueryResult(cell.coefficients(), cell.diagnostics().flags(), cell.validityFlags(),
					cell.methodIds(), false, cell.componentTotals(), cell.ownerTotals(), cell.derivatives(),
					cell.diagnostics().reasonCodes(), lowest(cell), cell.runtimeCorrection());
		}
		if (a.exact && b.exact && !m.exact && table.axes().mach().length >= 3) {
			TableCell lower = table.cell(m.lo, a.lo, b.lo, poweredIndex), upper = table.cell(m.hi, a.lo, b.lo, poweredIndex);
			if (!eventFlags(lower.validityFlags()).equals(eventFlags(upper.validityFlags())))
				throw new IllegalStateException("interpolation across a topology/separation/transition event is disabled");
			double[] coefficients = new double[6];
			for (int c = 0; c < 6; c++) {
				double[] values = new double[table.axes().mach().length];
				for (int i = 0; i < values.length; i++) values[i] = table.cell(i, a.lo, b.lo, poweredIndex).coefficients().toArray()[c];
				coefficients[c] = MonotonePchip1D.interpolate(table.axes().mach(), values, mach);
			}
			Set<DiagnosticFlag> flags = new HashSet<>(lower.diagnostics().flags()); flags.addAll(upper.diagnostics().flags()); flags.add(DiagnosticFlag.INTERPOLATED);
			Set<String> validity = new TreeSet<>(lower.validityFlags()); validity.addAll(upper.validityFlags());
			double fraction = (mach - table.axes().mach()[m.lo]) / (table.axes().mach()[m.hi] - table.axes().mach()[m.lo]);
			QueryResult grouped = linear(fromCell(lower), fromCell(upper), fraction);
			return new QueryResult(AerodynamicCoefficients.fromArray(coefficients), flags, List.copyOf(validity),
					grouped.methodIds(), true, grouped.componentTotals(), grouped.ownerTotals(), grouped.derivatives(),
					grouped.reasonCodes(), grouped.lowestConfidence(), grouped.runtimeCorrection());
		}
		List<TableCell> corners = new ArrayList<>(8);
		for (int im : new int[] {m.lo, m.hi}) for (int ia : new int[] {a.lo, a.hi}) for (int ib : new int[] {b.lo, b.hi}) corners.add(table.cell(im, ia, ib, poweredIndex));
		return new EventAwareInterpolator().interpolate(corners, m.t, a.t, b.t);
	}
	private static QueryResult fromCell(TableCell cell) {
		return new QueryResult(cell.coefficients(), cell.diagnostics().flags(), cell.validityFlags(),
				cell.methodIds(), false, cell.componentTotals(), cell.ownerTotals(), cell.derivatives(),
				cell.diagnostics().reasonCodes(), lowest(cell), cell.runtimeCorrection());
	}
	private static QueryResult linear(QueryResult lower, QueryResult upper, double fraction) {
		Set<DiagnosticFlag> flags = new HashSet<>(lower.diagnosticFlags()); flags.addAll(upper.diagnosticFlags()); flags.add(DiagnosticFlag.INTERPOLATED);
		Set<String> validity = new TreeSet<>(lower.validityFlags()); validity.addAll(upper.validityFlags());
		Set<FailureReason> reasons = new HashSet<>(lower.reasonCodes()); reasons.addAll(upper.reasonCodes());
		Set<String> methods = new TreeSet<>(lower.methodIds()); methods.addAll(upper.methodIds());
		return new QueryResult(blend(lower.coefficients(), upper.coefficients(), fraction), flags,
				List.copyOf(validity), List.copyOf(methods), true,
				blendMaps(lower.componentTotals(), upper.componentTotals(), fraction),
				blendMaps(lower.ownerTotals(), upper.ownerTotals(), fraction),
				blend(lower.derivatives(), upper.derivatives(), fraction), reasons,
				minimumConfidence(lower.lowestConfidence(), upper.lowestConfidence()),
				blendCorrection(lower.runtimeCorrection(), upper.runtimeCorrection(), fraction));
	}
	static RuntimeCorrectionData blendCorrection(RuntimeCorrectionData lower,
			RuntimeCorrectionData upper, double fraction) {
		double reference = lower.referenceReynolds()
				+ fraction * (upper.referenceReynolds() - lower.referenceReynolds());
		if (lower.requiresRebuild() || upper.requiresRebuild()
				|| !lower.methodId().equals(upper.methodId())) {
			return RuntimeCorrectionData.rebuildRequired(Math.max(0, reference));
		}
		/*
		 * The validity bounds are local ratios Re/Re_ref, not absolute Reynolds
		 * intervals.  Re_ref changes along the Mach axis (approximately in
		 * proportion to Mach at a fixed reference atmosphere), so intersecting
		 * absolute endpoint intervals falsely invalidates almost every interval
		 * whose endpoint Mach values differ by more than the narrow sensitivity
		 * stencil.  Preserve the common dimensionless validity domain while
		 * interpolating the reference surface itself.
		 *
		 * Re_ref=0 is also a valid coefficient limit at Mach zero.  In
		 * particular, blending two angular Mach-zero corners must retain that
		 * valid limit so a subsequent small positive-Mach corner can establish
		 * the physical Reynolds reference.  Marking the intermediate zero as a
		 * rebuild event poisons otherwise valid low-speed interpolation.
		 */
		double minimumRatio = Math.max(lower.minimumRatio(), upper.minimumRatio());
		double maximumRatio = Math.min(lower.maximumRatio(), upper.maximumRatio());
		if (maximumRatio < minimumRatio) return RuntimeCorrectionData.rebuildRequired(reference);
		double[] a = lower.dCoefficientDLogRe(), b = upper.dCoefficientDLogRe();
		double[] a2 = lower.dCoefficientDLogReSquared(), b2 = upper.dCoefficientDLogReSquared();
		double[] a3 = lower.dCoefficientDLogReCubed(), b3 = upper.dCoefficientDLogReCubed();
		double[] sensitivity = new double[6];
		double[] curvature = new double[6];
		double[] cubic = new double[6];
		for (int index = 0; index < sensitivity.length; index++) {
			sensitivity[index] = a[index] + fraction * (b[index] - a[index]);
			curvature[index] = a2[index] + fraction * (b2[index] - a2[index]);
			cubic[index] = a3[index] + fraction * (b3[index] - a3[index]);
		}
		return new RuntimeCorrectionData(reference, minimumRatio,
				maximumRatio, sensitivity, curvature, cubic, false, lower.methodId());
	}
	private static double lowest(TableCell cell) {
		return java.util.Arrays.stream(cell.confidence()).min().orElse(Double.NaN);
	}
	private static double minimumConfidence(double first, double second) {
		if (Double.isNaN(first)) return second;
		if (Double.isNaN(second)) return first;
		return Math.min(first, second);
	}
	static Set<String> eventFlags(List<String> flags) {
		Set<String> events = new TreeSet<>();
		for (String flag : flags) {
			String normalized = flag.toUpperCase(java.util.Locale.ROOT);
			if (normalized.contains("TOPOLOGY") || normalized.contains("SEPARAT")
					|| normalized.contains("TRANSITION") || normalized.contains("POWERED_STATE")) {
				events.add(flag);
			}
		}
		return events;
	}
	private static AerodynamicCoefficients blend(AerodynamicCoefficients lower, AerodynamicCoefficients upper, double fraction) {
		double[] a = lower.toArray(), b = upper.toArray(), result = new double[6];
		for (int i = 0; i < result.length; i++) result[i] = a[i] + fraction * (b[i] - a[i]);
		return AerodynamicCoefficients.fromArray(result);
	}
	private static AerodynamicDerivatives blend(AerodynamicDerivatives lower, AerodynamicDerivatives upper, double fraction) {
		double[] a = lower.toArray(), b = upper.toArray(), result = new double[3];
		for (int i = 0; i < result.length; i++) result[i] = a[i] + fraction * (b[i] - a[i]);
		return AerodynamicDerivatives.fromArray(result);
	}
	private static Map<String, AerodynamicCoefficients> blendMaps(Map<String, AerodynamicCoefficients> lower,
			Map<String, AerodynamicCoefficients> upper, double fraction) {
		Set<String> keys = new TreeSet<>(lower.keySet());
		keys.addAll(upper.keySet());
		Map<String, AerodynamicCoefficients> result = new TreeMap<>();
		AerodynamicCoefficients zero = new AerodynamicCoefficients(0, 0, 0, 0, 0, 0);
		for (String key : keys) {
			result.put(key, blend(lower.getOrDefault(key, zero), upper.getOrDefault(key, zero), fraction));
		}
		return result;
	}
	private static Bracket bracket(double[] axis, double query) {
		if (!Double.isFinite(query)) throw new IllegalArgumentException("OUT_OF_DOMAIN");
		double scale = Math.max(1, Math.max(Math.abs(axis[0]), Math.abs(axis[axis.length - 1])));
		double tolerance = 1.0e-12 * scale;
		if (query < axis[0] - tolerance || query > axis[axis.length - 1] + tolerance)
			throw new IllegalArgumentException("OUT_OF_DOMAIN");
		if (axis.length == 1) {
			if (Math.abs(query - axis[0]) > tolerance) throw new IllegalArgumentException("OUT_OF_DOMAIN");
			return new Bracket(0, 0, 0, true);
		}
		for (int i = 0; i < axis.length; i++) {
			if (Math.abs(query - axis[i]) <= tolerance) return new Bracket(i, i, 0, true);
		}
		for (int i = 0; i < axis.length - 1; i++) if (query < axis[i + 1]) return new Bracket(i, i + 1, (query - axis[i]) / (axis[i + 1] - axis[i]), false);
		throw new IllegalArgumentException("OUT_OF_DOMAIN");
	}
	private record Bracket(int lo, int hi, double t, boolean exact) {}
}
