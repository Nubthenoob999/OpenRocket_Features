package info.openrocket.core.aerodynamics.physicsaero.interpolation;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.table.*;
import info.openrocket.core.aerodynamics.physicsaero.math.MonotonePchip1D;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.DiagnosticFlag;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;

public final class TableQueryEngine {
	public QueryResult query(AerodynamicTable table, double mach, double alphaRad, double betaRad) {
		Bracket m = bracket(table.axes().mach(), mach), a = bracket(table.axes().alphaRad(), alphaRad), b = bracket(table.axes().betaRad(), betaRad);
		if (m.exact && a.exact && b.exact) {
			TableCell cell = table.cell(m.lo, a.lo, b.lo);
			return new QueryResult(cell.coefficients(), cell.diagnostics().flags(), cell.validityFlags(), cell.methodIds(), false);
		}
		if (a.exact && b.exact && !m.exact && table.axes().mach().length >= 3) {
			TableCell lower = table.cell(m.lo, a.lo, b.lo), upper = table.cell(m.hi, a.lo, b.lo);
			if (!lower.methodIds().equals(upper.methodIds())) throw new IllegalStateException("interpolation across a method event is disabled");
			double[] coefficients = new double[6];
			for (int c = 0; c < 6; c++) {
				double[] values = new double[table.axes().mach().length];
				for (int i = 0; i < values.length; i++) values[i] = table.cell(i, a.lo, b.lo).coefficients().toArray()[c];
				coefficients[c] = MonotonePchip1D.interpolate(table.axes().mach(), values, mach);
			}
			Set<DiagnosticFlag> flags = new HashSet<>(lower.diagnostics().flags()); flags.addAll(upper.diagnostics().flags()); flags.add(DiagnosticFlag.INTERPOLATED);
			Set<String> validity = new TreeSet<>(lower.validityFlags()); validity.addAll(upper.validityFlags());
			return new QueryResult(AerodynamicCoefficients.fromArray(coefficients), flags, List.copyOf(validity), lower.methodIds(), true);
		}
		List<TableCell> corners = new ArrayList<>(8);
		for (int im : new int[] {m.lo, m.hi}) for (int ia : new int[] {a.lo, a.hi}) for (int ib : new int[] {b.lo, b.hi}) corners.add(table.cell(im, ia, ib));
		return new EventAwareInterpolator().interpolate(corners, m.t, a.t, b.t);
	}
	private static Bracket bracket(double[] axis, double query) {
		if (!Double.isFinite(query) || query < axis[0] || query > axis[axis.length - 1]) throw new IllegalArgumentException("OUT_OF_DOMAIN");
		if (axis.length == 1) { if (Double.compare(query, axis[0]) != 0) throw new IllegalArgumentException("OUT_OF_DOMAIN"); return new Bracket(0, 0, 0, true); }
		for (int i = 0; i < axis.length; i++) if (Double.compare(query, axis[i]) == 0) return new Bracket(i, i, 0, true);
		for (int i = 0; i < axis.length - 1; i++) if (query < axis[i + 1]) return new Bracket(i, i + 1, (query - axis[i]) / (axis[i + 1] - axis[i]), false);
		throw new IllegalArgumentException("OUT_OF_DOMAIN");
	}
	private record Bracket(int lo, int hi, double t, boolean exact) {}
}
