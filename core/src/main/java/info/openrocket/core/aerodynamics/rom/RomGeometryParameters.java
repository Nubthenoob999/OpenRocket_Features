package info.openrocket.core.aerodynamics.rom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.position.AxialMethod;

/**
 * Bridge class: extracts geometry parameters from the live OpenRocket component
 * tree and packages them for use with the rom.core physics models via
 * {@link info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput}.
 *
 * <p>To convert: call {@link #toRomGeometryInput()} after constructing via
 * {@link #fromRocket(FlightConfiguration)}.
 */
public class RomGeometryParameters {

	private static final Logger log = LoggerFactory.getLogger(RomGeometryParameters.class);
	private static final double DEFAULT_SURFACE_ROUGHNESS = 6.4e-6;

	// Body dimensions (SI units throughout)
	public double bodyLength;          // m - total length nose tip to base
	public double maxDiameter;         // m - maximum body diameter
	public double baseArea;            // m^2 - exposed aft base area
	public double wetArea;             // m^2 - body wetted surface area only
	public double bodyWetArea;         // m^2 - body wetted surface area only
	public double noseLength;          // m - nose cone length
	public NoseShape noseShape;        // enum: CONICAL, OGIVE, VON_KARMAN, PARABOLIC, ELLIPSOID
	public double finessRatio;         // bodyLength / maxDiameter

	// Boattail / transition (zero if absent)
	public double boattailLength;      // m
	public double boattailBaseDiameter;// m - aft diameter of the aft-most shrinking transition

	// Fin geometry (legacy aggregate fields derived from finSets)
	public List<FinGeom> finSets = Collections.emptyList();
	public int finCount;               // total number of fins
	public double finRootChord;        // m
	public double finTipChord;         // m
	public double finSpan;             // m - semi-span from body
	public double finThickness;        // m
	public double finSweepAngle;       // radians - weighted leading edge sweep
	public double finWettedArea;       // m^2 - all fins, both sides

	// Motor exit geometry (for plume-on base drag)
	public double motorExitDiameter;   // m - 0 if no motor selected
	public double motorExitArea;       // m^2

	// Surface roughness
	public double surfaceRoughness;    // m - equivalent sand-grain k_s, default 6.4e-6 (paint)

	// Derived reference quantities (compute in constructor or factory)
	public double referenceArea;       // m^2 = pi(maxDiameter/2)^2
	public List<String> geometryWarnings = Collections.emptyList();

	public enum NoseShape {
		CONICAL, OGIVE, VON_KARMAN, PARABOLIC, ELLIPSOID, HAACK
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

		public double totalPlanformArea() {
			return Math.max(0.0, wettedArea) / 2.0;
		}

		public double meanChord() {
			if (count > 0 && span > 1e-9) {
				double meanChord = totalPlanformArea() / (count * span);
				if (Double.isFinite(meanChord) && meanChord > 0.0) {
					return meanChord;
				}
			}
			return Math.max(0.0, 0.5 * (rootChord + tipChord));
		}

		public double sweepAngle() {
			return span > 1e-9 ? Math.atan2(sweepLength, span) : 0.0;
		}

		private static double sanitizeNonNegative(double value) {
			return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
		}
	}

