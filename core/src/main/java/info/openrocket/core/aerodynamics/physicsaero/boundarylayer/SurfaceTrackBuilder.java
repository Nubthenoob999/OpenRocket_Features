package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowEvent;
import info.openrocket.core.aerodynamics.physicsaero.flow.SurfaceState;
import info.openrocket.core.aerodynamics.physicsaero.thermal.RecoveryTemperatureModel;
import info.openrocket.core.util.Coordinate;

/** Adapts Phase-2 body edge histories without introducing another flow-state type. */
public final class SurfaceTrackBuilder {
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
}
