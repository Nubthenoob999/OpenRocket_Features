package info.openrocket.core.aerodynamics.physicsaero.geometry;

/** Repeated external feature geometry, including annular lips and fin anchors. */
public record ProtuberanceGeometry(String type, int count, double axialPositionM,
		double lengthM, double heightM, double projectedAreaM2, double frontalAreaM2) {
	public ProtuberanceGeometry {
		if (type == null || type.isBlank() || count <= 0 || !Double.isFinite(axialPositionM)
				|| !(lengthM > 0) || heightM < 0 || projectedAreaM2 < 0 || frontalAreaM2 < 0
				|| !Double.isFinite(lengthM + heightM + projectedAreaM2 + frontalAreaM2)) {
			throw new IllegalArgumentException("invalid protuberance geometry");
		}
	}

	public ProtuberanceGeometry(String type, double lengthM, double projectedAreaM2,
			double frontalAreaM2) {
		this(type, 1, 0, lengthM, 0, projectedAreaM2, frontalAreaM2);
	}

	public static ProtuberanceGeometry annularLip(double axialPositionM, double axialLengthM,
			double radialHeightM, double outerRadiusM) {
		double projected = 2 * Math.PI * outerRadiusM * axialLengthM;
		double frontal = Math.PI * (outerRadiusM * outerRadiusM
				- Math.pow(Math.max(0, outerRadiusM - radialHeightM), 2));
		return new ProtuberanceGeometry("ANNULAR_LIP", 1, axialPositionM, axialLengthM,
				radialHeightM, projected, frontal);
	}

	public static ProtuberanceGeometry finAnchors(int count, double axialPositionM,
			double axialLengthM, double heightM, double projectedAreaEachM2,
			double frontalAreaEachM2) {
		return new ProtuberanceGeometry("FIN_ANCHOR", count, axialPositionM, axialLengthM,
				heightM, count * projectedAreaEachM2, count * frontalAreaEachM2);
	}
}
