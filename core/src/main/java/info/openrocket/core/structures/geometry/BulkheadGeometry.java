package info.openrocket.core.structures.geometry;

public final class BulkheadGeometry {
	private final double outerRadius;
	private final double thickness;
	private final double axialPosition;
	private final String componentName;

	public BulkheadGeometry(double outerRadius, double thickness, double axialPosition, String componentName) {
		this.outerRadius = outerRadius;
		this.thickness = thickness;
		this.axialPosition = axialPosition;
		this.componentName = componentName;
	}

	public double getOuterRadius() {
		return outerRadius;
	}

	public double getThickness() {
		return thickness;
	}

	public double getAxialPosition() {
		return axialPosition;
	}

	public String getComponentName() {
		return componentName;
	}
}
