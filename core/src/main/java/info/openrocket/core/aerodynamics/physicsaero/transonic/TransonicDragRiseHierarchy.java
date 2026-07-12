package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class TransonicDragRiseHierarchy {
	public TransonicDragRiseModel.Parameters select(double finenessRatio,double finThicknessRatio,double mcr,double mdd){double peak=Math.max(1.02,mdd+.08),amplitude=Math.min(.45,.12+.3*finThicknessRatio+.08/Math.max(2,finenessRatio));return new TransonicDragRiseModel.Parameters(mcr,mdd,peak,amplitude,1.35,2,4,"MISSILE_GEOMETRY_FAMILY_V1");}
}
