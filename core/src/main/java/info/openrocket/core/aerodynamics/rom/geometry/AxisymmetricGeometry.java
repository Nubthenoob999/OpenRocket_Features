package info.openrocket.core.aerodynamics.rom.geometry;

public class AxisymmetricGeometry {
	private final String componentName;
	private final String componentType;
	private final double xStart;
	private final double xEnd;
	private final double foreRadius;
	private final double aftRadius;
	private final double[] xSamples;
	private final double[] radiusSamples;

	public AxisymmetricGeometry(String componentName, String componentType, double xStart, double xEnd,
			double foreRadius, double aftRadius, double[] xSamples, double[] radiusSamples) {
		this.componentName = componentName;
		this.componentType = componentType;
		this.xStart = xStart;
		this.xEnd = xEnd;
		this.foreRadius = foreRadius;
		this.aftRadius = aftRadius;
		this.xSamples = xSamples.clone();
		this.radiusSamples = radiusSamples.clone();
	}

	public String getComponentName() {
		return componentName;
	}

	public String getComponentType() {
		return componentType;
	}

	public double getXStart() {
		return xStart;
	}

	public double getXEnd() {
		return xEnd;
	}

	public double getForeRadius() {
		return foreRadius;
	}

	public double getAftRadius() {
		return aftRadius;
	}

	public double[] getXSamples() {
		return xSamples.clone();
	}

	public double[] getRadiusSamples() {
		return radiusSamples.clone();
	}
}
