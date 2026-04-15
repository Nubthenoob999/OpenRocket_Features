package info.openrocket.core.aerodynamics.rom.core.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Geometry parameters required by rom.core physics models.
 * All fields are in SI units and this type has no OpenRocket dependencies.
 */
public final class RomGeometryInput {

	public final double bodyLength;
	public final double maxDiameter;
	public final double baseArea;
	public final double wetArea;
	public final double bodyWetArea;
	public final double noseLength;
	public final NoseShape noseShape;
	public final double finenessRatio;
	public final double referenceArea;

	public final double boattailLength;
	public final double boattailBaseDiameter;

	public final List<FinGeom> finSets;
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

	public record FinGeom(
			int count,
			double rootChord,
			double tipChord,
			double span,
			double thickness,
			double sweepLength,
			double wettedArea,
			double axialPosition,
			String finType,
			boolean thicknessFallbackUsed) {

		public FinGeom {
			count = Math.max(0, count);
			rootChord = sanitizeNonNegative(rootChord);
			tipChord = sanitizeNonNegative(tipChord);
			span = sanitizeNonNegative(span);
			thickness = sanitizeNonNegative(thickness);
			sweepLength = Double.isFinite(sweepLength) ? sweepLength : 0.0;
			wettedArea = sanitizeNonNegative(wettedArea);
			axialPosition = sanitizeNonNegative(axialPosition);
			finType = (finType == null || finType.isBlank()) ? "UNKNOWN" : finType;
		}

		public double meanChord() {
			double totalPlanformArea = totalPlanformArea();
			if (span > 1e-9 && totalPlanformArea > 1e-12 && count > 0) {
				return totalPlanformArea / (span * count);
			}
			double meanChord = 0.5 * (rootChord + tipChord);
			return sanitizeNonNegative(meanChord);
		}

		public double totalPlanformArea() {
			return Math.max(0.0, wettedArea) / 2.0;
		}

		public double totalPlanformAreaPerFin() {
			return count > 0 ? totalPlanformArea() / count : 0.0;
		}

		public double aspectRatio() {
			double cMean = meanChord();
			return cMean > 1e-9 ? 2.0 * span / cMean : 0.0;
		}

		private static double sanitizeNonNegative(double value) {
			return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
		}
	}

