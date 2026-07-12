package info.openrocket.core.aerodynamics.physicsaero.interpolation;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.DiagnosticFlag;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.math.MultilinearInterpolator;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;

public final class EventAwareInterpolator {
	public QueryResult interpolate(List<TableCell> corners, double tx, double ta, double tb) {
		if (corners.size() != 8) throw new IllegalArgumentException("eight corners required");
		Set<String> eventOwners = new HashSet<>(); for (TableCell c : corners) eventOwners.add(String.join("|", c.methodIds()));
		if (eventOwners.size() > 1) throw new IllegalStateException("interpolation across a method event is disabled");
		double[] result = new double[6];
		for (int coefficient = 0; coefficient < 6; coefficient++) { double[] values = new double[8]; for (int i = 0; i < 8; i++) values[i] = corners.get(i).coefficients().toArray()[coefficient]; result[coefficient] = MultilinearInterpolator.trilinear(values, tx, ta, tb); }
		Set<DiagnosticFlag> flags = new HashSet<>(); Set<String> validity = new TreeSet<>(); Set<String> methods = new TreeSet<>();
		for (TableCell c : corners) { flags.addAll(c.diagnostics().flags()); validity.addAll(c.validityFlags()); methods.addAll(c.methodIds()); }
		flags.add(DiagnosticFlag.INTERPOLATED);
		return new QueryResult(AerodynamicCoefficients.fromArray(result), flags, List.copyOf(validity), List.copyOf(methods), true);
	}
}
