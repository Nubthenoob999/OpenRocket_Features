package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowEvent;
import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.SurfaceState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;
import info.openrocket.core.aerodynamics.physicsaero.thermal.RecoveryTemperatureModel;
import info.openrocket.core.util.Coordinate;

/** Adapts Phase-2 body edge histories without introducing another flow-state type. */
public final class SurfaceTrackBuilder {
	/**
	 * Marks the first station whose freestream run Reynolds number reaches a
	 * measured or otherwise prescribed transition value.  The Reynolds number
	 * is based on surface run length, matching free-flight transition data such
	 * as {@code R_T = rho_inf U_inf s / mu_inf}.
	 */
	public SurfaceTrack withForcedTransitionReynolds(SurfaceTrack track, double transitionReynolds,
			double freestreamDensityKgM3, double freestreamVelocityMS, double freestreamViscosityPaS) {
		if (!(transitionReynolds > 0) || !(freestreamDensityKgM3 > 0)
				|| !(freestreamVelocityMS > 0) || !(freestreamViscosityPaS > 0)) {
			throw new IllegalArgumentException("invalid prescribed transition state");
		}
		double reynoldsPerM = freestreamDensityKgM3 * freestreamVelocityMS / freestreamViscosityPaS;
		double tripS = transitionReynolds / reynoldsPerM;
		int tripIndex = -1;
		for (int i = 0; i < track.stations().size(); i++) {
			if (track.stations().get(i).sM() * reynoldsPerM >= transitionReynolds) {
				tripIndex = i;
				break;
			}
		}
		if (tripIndex < 0) return track;
		List<BoundaryLayerStation> stations = new ArrayList<>(track.stations());
		BoundaryLayerStation station = stations.get(tripIndex);
		double tolerance = 1e-12 * Math.max(1, tripS);
		if (tripIndex > 0 && Math.abs(station.sM() - tripS) > tolerance
				&& "NONE".equals(station.event())) {
			BoundaryLayerStation before = stations.get(tripIndex - 1);
			double fraction = (tripS - before.sM()) / (station.sM() - before.sM());
			BoundaryLayerStation trip = interpolate(before, station, fraction, tripS);
			stations.add(tripIndex, trip);
			stations.set(tripIndex + 1, copyWithDelta(station, station.sM() - tripS));
			return new SurfaceTrack(track.componentId(), track.regionId(), track.mode(), stations,
					regionsWithInsertion(track.regions(), tripIndex), track.reducedConfidence());
		}
		String event = "NONE".equals(station.event()) ? "FORCED_TRIP" : station.event();
		stations.set(tripIndex, copy(station, station.wallTemperatureK(), true, event));
		return new SurfaceTrack(track.componentId(), track.regionId(), track.mode(), stations,
				track.regions(), track.reducedConfidence());
	}

	/** Applies an explicit component or test-article wall-temperature distribution. */
	public SurfaceTrack withPrescribedWallTemperature(SurfaceTrack track,
			DoubleUnaryOperator wallTemperatureAtXM) {
		if (wallTemperatureAtXM == null) throw new IllegalArgumentException("missing wall-temperature distribution");
		List<BoundaryLayerStation> stations = new ArrayList<>(track.stations().size());
		for (BoundaryLayerStation station : track.stations()) {
			double wallTemperatureK = wallTemperatureAtXM.applyAsDouble(station.positionM().x);
			if (!(wallTemperatureK > 0) || !Double.isFinite(wallTemperatureK)) {
				throw new IllegalArgumentException("invalid prescribed wall temperature");
			}
			stations.add(copy(station, wallTemperatureK, station.forcedTrip(), station.event()));
		}
		return new SurfaceTrack(track.componentId(), track.regionId(), track.mode(), stations,
				track.regions(), track.reducedConfidence());
	}

