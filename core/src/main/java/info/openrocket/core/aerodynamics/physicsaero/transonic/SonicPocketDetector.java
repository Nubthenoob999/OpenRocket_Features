package info.openrocket.core.aerodynamics.physicsaero.transonic;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.flow.SurfaceState;
public final class SonicPocketDetector {
	public record Pocket(int firstStation,int lastStation,double maximumMach,boolean physicalLocalState){ }
	public Pocket detect(List<SurfaceState> states,double tolerance){int first=-1,last=-1;double max=0;for(int i=0;i<states.size();i++){double m=states.get(i).staticState().mach();if(m>=1-tolerance){if(first<0)first=i;last=i;max=Math.max(max,m);}}return first<0?null:new Pocket(first,last,max,true);}
}
