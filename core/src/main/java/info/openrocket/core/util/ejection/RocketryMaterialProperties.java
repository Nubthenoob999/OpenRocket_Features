package info.openrocket.core.util.ejection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Hoop-direction Young's modulus (psi) and Poisson's ratio for rocketry
 * airframe materials, used by the Lamé interference-fit calculation.
 *
 * <p>Composite values assume woven or ±45 wound layups (typical for HPR
 * tubing). Unidirectional axial layups have a much lower hoop modulus and
 * are not suitable for interference-fit analysis with these constants.
 */
public final class RocketryMaterialProperties {

	/** Index 0: E_hoop in psi.  Index 1: Poisson's ratio (dimensionless). */
	private static final Map<AirframeMaterial, double[]> TABLE = buildTable();

	/**
	 * Approximate ultimate hoop tensile strength (psi) for typical HPR
	 * airframe materials. Used by the ejection-charge engine to warn the
	 * operator when the predicted internal pressure produces a hoop stress
	 * close to or exceeding the airframe's burst limit.
	 */
	private static final Map<AirframeMaterial, Double> HOOP_UTS_PSI = buildHoopUts();

	private RocketryMaterialProperties() {
	}

	private static Map<AirframeMaterial, double[]> buildTable() {
		EnumMap<AirframeMaterial, double[]> t = new EnumMap<>(AirframeMaterial.class);
		t.put(AirframeMaterial.FIBERGLASS,   new double[] { 2_500_000.0, 0.28 });
		t.put(AirframeMaterial.CARBON_FIBER, new double[] { 7_250_000.0, 0.09 });
		t.put(AirframeMaterial.PHENOLIC,     new double[] { 1_150_000.0, 0.34 });
		t.put(AirframeMaterial.CARDBOARD,    new double[] {   550_000.0, 0.31 });
		t.put(AirframeMaterial.BALSA_WOOD,   new double[] {   100_000.0, 0.28 });
		t.put(AirframeMaterial.HARDWOOD,     new double[] { 1_450_000.0, 0.36 });
		t.put(AirframeMaterial.BLUE_TUBE,    new double[] { 1_500_000.0, 0.33 });
		t.put(AirframeMaterial.PLASTIC_NC,   new double[] {   360_000.0, 0.38 });
		return Collections.unmodifiableMap(t);
	}

	private static Map<AirframeMaterial, Double> buildHoopUts() {
		EnumMap<AirframeMaterial, Double> t = new EnumMap<>(AirframeMaterial.class);
		t.put(AirframeMaterial.FIBERGLASS,   30_000.0);
		t.put(AirframeMaterial.CARBON_FIBER, 80_000.0);
		t.put(AirframeMaterial.PHENOLIC,      7_000.0);
		t.put(AirframeMaterial.CARDBOARD,     3_000.0);
		t.put(AirframeMaterial.BALSA_WOOD,    1_500.0);
		t.put(AirframeMaterial.HARDWOOD,     10_000.0);
		t.put(AirframeMaterial.BLUE_TUBE,    10_000.0);
		t.put(AirframeMaterial.PLASTIC_NC,    5_000.0);
		return Collections.unmodifiableMap(t);
	}

	public static double getYoungsModulus_psi(AirframeMaterial mat) {
		return getProperties(mat)[0];
	}

	public static double getPoissonsRatio(AirframeMaterial mat) {
		return getProperties(mat)[1];
	}

	public static double getHoopUts_psi(AirframeMaterial mat) {
		Double v = HOOP_UTS_PSI.get(mat);
		if (v == null) {
			throw new IllegalArgumentException("No UTS data for: " + mat);
		}
		return v;
	}

	private static double[] getProperties(AirframeMaterial mat) {
		double[] props = TABLE.get(mat);
		if (props == null) {
			throw new IllegalArgumentException("No material properties for: " + mat);
		}
		return props;
	}
}