	/** Builds an independent fin-side/chordwise track; callers invoke this once per fin, side, and span station. */
	public SurfaceTrack fromPlanarHistory(String componentId, String regionId, List<SurfaceState> source,
			Coordinate originM, Coordinate tangent, double surfaceWidthM, double roughnessM,
			BoundaryLayerRegionType regionType, BoundaryLayerConfiguration configuration) {
		if (source.size() < 2 || surfaceWidthM <= 0) throw new IllegalArgumentException("planar history needs stations and width");
		List<BoundaryLayerStation> stations = new ArrayList<>(); double s = 0;
		for (int i=0;i<source.size();i++) {
			SurfaceState current=source.get(i), before=source.get(Math.max(0,i-1)), after=source.get(Math.min(source.size()-1,i+1));
			double ds=i==0?0:Math.abs(current.xM()-before.xM()); if(i>0)s+=ds;
			double span=Math.abs(after.xM()-before.xM()); double du=span>0?(after.staticState().velocityMS()-before.staticState().velocityMS())/span:0;
			double dp=span>0?(after.staticState().pressurePa()-before.staticState().pressurePa())/span:0;
			double wallT=configuration.wallMode()==info.openrocket.core.aerodynamics.physicsaero.thermal.WallThermalBoundary.ADIABATIC
					?new RecoveryTemperatureModel().recoveryTemperatureK(current.staticState().temperatureK(),current.staticState().mach(),configuration.gamma(),configuration.prandtl(),false)
					:current.staticState().temperatureK();
			Coordinate position=new Coordinate(originM.x+tangent.x*s,originM.y+tangent.y*s,originM.z+tangent.z*s);
			stations.add(new BoundaryLayerStation(s,ds,position,0,0,0,current.staticState().velocityMS(),0,du,dp,wallT,roughnessM,false,"NONE",current,tangent,surfaceWidthM));
		}
		return new SurfaceTrack(componentId,regionId,BoundaryLayerMode.PLANAR,stations,
				List.of(new BoundaryLayerRegion(regionId,regionType,0,stations.size()-1)),false);
	}

	public SurfaceTrack fromAxisymmetricHistory(String componentId, String regionId, AxisymmetricEdgeStateHistory history,
			double roughnessM, BoundaryLayerConfiguration configuration) {
		List<SurfaceState> source = history.states(); List<BoundaryLayerStation> stations = new ArrayList<>();
		double s = 0;
		for (int i = 0; i < source.size(); i++) {
			SurfaceState current = source.get(i), before = source.get(Math.max(0, i - 1)), after = source.get(Math.min(source.size() - 1, i + 1));
			double dx = i == 0 ? 0 : current.xM() - before.xM(), dr = i == 0 ? 0 : current.radiusM() - before.radiusM();
			double ds = Math.hypot(dx, dr); if (i > 0) s += ds;
			double spanX = after.xM() - before.xM(), spanR = after.radiusM() - before.radiusM();
			double spanS = Math.hypot(spanX, spanR), drds = spanS > 0 ? spanR / spanS : 0;
			double tangentX = spanS > 0 ? spanX / spanS : Math.cos(current.flowAngleRad());
			double tangentR = spanS > 0 ? spanR / spanS : Math.sin(current.flowAngleRad());
			double velocity = current.staticState().velocityMS();
			double du = spanS > 0 ? (after.staticState().velocityMS() - before.staticState().velocityMS()) / spanS : 0;
			double dp = spanS > 0 ? (after.staticState().pressurePa() - before.staticState().pressurePa()) / spanS : 0;
			double wallT = configuration.wallMode() == info.openrocket.core.aerodynamics.physicsaero.thermal.WallThermalBoundary.ADIABATIC
					? new RecoveryTemperatureModel().recoveryTemperatureK(current.staticState().temperatureK(), current.staticState().mach(), configuration.gamma(), configuration.prandtl(), false)
					: current.staticState().temperatureK();
			String event = eventAt(current.xM(), history.events());
			if (i > 0 && ds == 0 && "NONE".equals(event)) event = "COMPONENT_JUNCTION";
			stations.add(new BoundaryLayerStation(s, ds, new Coordinate(current.xM(), current.radiusM(), 0), current.radiusM(),
					drds, 0, velocity, 0, du, dp, wallT, roughnessM, "FORCED_TRIP".equals(event), event,
					current, new Coordinate(tangentX, tangentR, 0), 2 * Math.PI * Math.max(current.radiusM(), 1e-12)));
		}
		List<BoundaryLayerRegion> regions = List.of(new BoundaryLayerRegion(regionId, BoundaryLayerRegionType.GENERIC, 0, stations.size() - 1));
		return new SurfaceTrack(componentId, regionId, BoundaryLayerMode.AXISYMMETRIC, stations, regions, false);
	}
	private String eventAt(double x, List<FlowEvent> events) {
		for (FlowEvent event : events) if (Math.abs(event.xM() - x) <= 1e-9 * Math.max(1, Math.abs(x)))
			return event.getClass().getSimpleName().contains("Shock") ? "SHOCK" : "EXPANSION_TERMINATION";
		return "NONE";
	}

	private static BoundaryLayerStation copy(BoundaryLayerStation station, double wallTemperatureK,
			boolean forcedTrip, String event) {
		return new BoundaryLayerStation(station.sM(), station.deltaSM(), station.positionM(), station.radiusM(),
				station.radiusGradient(), station.curvaturePerM(), station.streamwiseVelocityMS(),
				station.crossflowVelocityMS(), station.velocityGradientPerS(), station.pressureGradientPaM(),
				wallTemperatureK, station.roughnessM(), forcedTrip, event, station.edgeState(),
				station.streamwiseTangent(), station.areaWidthM());
	}

