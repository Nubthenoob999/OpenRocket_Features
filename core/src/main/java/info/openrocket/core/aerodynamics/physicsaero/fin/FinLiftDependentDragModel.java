package info.openrocket.core.aerodynamics.physicsaero.fin;

public final class FinLiftDependentDragModel {
	public double forceN(double normalForceN, double incidenceRad) { return Math.abs(normalForceN * Math.sin(incidenceRad)); }
}
