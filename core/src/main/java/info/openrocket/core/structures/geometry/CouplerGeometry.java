package info.openrocket.core.structures.geometry;

public final class CouplerGeometry {
	private final double outerRadius;
	private final double innerRadius;
	private final double length;
	private final double axialPosition;
	private final String componentName;

	public CouplerGeometry(double outerRadius, double innerRadius, double length, double axialPosition,
			String componentName) {
		this.outerRadius = outerRadius;
		this.innerRadius = innerRadius;
		this.length = length;
		this.axialPosition = axialPosition;
		this.componentName = componentName;
	}

	public double getOuterRadius() {
		return outerRadius;
	}

	public double getInnerRadius() {
		return innerRadius;
	}

	public double getLength() {
		return length;
	}

	public double getAxialPosition() {
		return axialPosition;
	}

	public String getComponentName() {
		return componentName;
	}
}