	private static BoundaryLayerStation copyWithDelta(BoundaryLayerStation station, double deltaSM) {
		return new BoundaryLayerStation(station.sM(), deltaSM, station.positionM(), station.radiusM(),
				station.radiusGradient(), station.curvaturePerM(), station.streamwiseVelocityMS(),
				station.crossflowVelocityMS(), station.velocityGradientPerS(), station.pressureGradientPaM(),
				station.wallTemperatureK(), station.roughnessM(), station.forcedTrip(), station.event(),
				station.edgeState(), station.streamwiseTangent(), station.areaWidthM());
	}

	private static BoundaryLayerStation interpolate(BoundaryLayerStation a, BoundaryLayerStation b,
			double fraction, double sM) {
		if (!(fraction > 0 && fraction < 1)) throw new IllegalArgumentException("invalid transition interpolation");
		SurfaceState ae = a.edgeState(), be = b.edgeState();
		GasState gas = new GasState(lerp(ae.staticState().mach(), be.staticState().mach(), fraction),
				lerp(ae.staticState().pressurePa(), be.staticState().pressurePa(), fraction),
				lerp(ae.staticState().temperatureK(), be.staticState().temperatureK(), fraction),
				lerp(ae.staticState().densityKgM3(), be.staticState().densityKgM3(), fraction),
				lerp(ae.staticState().velocityMS(), be.staticState().velocityMS(), fraction));
		TotalState total = new TotalState(lerp(ae.totalState().pressurePa(), be.totalState().pressurePa(), fraction),
				lerp(ae.totalState().temperatureK(), be.totalState().temperatureK(), fraction),
				lerp(ae.totalState().densityKgM3(), be.totalState().densityKgM3(), fraction));
		double xM = lerp(a.positionM().x, b.positionM().x, fraction);
		double radiusM = lerp(a.radiusM(), b.radiusM(), fraction);
		SurfaceState edge = new SurfaceState(xM, radiusM, gas, total,
				lerp(ae.flowAngleRad(), be.flowAngleRad(), fraction),
				lerp(ae.pressureCoefficient(), be.pressureCoefficient(), fraction),
				lerp(ae.speedOfSoundMS(), be.speedOfSoundMS(), fraction),
				lerp(ae.dynamicViscosityPaS(), be.dynamicViscosityPaS(), fraction),
				"PRESCRIBED_TRANSITION_INTERPOLATION_V1");
		Coordinate position = new Coordinate(xM, lerp(a.positionM().y, b.positionM().y, fraction),
				lerp(a.positionM().z, b.positionM().z, fraction));
		Coordinate tangent = new Coordinate(lerp(a.streamwiseTangent().x, b.streamwiseTangent().x, fraction),
				lerp(a.streamwiseTangent().y, b.streamwiseTangent().y, fraction),
				lerp(a.streamwiseTangent().z, b.streamwiseTangent().z, fraction));
		return new BoundaryLayerStation(sM, sM - a.sM(), position, radiusM,
				lerp(a.radiusGradient(), b.radiusGradient(), fraction),
				lerp(a.curvaturePerM(), b.curvaturePerM(), fraction),
				lerp(a.streamwiseVelocityMS(), b.streamwiseVelocityMS(), fraction),
				lerp(a.crossflowVelocityMS(), b.crossflowVelocityMS(), fraction),
				lerp(a.velocityGradientPerS(), b.velocityGradientPerS(), fraction),
				lerp(a.pressureGradientPaM(), b.pressureGradientPaM(), fraction),
				lerp(a.wallTemperatureK(), b.wallTemperatureK(), fraction),
				lerp(a.roughnessM(), b.roughnessM(), fraction), true, "FORCED_TRIP", edge, tangent,
				lerp(a.areaWidthM(), b.areaWidthM(), fraction));
	}

	private static double lerp(double a, double b, double fraction) {
		return a + fraction * (b - a);
	}

	private static List<BoundaryLayerRegion> regionsWithInsertion(List<BoundaryLayerRegion> regions,
			int insertionIndex) {
		return regions.stream().map(region -> new BoundaryLayerRegion(region.id(), region.type(),
				region.firstStation() >= insertionIndex ? region.firstStation() + 1 : region.firstStation(),
				region.lastStation() >= insertionIndex ? region.lastStation() + 1 : region.lastStation())).toList();
	}
}
