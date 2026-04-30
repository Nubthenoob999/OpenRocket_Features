package info.openrocket.core.util.ejection;

import java.util.Locale;

/**
 * Hollow-cylinder interference-fit friction force calculator using Lamé's
 * thick-walled cylinder equations.
 *
 * <p>All inputs and outputs are in IMPERIAL units (inches, psi, lbs). The
 * orchestrating engine performs SI ↔ imperial conversions at its boundary.
 *
 * <p>Equation summary (see pseudocode header for full derivation):
 * <pre>
 *   P_c        = δ / ( d_nom × [ C_outer/E_outer + C_inner/E_inner ] )
 *   A          = π × d_nom × L
 *   F_friction = μ_s × P_c × A
 *
 *   C_outer = (r_o² + R²)/(r_o² − R²) + ν_outer    (hub formula)
 *   C_inner = (R² + r_i²)/(R² − r_i²) − ν_inner    (shaft formula)
 * </pre>
 */
public final class CylinderFrictionForceCalculator {

	private CylinderFrictionForceCalculator() {
	}

	/** Detailed Lamé output for diagnostics and engine consumption. */
	public static final class FrictionResult {
		private final double contactPressure_psi;
		private final double contactArea_sqin;
		private final double frictionForce_lbs;
		private final double lameFactorOuter;
		private final double lameFactorInner;
		private final double compliance;
		private final String diagnosticMsg;

		FrictionResult(double pc, double a, double f,
					   double co, double ci, double comp, String msg) {
			this.contactPressure_psi = pc;
			this.contactArea_sqin = a;
			this.frictionForce_lbs = f;
			this.lameFactorOuter = co;
			this.lameFactorInner = ci;
			this.compliance = comp;
			this.diagnosticMsg = msg;
		}

		public double getContactPressure_psi() { return contactPressure_psi; }
		public double getContactArea_sqin() { return contactArea_sqin; }
		public double getFrictionForce_lbs() { return frictionForce_lbs; }
		public double getLameFactorOuter() { return lameFactorOuter; }
		public double getLameFactorInner() { return lameFactorInner; }
		public double getCompliance() { return compliance; }
		public String getDiagnosticMsg() { return diagnosticMsg; }
	}

	/**
	 * Lamé geometry factor for one tube.
	 *
	 * @param interfaceRadius mating radius R (in)
	 * @param farRadius       outer radius for the hub, inner radius for the shaft (in)
	 * @param poissonsRatio   ν for this tube's material
	 * @param isOuterTube     true → hub formula (+ν); false → shaft formula (−ν)
	 */
	public static double lameGeometryFactor(double interfaceRadius,
											double farRadius,
											double poissonsRatio,
											boolean isOuterTube) {
		double R_sq = interfaceRadius * interfaceRadius;
		double r_sq = farRadius * farRadius;

		if (isOuterTube) {
			if (r_sq <= R_sq) {
				throw new IllegalArgumentException(
						"Outer tube outer radius must be > interface radius");
			}
			return (r_sq + R_sq) / (r_sq - R_sq) + poissonsRatio;
		} else {
			if (R_sq <= r_sq) {
				throw new IllegalArgumentException(
						"Inner tube inner radius must be < interface radius");
			}
			return (R_sq + r_sq) / (R_sq - r_sq) - poissonsRatio;
		}
	}

	/**
	 * Contact pressure at the mating interface (psi). Returns 0 when there is
	 * no diametral interference (slip or clearance fit).
	 */
	public static double contactPressure(double delta_diametral_in,
										 double nominalDiameter_in,
										 TubeGeometry outerTube,
										 TubeGeometry innerTube) {
		if (delta_diametral_in <= 0.0) {
			return 0.0;
		}
		double R = nominalDiameter_in / 2.0;
		double C_outer = lameGeometryFactor(R,
				outerTube.getOuterRadius_in(), outerTube.getPoissonsRatio(), true);
		double C_inner = lameGeometryFactor(R,
				innerTube.getInnerRadius_in(), innerTube.getPoissonsRatio(), false);
		double compliance = C_outer / outerTube.getYoungsModulus_psi()
				+ C_inner / innerTube.getYoungsModulus_psi();
		return delta_diametral_in / (nominalDiameter_in * compliance);
	}

	/** Mating contact area (in²) of two cylinders engaged over length L. */
	public static double contactArea(double nominalDiameter_in,
									 double overlapLength_in) {
		return Math.PI * nominalDiameter_in * overlapLength_in;
	}

	/**
	 * Single-call entry point: bundles all Lamé steps and returns a
	 * {@link FrictionResult}. Returns 0 force when {@code delta ≤ 0}.
	 */
	public static FrictionResult calculate(AirframeMaterial outerMaterial,
										   AirframeMaterial innerMaterial,
										   double outerTubeID_in,
										   double outerTubeOD_in,
										   double innerTubeID_in,
										   double innerTubeOD_in,
										   double overlapLength_in,
										   double delta_diametral_in,
										   double mu_s) {

		TubeGeometry outerTube = new TubeGeometry(
				"Outer (Airframe)", outerMaterial,
				outerTubeOD_in, outerTubeID_in,
				RocketryMaterialProperties.getYoungsModulus_psi(outerMaterial),
				RocketryMaterialProperties.getPoissonsRatio(outerMaterial));

		TubeGeometry innerTube = new TubeGeometry(
				"Inner (Coupler)", innerMaterial,
				innerTubeOD_in, innerTubeID_in,
				RocketryMaterialProperties.getYoungsModulus_psi(innerMaterial),
				RocketryMaterialProperties.getPoissonsRatio(innerMaterial));

		double d_nom = outerTubeID_in;
		double R = d_nom / 2.0;

		double C_outer = lameGeometryFactor(R,
				outerTube.getOuterRadius_in(), outerTube.getPoissonsRatio(), true);
		double C_inner = lameGeometryFactor(R,
				innerTube.getInnerRadius_in(), innerTube.getPoissonsRatio(), false);
		double compliance = C_outer / outerTube.getYoungsModulus_psi()
				+ C_inner / innerTube.getYoungsModulus_psi();

		double P_c = contactPressure(delta_diametral_in, d_nom, outerTube, innerTube);
		double A = contactArea(d_nom, overlapLength_in);
		double F = mu_s * P_c * A;

		String diag = String.format(Locale.ROOT,
				"δ = %.4f in | d_nom = %.3f in | L = %.2f in | "
						+ "P_c = %.2f psi | A = %.4f in² | "
						+ "F = %.2f lbs (μ_s = %.2f)",
				delta_diametral_in, d_nom, overlapLength_in, P_c, A, F, mu_s);

		return new FrictionResult(P_c, A, F, C_outer, C_inner, compliance, diag);
	}
}
