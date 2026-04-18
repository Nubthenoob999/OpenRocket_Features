package info.openrocket.core.aerodynamics.rom.geometry;

public class SurfacePatch {
	public enum PatchType {
		BODY,
		FIN,
		BASE
	}

	private final String patchId;
	private final String componentName;
	private final PatchType patchType;
	private final double xStart;
	private final double xEnd;
	private final double centroidX;
	private final double areaWeight;
	private final double normalAngleRad;

	public SurfacePatch(String patchId, String componentName, PatchType patchType, double xStart, double xEnd,
			double centroidX, double areaWeight, double normalAngleRad) {
		this.patchId = patchId;
		this.componentName = componentName;
		this.patchType = patchType;
		this.xStart = xStart;
		this.xEnd = xEnd;
		this.centroidX = centroidX;
		this.areaWeight = areaWeight;
		this.normalAngleRad = normalAngleRad;
	}

	public String getPatchId() {
		return patchId;
	}

	public String getComponentName() {
		return componentName;
	}

	public PatchType getPatchType() {
		return patchType;
	}

	public double getXStart() {
		return xStart;
	}

	public double getXEnd() {
		return xEnd;
	}

	public double getCentroidX() {
		return centroidX;
	}

	public double getAreaWeight() {
		return areaWeight;
	}

	public double getNormalAngleRad() {
		return normalAngleRad;
	}
}
