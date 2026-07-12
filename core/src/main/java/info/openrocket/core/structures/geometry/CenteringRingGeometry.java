package info.openrocket.core.structures.geometry;

public final class CenteringRingGeometry {
	private final double outerRadius;
	private final double innerRadius;
	private final double thickness;
	private final int numberOfRings;
	private final double axialPosition;
	private final String componentName;

	public CenteringRingGeometry(double outerRadius, double innerRadius, double thickness, int numberOfRings,
			double axialPosition, String componentName) {
		this.outerRadius = outerRadius;
		this.innerRadius = innerRadius;
		this.thickness = thickness;
		this.numberOfRings = numberOfRings;
		this.axialPosition = axialPosition;
		this.componentName = componentName;
	}

	public double getOuterRadius() {
		return outerRadius;
	}

	public double getInnerRadius() {
		return innerRadius;
	}

	public double getThickness() {
		return thickness;
	}

	public int getNumberOfRings() {
		return numberOfRings;
	}

	public double getAxialPosition() {
		return axialPosition;
	}

	public String getComponentName() {
		return componentName;
	}
}
