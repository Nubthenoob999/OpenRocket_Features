package info.openrocket.core.util.ejection;

/**
 * Single-tube geometry used by the Lamé interference-fit calculation. All
 * dimensions are in inches; {@code youngsModulus} is in psi.
 */
public final class TubeGeometry {

	private final String componentName;
	private final AirframeMaterial material;
	private final double outerDiameter_in;
	private final double innerDiameter_in;
	private final double wallThickness_in;
	private final double outerRadius_in;
	private final double innerRadius_in;
	private final double youngsModulus_psi;
	private final double poissonsRatio;

	public TubeGeometry(String componentName,
						AirframeMaterial material,
						double outerDiameter_in,
						double innerDiameter_in,
						double youngsModulus_psi,
						double poissonsRatio) {
		if (outerDiameter_in <= 0.0 || innerDiameter_in < 0.0
				|| outerDiameter_in <= innerDiameter_in) {
			throw new IllegalArgumentException(
					"Tube geometry requires outerDiameter > innerDiameter >= 0 (got OD="
							+ outerDiameter_in + ", ID=" + innerDiameter_in + ")");
		}
		if (youngsModulus_psi <= 0.0) {
			throw new IllegalArgumentException("Young's modulus must be > 0");
		}
		if (poissonsRatio < 0.0 || poissonsRatio >= 0.5) {
			throw new IllegalArgumentException(
					"Poisson's ratio must be in [0, 0.5) (got " + poissonsRatio + ")");
		}
		this.componentName = componentName;
		this.material = material;
		this.outerDiameter_in = outerDiameter_in;
		this.innerDiameter_in = innerDiameter_in;
		this.wallThickness_in = (outerDiameter_in - innerDiameter_in) / 2.0;
		this.outerRadius_in = outerDiameter_in / 2.0;
		this.innerRadius_in = innerDiameter_in / 2.0;
		this.youngsModulus_psi = youngsModulus_psi;
		this.poissonsRatio = poissonsRatio;
	}

	public String getComponentName() { return componentName; }
	public AirframeMaterial getMaterial() { return material; }
	public double getOuterDiameter_in() { return outerDiameter_in; }
	public double getInnerDiameter_in() { return innerDiameter_in; }
	public double getWallThickness_in() { return wallThickness_in; }
	public double getOuterRadius_in() { return outerRadius_in; }
	public double getInnerRadius_in() { return innerRadius_in; }
	public double getYoungsModulus_psi() { return youngsModulus_psi; }
	public double getPoissonsRatio() { return poissonsRatio; }
}
