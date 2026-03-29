package info.openrocket.core.aerodynamics.rom.core.geometry;

/**
 * Geometry parameters required by rom.core physics models.
 * All fields are in SI units and this type has no OpenRocket dependencies.
 */
public final class RomGeometryInput {

	public final double bodyLength;
	public final double maxDiameter;
	public final double baseArea;
	public final double wetArea;
	public final double noseLength;
	public final NoseShape noseShape;
	public final double finenessRatio;
	public final double referenceArea;

	public final double boattailLength;
	public final double boattailBaseDiameter;

	public final int finCount;
	public final double finRootChord;
	public final double finTipChord;
	public final double finSpan;
	public final double finThickness;
	public final double finSweepAngle;
	public final double finWettedArea;
	public final double finAxialPosition;

	public final double motorExitArea;
	public final double surfaceRoughness;

	public enum NoseShape {
		CONICAL,
		OGIVE,
		VON_KARMAN,
		PARABOLIC,
		ELLIPSOID,
		HAACK
	}

	public RomGeometryInput(
			double bodyLength, double maxDiameter, double baseArea,
			double wetArea, double noseLength, NoseShape noseShape,
			double finenessRatio, double referenceArea,
			double boattailLength, double boattailBaseDiameter,
			int finCount, double finRootChord, double finTipChord,
			double finSpan, double finThickness, double finSweepAngle,
			double finWettedArea, double finAxialPosition,
			double motorExitArea, double surfaceRoughness) {
		this.bodyLength = bodyLength;
		this.maxDiameter = maxDiameter;
		this.baseArea = baseArea;
		this.wetArea = wetArea;
		this.noseLength = noseLength;
		this.noseShape = noseShape;
		this.finenessRatio = finenessRatio;
		this.referenceArea = referenceArea;
		this.boattailLength = boattailLength;
		this.boattailBaseDiameter = boattailBaseDiameter;
		this.finCount = finCount;
		this.finRootChord = finRootChord;
		this.finTipChord = finTipChord;
		this.finSpan = finSpan;
		this.finThickness = finThickness;
		this.finSweepAngle = finSweepAngle;
		this.finWettedArea = finWettedArea;
		this.finAxialPosition = finAxialPosition;
		this.motorExitArea = motorExitArea;
		this.surfaceRoughness = surfaceRoughness;
	}

	public RomGeometryInput(
			double bodyLength, double maxDiameter, double baseArea,
			double wetArea, double noseLength, NoseShape noseShape,
			double finenessRatio, double referenceArea,
			double boattailLength, double boattailBaseDiameter,
			int finCount, double finRootChord, double finTipChord,
			double finSpan, double finThickness, double finSweepAngle,
			double finWettedArea, double motorExitArea, double surfaceRoughness) {
		this(bodyLength, maxDiameter, baseArea,
				wetArea, noseLength, noseShape,
				finenessRatio, referenceArea,
				boattailLength, boattailBaseDiameter,
				finCount, finRootChord, finTipChord,
				finSpan, finThickness, finSweepAngle,
				finWettedArea,
				Math.max(0.0, bodyLength - finRootChord - Math.max(boattailLength, 0.0)),
				motorExitArea, surfaceRoughness);
	}

	/**
	 * Returns a normalized geometry feature vector used for geometry-space POD selection.
	 */
	public double[] toFeatureVector() {
		double[] v = new double[10];

		v[0] = (maxDiameter > 1e-9) ? Math.min(bodyLength / maxDiameter, 30.0) / 30.0 : 0.0;
		v[1] = (bodyLength > 1e-9) ? Math.min(noseLength / bodyLength, 1.0) : 0.0;
		v[2] = (bodyLength > 1e-9) ? Math.min(boattailLength / bodyLength, 0.5) / 0.5 : 0.0;
		v[3] = (maxDiameter > 1e-9 && boattailBaseDiameter > 0.0) ? boattailBaseDiameter / maxDiameter : 1.0;

		if (finCount > 0 && finRootChord > 1e-9) {
			double meanChord = (finRootChord + finTipChord) / 2.0;
			v[4] = Math.min(2.0 * finSpan / meanChord, 8.0) / 8.0;
			v[5] = Math.min(finTipChord / finRootChord, 1.0);
			v[6] = Math.min(finThickness / meanChord, 0.2) / 0.2;
		}

		v[7] = (referenceArea > 1e-12) ? Math.min(finCount * finWettedArea / referenceArea, 5.0) / 5.0 : 0.0;

		switch (noseShape) {
			case CONICAL:
				v[8] = 0.0 / 5.0;
				break;
			case PARABOLIC:
				v[8] = 1.0 / 5.0;
				break;
			case ELLIPSOID:
				v[8] = 2.0 / 5.0;
				break;
			case OGIVE:
				v[8] = 3.0 / 5.0;
				break;
			case VON_KARMAN:
				v[8] = 4.0 / 5.0;
				break;
			case HAACK:
				v[8] = 5.0 / 5.0;
				break;
			default:
				v[8] = 3.0 / 5.0;
				break;
		}

		v[9] = (baseArea > 1e-12) ? Math.min(motorExitArea / baseArea, 1.0) : 0.0;
		return v;
	}

	/**
	 * Euclidean distance in normalized geometry feature space.
	 */
	public double featureDistance(RomGeometryInput other) {
		double[] a = this.toFeatureVector();
		double[] b = other.toFeatureVector();
		double sum = 0.0;
		for (int i = 0; i < a.length; i++) {
			double d = a[i] - b[i];
			sum += d * d;
		}
		return Math.sqrt(sum);
	}
}
