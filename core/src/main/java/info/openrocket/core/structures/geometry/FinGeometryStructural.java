package info.openrocket.core.structures.geometry;

public final class FinGeometryStructural {
	private final int finCount;
	private final double rootChord;
	private final double tipChord;
	private final double semiSpan;
	private final double sweep;
	private final double thickness;
	private final double axialLeadingEdgePosition;
	private final double planformArea;
	private final String componentName;

	public FinGeometryStructural(int finCount, double rootChord, double tipChord, double semiSpan, double sweep,
			double thickness, double axialLeadingEdgePosition, double planformArea, String componentName) {
		this.finCount = finCount;
		this.rootChord = rootChord;
		this.tipChord = tipChord;
		this.semiSpan = semiSpan;
		this.sweep = sweep;
		this.thickness = thickness;
		this.axialLeadingEdgePosition = axialLeadingEdgePosition;
		this.planformArea = planformArea;
		this.componentName = componentName;
	}

	public int getFinCount() {
		return finCount;
	}

	public double getRootChord() {
		return rootChord;
	}

	public double getTipChord() {
		return tipChord;
	}

	public double getSemiSpan() {
		return semiSpan;
	}

	public double getSweep() {
		return sweep;
	}

	public double getThickness() {
		return thickness;
	}

	public double getAxialLeadingEdgePosition() {
		return axialLeadingEdgePosition;
	}

	public double getPlanformArea() {
		return planformArea;
	}

	public String getComponentName() {
		return componentName;
	}

	public double getMeanAerodynamicChord() {
		if (rootChord <= 0) {
			return Double.NaN;
		}
		double taperRatio = tipChord / rootChord;
		return (2.0 / 3.0) * rootChord * (1.0 + taperRatio + taperRatio * taperRatio) / (1.0 + taperRatio);
	}

	public boolean isComplete() {
		return finCount > 0 &&
				Double.isFinite(rootChord) && rootChord > 0 &&
				Double.isFinite(tipChord) && tipChord >= 0 &&
				Double.isFinite(semiSpan) && semiSpan > 0 &&
				Double.isFinite(thickness) && thickness > 0;
	}
}
