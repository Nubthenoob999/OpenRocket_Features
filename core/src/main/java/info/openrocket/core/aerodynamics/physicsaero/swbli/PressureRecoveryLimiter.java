package info.openrocket.core.aerodynamics.physicsaero.swbli;
/** Convex, bounded recovery correction; a local trace requires an explicit defensible target. */
public final class PressureRecoveryLimiter {
	public record Result(double inviscidPressurePa,double correctedPressurePa,double targetPressurePa,double recoveryFraction,boolean localPressureResolved,String methodId){ }
	public Result apply(double inviscidPressurePa,Double separatedTargetPressurePa,double severity){
		if(inviscidPressurePa<=0||severity<0||severity>1)throw new IllegalArgumentException("invalid recovery state");
		if(separatedTargetPressurePa==null)return new Result(inviscidPressurePa,inviscidPressurePa,Double.NaN,0,false,"component-residual-only-v1");
		if(separatedTargetPressurePa<=0)throw new IllegalArgumentException("invalid separated pressure target");
		double g=Math.min(1,severity);double corrected=inviscidPressurePa+g*(separatedTargetPressurePa-inviscidPressurePa);
		return new Result(inviscidPressurePa,corrected,separatedTargetPressurePa,g,true,"bounded-convex-pressure-recovery-v1");
	}
}
