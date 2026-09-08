package info.openrocket.core.structures.materials;

public final class StructuralMaterial {
	private final String name;
	private final double youngsModulus;
	private final double shearModulus;
	private final double tensileAllowable;
	private final double ultimateStrength;
	private final double compressiveStrength;
	private final double density;
	private final Double poissonRatio;
	private final double shearStrength;

	public StructuralMaterial(String name, double youngsModulus, double shearModulus, double tensileAllowable,
			double ultimateStrength, double compressiveStrength, double density, Double poissonRatio) {
		this(name, youngsModulus, shearModulus, tensileAllowable, ultimateStrength, compressiveStrength,
				density, poissonRatio, Double.NaN);
	}

	public StructuralMaterial(String name, double youngsModulus, double shearModulus, double tensileAllowable,
			double ultimateStrength, double compressiveStrength, double density, Double poissonRatio,
			double shearStrength) {
		this.name = name;
		this.youngsModulus = youngsModulus;
		this.shearModulus = shearModulus;
		this.tensileAllowable = tensileAllowable;
		this.ultimateStrength = ultimateStrength;
		this.compressiveStrength = compressiveStrength;
		this.density = density;
		this.poissonRatio = poissonRatio;
		this.shearStrength = shearStrength;
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

	/**
	 * Legacy accessor.  The stored value is a tensile design limit: yield for
	 * ductile entries and a tensile failure limit for brittle entries.
	 */
	public double getYieldStrength() {
		return tensileAllowable;
	}

	public double getTensileAllowable() {
		return tensileAllowable;
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

	/** Transverse shear allowable in Pascals, or NaN when unavailable. */
	public double getShearStrength() {
		return shearStrength;
	}

	public double getBestAllowableStress() {
		double tensile = positiveOrNaN(tensileAllowable);
		double compression = positiveOrNaN(compressiveStrength);
		if (Double.isFinite(tensile) && Double.isFinite(compression)) {
			return Math.min(tensile, compression);
		}
		if (Double.isFinite(tensile)) {
			return tensile;
		}
		if (Double.isFinite(compression)) {
			return compression;
		}
		return positiveOrNaN(ultimateStrength);
	}

	/** Strength used by column/buckling calculations. */
	public double getColumnStrength() {
		double compression = positiveOrNaN(compressiveStrength);
		return Double.isFinite(compression) ? compression : getBestAllowableStress();
	}

	private static double positiveOrNaN(double value) {
		return Double.isFinite(value) && value > 0.0 ? value : Double.NaN;
	}

	public boolean hasRequiredStrengthAndStiffness() {
		return Double.isFinite(youngsModulus) && youngsModulus > 0 &&
				Double.isFinite(getBestAllowableStress()) && getBestAllowableStress() > 0;
	}
}
