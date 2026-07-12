package info.openrocket.core.aerodynamics.physicsaero.blending;
import java.util.ArrayList;
import java.util.List;
public final class BlendContinuityValidator {
	public record Result(boolean valid,double maximumJump,double maximumSlopeChange,List<Integer> offendingIntervals){public Result{offendingIntervals=List.copyOf(offendingIntervals);}}
	public Result validate(double[] mach,double[] coefficient,double jumpLimit,double slopeChangeLimit){if(mach.length!=coefficient.length||mach.length<3)throw new IllegalArgumentException();double maxJ=0,maxS=0,prior=0;List<Integer>bad=new ArrayList<>();for(int i=1;i<mach.length;i++){double jump=Math.abs(coefficient[i]-coefficient[i-1]),slope=(coefficient[i]-coefficient[i-1])/(mach[i]-mach[i-1]);maxJ=Math.max(maxJ,jump);if(i>1){double ds=Math.abs(slope-prior);maxS=Math.max(maxS,ds);if(jump>jumpLimit||ds>slopeChangeLimit)bad.add(i-1);}prior=slope;}return new Result(bad.isEmpty(),maxJ,maxS,bad);}
}
