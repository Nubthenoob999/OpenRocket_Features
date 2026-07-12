package info.openrocket.core.structures.geometry;

public final class TubeGeometry {
	private final double outerRadius;
	private final double innerRadius;
	private final double wallThickness;
	private final double length;
	private final double unsupportedLength;
	private final double axialPosition;
	private final String componentName;

	public TubeGeometry(double outerRadius, double innerRadius, double wallThickness, double length,
			double unsupportedLength, double axialPosition, String componentName) {
		this.outerRadius = outerRadius;
		this.innerRadius = innerRadius;
		this.wallThickness = wallThickness;
		this.length = length;
		this.unsupportedLength = unsupportedLength;
		this.axialPosition = axialPosition;
		this.componentName = componentName;
	}

	public double getOuterRadius() {
		return outerRadius;
	}

	public double getInnerRadius() {
		return innerRadius;
	}

	public double getWallThickness() {
		return wallThickness;
	}

	public double getLength() {
		return length;
	}

	public double getUnsupportedLength() {
		return unsupportedLength;
	}

	public double getAxialPosition() {
		return axialPosition;
	}

	public String getComponentName() {
		return componentName;
	}

	public double getOuterDiameter() {
		return 2.0 * outerRadius;
	}

	public double getInnerDiameter() {
		return 2.0 * innerRadius;
	}

	public double getArea() {
		return Math.PI * (outerRadius * outerRadius - innerRadius * innerRadius);
	}

	public double getSecondMomentOfArea() {
		return Math.PI / 4.0 * (Math.pow(outerRadius, 4) - Math.pow(innerRadius, 4));
	}

	public double getReferenceArea() {
		return Math.PI * outerRadius * outerRadius;
	}

	public boolean isComplete() {
		return Double.isFinite(outerRadius) && outerRadius > 0 &&
				Double.isFinite(innerRadius) && innerRadius >= 0 &&
				Double.isFinite(wallThickness) && wallThickness > 0 &&
				innerRadius < outerRadius &&
				Double.isFinite(length) && length > 0;
	}
}