	public RomGeometryInput(
			double bodyLength, double maxDiameter, double baseArea, double bodyWetArea,
			double wetArea, double noseLength, NoseShape noseShape,
			double finenessRatio, double referenceArea,
			double boattailLength, double boattailBaseDiameter,
			List<FinGeom> finSets,
			int finCount, double finRootChord, double finTipChord,
			double finSpan, double finThickness, double finSweepAngle,
			double finWettedArea, double finAxialPosition,
			double motorExitArea, double surfaceRoughness) {
		this.bodyLength = sanitizeNonNegative(bodyLength);
		this.maxDiameter = sanitizeNonNegative(maxDiameter);
		this.baseArea = sanitizeNonNegative(baseArea);
		this.bodyWetArea = sanitizeNonNegative(bodyWetArea);
		this.wetArea = sanitizeNonNegative(wetArea);
		this.noseLength = sanitizeNonNegative(noseLength);
		this.noseShape = noseShape == null ? NoseShape.OGIVE : noseShape;
		this.finenessRatio = sanitizeNonNegative(finenessRatio);
		this.referenceArea = sanitizeNonNegative(referenceArea);
		this.boattailLength = sanitizeNonNegative(boattailLength);
		this.boattailBaseDiameter = sanitizeNonNegative(boattailBaseDiameter);

		List<FinGeom> normalizedFinSets = normalizeFinSets(
				finSets,
				finCount,
				finRootChord,
				finTipChord,
				finSpan,
				finThickness,
				finSweepAngle,
				finWettedArea,
				finAxialPosition);
		this.finSets = normalizedFinSets;

		if (!normalizedFinSets.isEmpty()) {
			int aggregateCount = 0;
			double totalPlanformArea = 0.0;
			double totalWettedArea = 0.0;
			double weightedRoot = 0.0;
			double weightedTip = 0.0;
			double weightedSpan = 0.0;
			double weightedThickness = 0.0;
			double weightedSweepAngle = 0.0;
			double weightedAxial = 0.0;
			for (FinGeom finSet : normalizedFinSets) {
				double weight = Math.max(finSet.totalPlanformArea(), 1e-12);
				totalPlanformArea += weight;
				totalWettedArea += finSet.wettedArea();
				aggregateCount += finSet.count();
				weightedRoot += weight * finSet.rootChord();
				weightedTip += weight * finSet.tipChord();
				weightedSpan += weight * finSet.span();
				weightedThickness += weight * finSet.thickness();
				double sweepAngle = finSet.span() > 1e-9 ? Math.atan2(finSet.sweepLength(), finSet.span()) : 0.0;
				weightedSweepAngle += weight * sweepAngle;
				weightedAxial += weight * finSet.axialPosition();
			}
			this.finCount = aggregateCount;
			this.finRootChord = weightedAverage(weightedRoot, totalPlanformArea);
			this.finTipChord = weightedAverage(weightedTip, totalPlanformArea);
			this.finSpan = weightedAverage(weightedSpan, totalPlanformArea);
			this.finThickness = weightedAverage(weightedThickness, totalPlanformArea);
			this.finSweepAngle = weightedAverage(weightedSweepAngle, totalPlanformArea);
			this.finWettedArea = totalWettedArea;
			this.finAxialPosition = weightedAverage(weightedAxial, totalPlanformArea);
		} else {
			this.finCount = Math.max(0, finCount);
			this.finRootChord = sanitizeNonNegative(finRootChord);
			this.finTipChord = sanitizeNonNegative(finTipChord);
			this.finSpan = sanitizeNonNegative(finSpan);
			this.finThickness = sanitizeNonNegative(finThickness);
			this.finSweepAngle = Double.isFinite(finSweepAngle) ? finSweepAngle : 0.0;
			this.finWettedArea = sanitizeNonNegative(finWettedArea);
			this.finAxialPosition = sanitizeNonNegative(finAxialPosition);
		}

		this.motorExitArea = sanitizeNonNegative(motorExitArea);
		this.surfaceRoughness = sanitizeNonNegative(surfaceRoughness);
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
		this(bodyLength, maxDiameter, baseArea, wetArea, wetArea, noseLength, noseShape,
				finenessRatio, referenceArea, boattailLength, boattailBaseDiameter,
				null, finCount, finRootChord, finTipChord, finSpan, finThickness, finSweepAngle,
				finWettedArea, finAxialPosition, motorExitArea, surfaceRoughness);
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

		v[7] = (referenceArea > 1e-12) ? Math.min(finWettedArea / referenceArea, 5.0) / 5.0 : 0.0;

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

	private static List<FinGeom> normalizeFinSets(
			List<FinGeom> finSets,
			int finCount,
			double finRootChord,
			double finTipChord,
			double finSpan,
			double finThickness,
			double finSweepAngle,
			double finWettedArea,
			double finAxialPosition) {
		List<FinGeom> normalized = new ArrayList<>();
		if (finSets != null) {
			for (FinGeom finSet : finSets) {
				if (finSet == null || finSet.count() <= 0) {
					continue;
				}
				normalized.add(finSet);
			}
		}
		if (normalized.isEmpty() && finCount > 0) {
			normalized.add(new FinGeom(
					finCount,
					finRootChord,
					finTipChord,
					finSpan,
					finThickness,
					sweepLengthFromAngle(finSweepAngle, finSpan),
					finWettedArea,
					finAxialPosition,
					"AGGREGATE",
					false));
		}
		return Collections.unmodifiableList(normalized);
	}

	private static double sanitizeNonNegative(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	private static double weightedAverage(double weightedValue, double totalWeight) {
		if (!(totalWeight > 0.0)) {
			return 0.0;
		}
		double average = weightedValue / totalWeight;
		return Double.isFinite(average) ? average : 0.0;
	}

	private static double sweepLengthFromAngle(double sweepAngle, double span) {
		if (!Double.isFinite(sweepAngle) || !Double.isFinite(span) || span <= 0.0) {
			return 0.0;
		}
		return Math.tan(sweepAngle) * span;
	}
}
