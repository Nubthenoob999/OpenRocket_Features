package info.openrocket.core.aerodynamics.rom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import info.openrocket.core.motor.Motor;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.rocketcomponent.BodyTube;
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

	// Body dimensions (SI units throughout)
	public double bodyLength;          // m - total length nose tip to base
	public double maxDiameter;         // m - maximum body diameter
	public double baseArea;            // m^2 - pi(d/2)^2
	public double wetArea;             // m^2 - total wetted surface area
	public double noseLength;          // m - nose cone length
	public NoseShape noseShape;        // enum: CONICAL, OGIVE, VON_KARMAN, PARABOLIC, ELLIPSOID
	public double finessRatio;         // bodyLength / maxDiameter

	// Boattail / transition (zero if absent)
	public double boattailLength;      // m
	public double boattailBaseDiameter;// m - aft diameter if boattailed

	// Fin geometry (aggregate for all fin sets)
	public int finCount;               // total number of fins
	public double finRootChord;        // m
	public double finTipChord;         // m
	public double finSpan;             // m - semi-span from body
	public double finThickness;        // m
	public double finSweepAngle;       // radians - leading edge sweep from perpendicular
	public double finWettedArea;       // m^2 - one fin, both sides

	// Motor exit geometry (for plume-on base drag)
	public double motorExitDiameter;   // m - 0 if no motor selected
	public double motorExitArea;       // m^2

	// Surface roughness
	public double surfaceRoughness;    // m - equivalent sand-grain k_s, default 6.4e-6 (paint)

	// Derived reference quantities (compute in constructor or factory)
	public double referenceArea;       // m^2 = pi(maxDiameter/2)^2

	public enum NoseShape {
		CONICAL, OGIVE, VON_KARMAN, PARABOLIC, ELLIPSOID, HAACK
	}

	/** Factory: populate from the live OpenRocket component tree. */
	public static RomGeometryParameters fromRocket(FlightConfiguration config) {
		if (config == null) {
			throw new IllegalArgumentException("FlightConfiguration must not be null");
		}

		RomGeometryParameters g = new RomGeometryParameters();
		g.noseShape = NoseShape.OGIVE;
		g.surfaceRoughness = 6.4e-6;

		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxRadius = 0.0;
		double roughness = g.surfaceRoughness;

		int finCount = 0;
		double finRoot = 0.0;
		double finTip = 0.0;
		double finSpan = 0.0;
		double finThick = 0.0;
		double finSweep = 0.0;
		double finWetArea = 0.0;
		boolean primaryFinSetCaptured = false;

		for (RocketComponent component : config.getAllComponents()) {
			if (component instanceof SymmetricComponent) {
				SymmetricComponent s = (SymmetricComponent) component;
				double x0 = component.getAxialOffset(AxialMethod.ABSOLUTE);
				double x1 = x0 + component.getLength();
				minX = Math.min(minX, x0);
				maxX = Math.max(maxX, x1);
				maxRadius = Math.max(maxRadius, Math.max(s.getForeRadius(), s.getAftRadius()));
				g.wetArea += Math.max(0.0, s.getComponentWetArea());
			}

			if (component instanceof ExternalComponent) {
				ExternalComponent external = (ExternalComponent) component;
				roughness = Math.max(roughness, mapFinishToRoughness(external.getFinish()));
			}

			if (component instanceof NoseCone) {
				NoseCone noseCone = (NoseCone) component;
				g.noseLength = Math.max(g.noseLength, noseCone.getLength());
				g.noseShape = mapNoseShape(noseCone.getShapeType());
			}

			if (component instanceof BodyTube) {
				// Body tube wet area is already included via SymmetricComponent#getComponentWetArea.
			}

			if ((component instanceof Transition) && !(component instanceof NoseCone)) {
				Transition transition = (Transition) component;
				if (transition.getAftRadius() < transition.getForeRadius()) {
					g.boattailLength += transition.getLength();
					g.boattailBaseDiameter = Math.max(g.boattailBaseDiameter, 2.0 * transition.getAftRadius());
				}
			}

			if (component instanceof FinSet) {
				FinSet fins = (FinSet) component;
				finCount += fins.getFinCount();

				if (!primaryFinSetCaptured || finRoot == 0.0) {
					if (component instanceof TrapezoidFinSet) {
						TrapezoidFinSet trapezoid = (TrapezoidFinSet) component;
						finRoot = trapezoid.getRootChord();
						finTip = trapezoid.getTipChord();
						finSpan = trapezoid.getSpan();
						finThick = trapezoid.getThickness();
						finSweep = trapezoid.getSweepAngle();
					} else {
						finRoot = fins.getLength();
						finTip = 0.0;
						finSpan = fins.getSpan();
						finThick = 0.003;
						finSweep = 0.0;
					}
					primaryFinSetCaptured = true;
				}

				double cMean = (finRoot + finTip) / 2.0;
				finWetArea += cMean * finSpan * 2.0 * fins.getFinCount();
			}
		}

		if (Double.isFinite(minX) && Double.isFinite(maxX) && maxX > minX) {
			g.bodyLength = maxX - minX;
		}
		g.maxDiameter = 2.0 * maxRadius;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.baseArea = g.referenceArea;
		g.finessRatio = (g.maxDiameter > 0.0) ? (g.bodyLength / g.maxDiameter) : 0.0;
		g.surfaceRoughness = roughness;

		g.finCount = finCount;
		g.finRootChord = finRoot;
		g.finTipChord = finTip;
		g.finSpan = finSpan;
		g.finThickness = finThick;
		g.finSweepAngle = finSweep;
		g.finWettedArea = finWetArea;

		double totalExitArea = 0.0;
		for (MotorConfiguration motorConfiguration : config.getActiveMotors()) {
			Motor motor = motorConfiguration.getMotor();
			if (motor == null) {
				continue;
			}
			double d = Math.max(0.0, motor.getDiameter());
			double area = Math.PI * Math.pow(d / 2.0, 2.0);
			totalExitArea += area * Math.max(1, motorConfiguration.getMotorCount());
		}
		g.motorExitArea = totalExitArea;
		g.motorExitDiameter = (totalExitArea > 0.0) ? Math.sqrt(4.0 * totalExitArea / Math.PI) : 0.0;

		return g;
	}

	/** Stable SHA-256 hash of all geometry fields for cache invalidation. */
	public String geometryHash() {
		StringBuilder data = new StringBuilder(512);
		append(data, bodyLength);
		append(data, maxDiameter);
		append(data, baseArea);
		append(data, wetArea);
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

		double finAxialEst = Math.max(0.0,
				this.bodyLength - this.finRootChord - Math.max(this.boattailLength, 0.0));

		return new info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput(
				this.bodyLength,
				this.maxDiameter,
				this.baseArea,
				this.wetArea,
				this.noseLength,
				coreShape,
				this.finessRatio,
				this.referenceArea,
				this.boattailLength,
				this.boattailBaseDiameter,
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

	private static NoseShape mapNoseShape(Transition.Shape shape) {
		if (shape == null) {
			return NoseShape.OGIVE;
		}
		String name = shape.name();
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
			return 6.4e-6;
		}
		switch (finish) {
			case ROUGH:
				return 500e-6;
			case ROUGHUNFINISHED:
				return 250e-6;
			case UNFINISHED:
				return 60e-6;
			case NORMAL:
				return 6.4e-6;
			case SMOOTH:
				return 2e-6;
			case OPTIMUM:
				return 2e-6;
			case POLISHED:
				return 2e-6;
			case FINISHPOLISHED:
				return 0.5e-6;
			case MIRROR:
				return 0.5e-6;
			default:
				return 6.4e-6;
		}
	}
}
