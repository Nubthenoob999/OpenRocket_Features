package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.Objects;
import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;
import info.openrocket.core.util.Coordinate;

/** Complete strip upstream state. Shock interaction replaces this object rather than adding a pressure jump. */
public record FinLocalFlow(GasState staticState, TotalState totalState, Coordinate velocityBody,
		double effectiveIncidenceRad, double normalMach, LeadingEdgeClassification leadingEdge,
		String stateSource) {
	public FinLocalFlow {
		Objects.requireNonNull(staticState); Objects.requireNonNull(totalState); Objects.requireNonNull(velocityBody);
		Objects.requireNonNull(leadingEdge); Objects.requireNonNull(stateSource);
		if (!Double.isFinite(effectiveIncidenceRad + normalMach) || normalMach < 0 || stateSource.isBlank())
			throw new IllegalArgumentException("invalid local flow");
	}
	public double dynamicPressurePa() {
		return 0.5 * staticState.densityKgM3() * velocityBody.length() * velocityBody.length();
	}
}
