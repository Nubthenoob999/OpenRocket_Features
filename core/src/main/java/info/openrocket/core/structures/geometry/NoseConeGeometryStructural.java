package info.openrocket.core.structures.geometry;

public final class NoseConeGeometryStructural {
	private final double length;
	private final double baseRadius;
	private final double axialPosition;
	private final String shape;
	private final String componentName;

	public NoseConeGeometryStructural(double length, double baseRadius, double axialPosition, String shape,
			String componentName) {
		this.length = length;
		this.baseRadius = baseRadius;
		this.axialPosition = axialPosition;
		this.shape = shape;
		this.componentName = componentName;
	}

	public double getLength() {
		return length;
	}

	public double getBaseRadius() {
		return baseRadius;
	}

	public double getAxialPosition() {
		return axialPosition;
	}

	public String getShape() {
		return shape;
	}

	public String getComponentName() {
		return componentName;
	}
}
