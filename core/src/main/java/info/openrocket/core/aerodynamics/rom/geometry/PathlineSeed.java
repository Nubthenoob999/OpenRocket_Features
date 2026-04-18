package info.openrocket.core.aerodynamics.rom.geometry;

public class PathlineSeed {
	public enum SeedFamily {
		BODY_MERIDIAN,
		FIN_SURFACE,
		AFT_BODY_PLACEHOLDER
	}

	private final SeedFamily family;
	private final String componentName;
	private final int index;
	private final double x;
	private final double y;
	private final double z;
	private final double directionX;
	private final double directionY;
	private final double directionZ;
	private final double areaWeight;

	public PathlineSeed(SeedFamily family, String componentName, int index, double x, double y, double z,
			double directionX, double directionY, double directionZ, double areaWeight) {
		this.family = family;
		this.componentName = componentName;
		this.index = index;
		this.x = x;
		this.y = y;
		this.z = z;
		this.directionX = directionX;
		this.directionY = directionY;
		this.directionZ = directionZ;
		this.areaWeight = areaWeight;
	}

	public SeedFamily getFamily() {
		return family;
	}

	public String getComponentName() {
		return componentName;
	}

	public int getIndex() {
		return index;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	public double getDirectionX() {
		return directionX;
	}

	public double getDirectionY() {
		return directionY;
	}

	public double getDirectionZ() {
		return directionZ;
	}

	public double getAreaWeight() {
		return areaWeight;
	}
}
