package info.openrocket.core.structures.materials;

public final class StructuralMaterial {
	private final String name;
	private final double youngsModulus;
	private final double shearModulus;
	private final double yieldStrength;
	private final double ultimateStrength;
	private final double compressiveStrength;
	private final double density;
	private final Double poissonRatio;

	public StructuralMaterial(String name, double youngsModulus, double shearModulus, double yieldStrength,
			double ultimateStrength, double compressiveStrength, double density, Double poissonRatio) {
		this.name = name;
		this.youngsModulus = youngsModulus;
		this.shearModulus = shearModulus;
		this.yieldStrength = yieldStrength;
		this.ultimateStrength = ultimateStrength;
		this.compressiveStrength = compressiveStrength;
		this.density = density;
		this.poissonRatio = poissonRatio;
	}

	public String getName() {
		return name;
	}

	public double getYoungsModulus() {
		return youngsModulus;
	}

	public double getShearModulus() {
		return shearModulus;
	}

	public double getYieldStrength() {
		return yieldStrength;
	}

	public double getUltimateStrength() {
		return ultimateStrength;
	}

	public double getCompressiveStrength() {
		return compressiveStrength;
	}

	public double getDensity() {
		return density;
	}

	public Double getPoissonRatio() {
		return poissonRatio;
	}

	public double getBestAllowableStress() {
		if (Double.isFinite(yieldStrength) && yieldStrength > 0) {
			return yieldStrength;
		}
		if (Double.isFinite(compressiveStrength) && compressiveStrength > 0) {
			return compressiveStrength;
		}
		return ultimateStrength;
	}

	public boolean hasRequiredStrengthAndStiffness() {
		return Double.isFinite(youngsModulus) && youngsModulus > 0 &&
				Double.isFinite(getBestAllowableStress()) && getBestAllowableStress() > 0;
	}
}
