package info.openrocket.core.aerodynamics.physicsaero.roughness;
public final class RoughWallSkinFrictionCorrection {
	public RoughnessRegime regime(double ksPlus) { return ksPlus < 5 ? RoughnessRegime.HYDRAULICALLY_SMOOTH : ksPlus < 70 ? RoughnessRegime.TRANSITIONALLY_ROUGH : RoughnessRegime.FULLY_ROUGH; }
	public double apply(double smoothCf, double ksPlus) {
		if (ksPlus < 5) return smoothCf;
		double multiplier = ksPlus < 70 ? 1.0 + 0.12 * Math.log(ksPlus / 5.0) : 1.0 + 0.12 * Math.log(14.0) + 0.08 * Math.log(ksPlus / 70.0);
		return smoothCf * multiplier;
	}
}