	/** Factory: populate from the live OpenRocket component tree. */
	public static RomGeometryParameters fromRocket(FlightConfiguration config) {
		if (config == null) {
			throw new IllegalArgumentException("FlightConfiguration must not be null");
		}

		RomGeometryParameters g = new RomGeometryParameters();
		g.noseShape = NoseShape.OGIVE;
		g.surfaceRoughness = DEFAULT_SURFACE_ROUGHNESS;

		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxRadius = 0.0;
		double roughness = g.surfaceRoughness;
		double bodyWetArea = 0.0;
		double aftMostAxialEnd = Double.NEGATIVE_INFINITY;
		double aftMostBaseRadius = 0.0;
		double aftMostBoattailEnd = Double.NEGATIVE_INFINITY;
		double aftMostBoattailBaseDiameter = 0.0;
		double boattailLength = 0.0;
		List<FinGeom> finSets = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		for (RocketComponent component : config.getAllComponents()) {
			if (component instanceof SymmetricComponent symmetric) {
				double x0 = component.getAxialOffset(AxialMethod.ABSOLUTE);
				double x1 = x0 + component.getLength();
				minX = Math.min(minX, x0);
				maxX = Math.max(maxX, x1);
				maxRadius = Math.max(maxRadius, Math.max(symmetric.getForeRadius(), symmetric.getAftRadius()));
				bodyWetArea += Math.max(0.0, symmetric.getComponentWetArea());

				if (x1 >= aftMostAxialEnd - 1e-9) {
					aftMostAxialEnd = x1;
					aftMostBaseRadius = Math.max(0.0, symmetric.getAftRadius());
				}

				if (component instanceof NoseCone noseCone) {
					g.noseLength = Math.max(g.noseLength, noseCone.getLength());
					g.noseShape = mapNoseShape(noseCone.getShapeType());
				} else if (component instanceof Transition transition
						&& transition.getAftRadius() < transition.getForeRadius()) {
					boattailLength += Math.max(0.0, transition.getLength());
					if (x1 >= aftMostBoattailEnd - 1e-9) {
						aftMostBoattailEnd = x1;
						aftMostBoattailBaseDiameter = 2.0 * Math.max(0.0, transition.getAftRadius());
					}
				}
			}

			if (component instanceof ExternalComponent external) {
				roughness = Math.max(roughness, mapFinishToRoughness(external.getFinish()));
			}

			if (component instanceof FinSet fins) {
				FinGeom finGeom = extractFinGeometry(fins, component, warnings);
				if (finGeom != null) {
					finSets.add(finGeom);
				}
			}
		}

		if (Double.isFinite(minX) && Double.isFinite(maxX) && maxX > minX) {
			g.bodyLength = maxX - minX;
		}
		g.maxDiameter = 2.0 * maxRadius;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.bodyWetArea = bodyWetArea;
		g.wetArea = bodyWetArea;
		g.finessRatio = (g.maxDiameter > 0.0) ? (g.bodyLength / g.maxDiameter) : 0.0;
		g.surfaceRoughness = roughness;
		g.boattailLength = boattailLength;
		g.baseArea = Math.PI * aftMostBaseRadius * aftMostBaseRadius;
		if (!(g.baseArea > 0.0)) {
			g.baseArea = g.referenceArea;
		}
		if (aftMostBoattailBaseDiameter > 0.0) {
			g.boattailBaseDiameter = aftMostBoattailBaseDiameter;
		} else if (aftMostBaseRadius > 0.0 && (2.0 * aftMostBaseRadius) + 1e-9 < g.maxDiameter) {
			g.boattailBaseDiameter = 2.0 * aftMostBaseRadius;
		}

		g.finSets = Collections.unmodifiableList(new ArrayList<>(finSets));
		populateAggregateFinGeometry(g, finSets);

		// Motor diameter corresponds to casing OD, not nozzle exit diameter.
		// Use a conservative default until explicit nozzle geometry is available.
		g.motorExitArea = 0.0;
		g.motorExitDiameter = 0.0;

		if (g.motorExitArea > g.baseArea + 1e-12) {
			String warning = String.format(
					Locale.ROOT,
					"Motor exit area %.6f m^2 exceeds base area %.6f m^2; plume-on base drag will clamp to zero.",
					g.motorExitArea,
					g.baseArea);
			warnings.add(warning);
			log.warn(warning);
		}

		g.geometryWarnings = Collections.unmodifiableList(new ArrayList<>(warnings));
		return g;
	}

