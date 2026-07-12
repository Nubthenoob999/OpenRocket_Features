package info.openrocket.core.aerodynamics.physicsaero.transition;

import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.*;
import info.openrocket.core.aerodynamics.physicsaero.thermal.WallThermalBoundary;
import info.openrocket.core.util.Coordinate;

/** Runs the specified 0.1/0.2/0.5-percent free-flight turbulence sensitivity envelope. */
public final class TransitionUncertaintyRunner {
	public record Envelope(BoundaryLayerResult early, BoundaryLayerResult nominal, BoundaryLayerResult late,
			double minimumDragN, double maximumDragN) { }
	public Envelope run(SurfaceTrack track, BoundaryLayerConfiguration nominal, Coordinate reference) {
		BoundaryLayerResult late=runAt(track,nominal,.1,reference), center=runAt(track,nominal,.2,reference), early=runAt(track,nominal,.5,reference);
		double min=Math.min(drag(late),Math.min(drag(center),drag(early))),max=Math.max(drag(late),Math.max(drag(center),drag(early)));
		return new Envelope(early,center,late,min,max);
	}
	private BoundaryLayerResult runAt(SurfaceTrack track,BoundaryLayerConfiguration c,double tu,Coordinate r) {
		return new BoundaryLayerMarcher().march(track,new BoundaryLayerConfiguration(c.transitionMode(),tu,c.transitionBlendLengthM(),c.intermittencyExponent(),
				c.wallMode()==null?WallThermalBoundary.ADIABATIC:c.wallMode(),c.gamma(),c.prandtl(),c.minimumVelocityMS(),c.totalStateRelativeTolerance()),r);
	}
	private double drag(BoundaryLayerResult r){ var f=r.skinFriction().forceBodyN(); return Math.sqrt(f.x*f.x+f.y*f.y+f.z*f.z); }
}
