package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import java.util.Set;

/** Strict validator: discontinuities must be explicit and no thermodynamic state is repaired. */
public final class EdgeStateHistoryValidator {
	private static final Set<String> EVENTS = Set.of("NONE", "SHOCK", "EXPANSION_TERMINATION", "GEOMETRY_CORNER",
			"COMPONENT_JUNCTION", "ROUGHNESS_STEP", "THERMAL_STEP", "FORCED_TRIP", "PROTUBERANCE_WAKE_ENTRY",
			"PROTUBERANCE_WAKE_EXIT", "USER_DEFINED_EVENT");
	public void validate(SurfaceTrack track, BoundaryLayerConfiguration configuration) {
		BoundaryLayerStation previous = null;
		for (BoundaryLayerStation station : track.stations()) {
			if (!EVENTS.contains(station.event())) fail(BoundaryLayerException.Reason.UNREGISTERED_DISCONTINUITY, "unknown event " + station.event());
			if (station.wallTemperatureK() <= 0) fail(BoundaryLayerException.Reason.MISSING_WALL_STATE, "missing wall temperature");
			if (station.roughnessM() < 0) fail(BoundaryLayerException.Reason.MISSING_ROUGHNESS_STATE, "missing roughness");
			double expectedMach = station.streamwiseVelocityMS() / station.edgeState().speedOfSoundMS();
			if (Math.abs(expectedMach - station.mach()) > 0.02 * Math.max(1, station.mach()))
				fail(BoundaryLayerException.Reason.THERMODYNAMIC_INCONSISTENCY, "Mach, velocity and sound speed disagree");
			if (previous != null) {
				if (station.sM() < previous.sM() || station.sM() == previous.sM() && "NONE".equals(station.event()))
					fail(BoundaryLayerException.Reason.NON_MONOTONE_TRACK, "s must increase strictly except at an explicit event");
				double t0rel = relative(station.edgeState().totalState().temperatureK(), previous.edgeState().totalState().temperatureK());
				if (t0rel > configuration.totalStateRelativeTolerance() && "NONE".equals(station.event()))
					fail(BoundaryLayerException.Reason.THERMODYNAMIC_INCONSISTENCY, "unregistered total-temperature jump");
				if ("SHOCK".equals(station.event()) && station.edgeState().totalState().pressurePa() > previous.edgeState().totalState().pressurePa() * (1 + configuration.totalStateRelativeTolerance()))
					fail(BoundaryLayerException.Reason.THERMODYNAMIC_INCONSISTENCY, "total pressure increased across shock");
				double pressureJump = relative(station.pressurePa(), previous.pressurePa());
				if (pressureJump > 0.25 && "NONE".equals(station.event())) fail(BoundaryLayerException.Reason.UNREGISTERED_DISCONTINUITY, "large pressure jump without event");
			}
			previous = station;
		}
	}
	private static double relative(double a, double b) { return Math.abs(a - b) / Math.max(Math.abs(b), 1e-12); }
	private static void fail(BoundaryLayerException.Reason reason, String message) { throw new BoundaryLayerException(reason, message); }
}
