package info.openrocket.core.util.ejection;

/**
 * Coefficient-of-friction record for a single airframe material pair.
 * Static (μ_s) is the value the ejection charge must overcome first; kinetic
 * (μ_k) is included for completeness and post-separation modeling.
 */
public final class MaterialPairCOF {

	private final AirframeMaterial material1;
	private final AirframeMaterial material2;

	private final double staticMin;
	private final double staticNominal;
	private final double staticMax;

	private final double kineticMin;
	private final double kineticNominal;
	private final double kineticMax;

	private final String confidenceLevel;
	private final String dataSources;
	private final String rocketryNotes;

	public MaterialPairCOF(AirframeMaterial material1,
						   AirframeMaterial material2,
						   double staticMin, double staticNominal, double staticMax,
						   double kineticMin, double kineticNominal, double kineticMax,
						   String confidenceLevel,
						   String dataSources,
						   String rocketryNotes) {
		this.material1 = material1;
		this.material2 = material2;
		this.staticMin = staticMin;
		this.staticNominal = staticNominal;
		this.staticMax = staticMax;
		this.kineticMin = kineticMin;
		this.kineticNominal = kineticNominal;
		this.kineticMax = kineticMax;
		this.confidenceLevel = confidenceLevel;
		this.dataSources = dataSources;
		this.rocketryNotes = rocketryNotes;
	}

	public AirframeMaterial getMaterial1() { return material1; }
	public AirframeMaterial getMaterial2() { return material2; }

	public double getStaticMin() { return staticMin; }
	public double getStaticNominal() { return staticNominal; }
	public double getStaticMax() { return staticMax; }

	public double getKineticMin() { return kineticMin; }
	public double getKineticNominal() { return kineticNominal; }
	public double getKineticMax() { return kineticMax; }

	public String getConfidenceLevel() { return confidenceLevel; }
	public String getDataSources() { return dataSources; }
	public String getRocketryNotes() { return rocketryNotes; }
}