	/** Stable SHA-256 hash of all geometry fields for cache invalidation. */
	public String geometryHash() {
		StringBuilder data = new StringBuilder(1024);
		data.append(AeroGridEvaluator4D.ROM_PHYSICS_VERSION).append('|');
		append(data, bodyLength);
		append(data, maxDiameter);
		append(data, baseArea);
		append(data, wetArea);
		append(data, bodyWetArea);
		append(data, noseLength);
		data.append(noseShape != null ? noseShape.name() : "null").append('|');
		append(data, finessRatio);
		append(data, boattailLength);
		append(data, boattailBaseDiameter);
		data.append(finCount).append('|');
		append(data, finRootChord);
		append(data, finTipChord);
		append(data, finSpan);
		append(data, finThickness);
		append(data, finSweepAngle);
		append(data, finWettedArea);
		append(data, motorExitDiameter);
		append(data, motorExitArea);
		append(data, surfaceRoughness);
		append(data, referenceArea);
		data.append(finSets.size()).append('|');
		for (FinGeom finSet : finSets) {
			data.append(finSet.count()).append('|');
			append(data, finSet.rootChord());
			append(data, finSet.tipChord());
			append(data, finSet.span());
			append(data, finSet.thickness());
			append(data, finSet.sweepLength());
			append(data, finSet.wettedArea());
			append(data, finSet.axialPosition());
			data.append(finSet.finType()).append('|');
			data.append(finSet.thicknessFallbackUsed() ? '1' : '0').append('|');
		}

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(data.toString().getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format(Locale.ROOT, "%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}

	public String geometryHash(RomSurfaceMode mode) {
		if (mode == null) {
			return geometryHash();
		}
		return mode.tagGeometryHash(geometryHash());
	}

	public List<FinGeom> resolvedFinSets() {
		if (finSets != null && !finSets.isEmpty()) {
			return finSets;
		}
		if (finCount <= 0) {
			return Collections.emptyList();
		}
		return List.of(new FinGeom(
				finCount,
				finRootChord,
				finTipChord,
				finSpan,
				finThickness,
				sweepLengthFromAngle(finSweepAngle, finSpan),
				finWettedArea,
				Math.max(0.0, bodyLength - finRootChord - Math.max(boattailLength, 0.0)),
				"AGGREGATE",
				false));
	}

	private static void append(StringBuilder sb, double value) {
		sb.append(String.format(Locale.ROOT, "%.12e", value)).append('|');
	}

	public info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput toRomGeometryInput() {
		info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape coreShape;
		switch (this.noseShape) {
			case CONICAL:
				coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.CONICAL;
				break;
			case OGIVE:
				coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.OGIVE;
				break;
			case VON_KARMAN:
				coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.VON_KARMAN;
				break;
			case PARABOLIC:
				coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.PARABOLIC;
				break;
			case ELLIPSOID:
				coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.ELLIPSOID;
				break;
			case HAACK:
				coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.HAACK;
				break;
			default:
				coreShape = info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.NoseShape.OGIVE;
		}

		double finAxialEst = weightedFinAxialPosition();
		if (!(finAxialEst > 0.0)) {
			finAxialEst = Math.max(0.0,
					this.bodyLength - this.finRootChord - Math.max(this.boattailLength, 0.0));
		}

		List<info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.FinGeom> coreFinSets = new ArrayList<>(
				resolvedFinSets().size());
		for (FinGeom finSet : resolvedFinSets()) {
			coreFinSets.add(new info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput.FinGeom(
					finSet.count(),
					finSet.rootChord(),
					finSet.tipChord(),
					finSet.span(),
					finSet.thickness(),
					finSet.sweepLength(),
					finSet.wettedArea(),
					finSet.axialPosition(),
					finSet.finType(),
					finSet.thicknessFallbackUsed()));
		}

		return new info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput(
				this.bodyLength,
				this.maxDiameter,
				this.baseArea,
				this.bodyWetArea,
				this.wetArea,
				this.noseLength,
				coreShape,
				this.finessRatio,
				this.referenceArea,
				this.boattailLength,
				this.boattailBaseDiameter,
				coreFinSets,
				this.finCount,
				this.finRootChord,
				this.finTipChord,
				this.finSpan,
				this.finThickness,
				this.finSweepAngle,
				this.finWettedArea,
				finAxialEst,
				this.motorExitArea,
				this.surfaceRoughness);
	}

	private static FinGeom extractFinGeometry(FinSet fins, RocketComponent component, List<String> warnings) {
		int count = Math.max(0, fins.getFinCount());
		if (count <= 0) {
			return null;
		}

		double span = sanitizeNonNegative(fins.getSpan());
		double planformAreaPerFin = sanitizeNonNegative(fins.getPlanformArea());
		double axialPosition = sanitizeNonNegative(component.getAxialOffset(AxialMethod.ABSOLUTE));
		double rootChord = sanitizeNonNegative(fins.getLength());
		double tipChord = 0.0;
		double sweepLength = 0.0;
		if (fins instanceof TrapezoidFinSet trapezoid) {
			rootChord = sanitizeNonNegative(trapezoid.getRootChord());
			tipChord = sanitizeNonNegative(trapezoid.getTipChord());
			sweepLength = Double.isFinite(trapezoid.getSweep()) ? trapezoid.getSweep() : 0.0;
		} else if (span > 1e-9 && planformAreaPerFin > 1e-12) {
			double meanChord = planformAreaPerFin / span;
			tipChord = Math.max(0.0, 2.0 * meanChord - rootChord);
		}

		double thickness = fins.getThickness();
		boolean thicknessFallbackUsed = false;
		if (!Double.isFinite(thickness) || thickness <= 0.0) {
			thickness = 0.0;
			thicknessFallbackUsed = true;
			String warning = String.format(
					Locale.ROOT,
					"Fin thickness unresolved for %s at x=%.4f m; using zero-thickness conservative fallback.",
					fins.getClass().getSimpleName(),
					axialPosition);
			warnings.add(warning);
			log.warn(warning);
		}

		double totalWettedArea = 2.0 * planformAreaPerFin * count;
		if (!(totalWettedArea > 0.0) && span > 1e-9 && rootChord > 0.0) {
			double fallbackPlanformAreaPerFin = 0.5 * (rootChord + tipChord) * span;
			totalWettedArea = 2.0 * fallbackPlanformAreaPerFin * count;
		}

		return new FinGeom(
				count,
				rootChord,
				tipChord,
				span,
				thickness,
				sweepLength,
				totalWettedArea,
				axialPosition,
				fins.getClass().getSimpleName(),
				thicknessFallbackUsed);
	}

	private static void populateAggregateFinGeometry(RomGeometryParameters g, List<FinGeom> finSets) {
		if (finSets == null || finSets.isEmpty()) {
			g.finCount = 0;
			g.finRootChord = 0.0;
			g.finTipChord = 0.0;
			g.finSpan = 0.0;
			g.finThickness = 0.0;
			g.finSweepAngle = 0.0;
			g.finWettedArea = 0.0;
			return;
		}

		int aggregateCount = 0;
		double totalPlanformArea = 0.0;
		double totalWettedArea = 0.0;
		double weightedRoot = 0.0;
		double weightedTip = 0.0;
		double weightedSpan = 0.0;
		double weightedThickness = 0.0;
		double weightedSweepAngle = 0.0;

		for (FinGeom finSet : finSets) {
			double weight = Math.max(finSet.totalPlanformArea(), 1e-12);
			totalPlanformArea += weight;
			totalWettedArea += finSet.wettedArea();
			aggregateCount += finSet.count();
			weightedRoot += weight * finSet.rootChord();
			weightedTip += weight * finSet.tipChord();
			weightedSpan += weight * finSet.span();
			weightedThickness += weight * finSet.thickness();
			weightedSweepAngle += weight * finSet.sweepAngle();
		}

		g.finCount = aggregateCount;
		g.finRootChord = weightedAverage(weightedRoot, totalPlanformArea);
		g.finTipChord = weightedAverage(weightedTip, totalPlanformArea);
		g.finSpan = weightedAverage(weightedSpan, totalPlanformArea);
		g.finThickness = weightedAverage(weightedThickness, totalPlanformArea);
		g.finSweepAngle = weightedAverage(weightedSweepAngle, totalPlanformArea);
		g.finWettedArea = totalWettedArea;
	}

	private double weightedFinAxialPosition() {
		if (finSets == null || finSets.isEmpty()) {
			return 0.0;
		}
		double weightedAxial = 0.0;
		double totalPlanformArea = 0.0;
		for (FinGeom finSet : finSets) {
			double weight = Math.max(finSet.totalPlanformArea(), 1e-12);
			weightedAxial += weight * finSet.axialPosition();
			totalPlanformArea += weight;
		}
		return weightedAverage(weightedAxial, totalPlanformArea);
	}

	private static NoseShape mapNoseShape(Transition.Shape shape) {
		if (shape == null) {
			return NoseShape.OGIVE;
		}
		String name = shape.name();
		if ("VON_KARMAN".equals(name) || "VONKARMAN".equals(name)) {
			return NoseShape.VON_KARMAN;
		}
		if ("CONICAL".equals(name)) {
			return NoseShape.CONICAL;
		}
		if ("OGIVE".equals(name)) {
			return NoseShape.OGIVE;
		}
		if ("HAACK".equals(name)) {
			return NoseShape.HAACK;
		}
		if ("PARABOLIC".equals(name)) {
			return NoseShape.PARABOLIC;
		}
		if ("ELLIPSOID".equals(name)) {
			return NoseShape.ELLIPSOID;
		}
		return NoseShape.OGIVE;
	}

	private static double mapFinishToRoughness(ExternalComponent.Finish finish) {
		if (finish == null) {
			return DEFAULT_SURFACE_ROUGHNESS;
		}
		switch (finish) {
			case ROUGH:
				return 500e-6;
			case ROUGHUNFINISHED:
				return 250e-6;
			case UNFINISHED:
				return 60e-6;
			case NORMAL:
				return DEFAULT_SURFACE_ROUGHNESS;
			case SMOOTH:
			case OPTIMUM:
			case POLISHED:
				return 2e-6;
			case FINISHPOLISHED:
			case MIRROR:
				return 0.5e-6;
			default:
				return DEFAULT_SURFACE_ROUGHNESS;
		}
	}

	private static double weightedAverage(double weightedValue, double totalWeight) {
		if (!(totalWeight > 0.0)) {
			return 0.0;
		}
		double average = weightedValue / totalWeight;
		return Double.isFinite(average) ? average : 0.0;
	}

	private static double sanitizeNonNegative(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	private static double sweepLengthFromAngle(double sweepAngle, double span) {
		if (!Double.isFinite(sweepAngle) || !Double.isFinite(span) || span <= 0.0) {
			return 0.0;
		}
		return Math.tan(sweepAngle) * span;
	}
}
