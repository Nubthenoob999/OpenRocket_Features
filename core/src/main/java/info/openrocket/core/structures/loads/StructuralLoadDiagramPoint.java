package info.openrocket.core.structures.loads;

public final class StructuralLoadDiagramPoint {
	private final double station;
	private final double shearForce;
	private final double bendingMoment;

	public StructuralLoadDiagramPoint(double station, double shearForce, double bendingMoment) {
		this.station = station;
		this.shearForce = shearForce;
		this.bendingMoment = bendingMoment;
	}

	public double getStation() {
		return station;
	}

	public double getShearForce() {
		return shearForce;
	}

	public double getBendingMoment() {
		return bendingMoment;
	}
}
