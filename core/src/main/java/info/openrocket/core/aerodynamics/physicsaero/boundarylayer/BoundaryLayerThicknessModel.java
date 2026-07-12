package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;
public final class BoundaryLayerThicknessModel {
	public double delta99M(double theta, double h, TransitionState state) {
		return theta * (state == TransitionState.LAMINAR ? 7.52 * h / 2.59 : 8.0 + 2.0 * (h - 1.4));
	}
}
