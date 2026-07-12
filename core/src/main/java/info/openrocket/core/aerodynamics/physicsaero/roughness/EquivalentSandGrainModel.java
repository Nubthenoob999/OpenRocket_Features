package info.openrocket.core.aerodynamics.physicsaero.roughness;
/** Conversion is deliberately configured by roughness type and a source-traceable multiplier. */
public final class EquivalentSandGrainModel {
	public SurfaceRoughness convert(double measuredHeightM,double sandGrainMultiplier,String roughnessType,String source) {
		if (measuredHeightM<0||sandGrainMultiplier<=0) throw new IllegalArgumentException("invalid roughness conversion");
		return new SurfaceRoughness(measuredHeightM*sandGrainMultiplier,roughnessType,source);
	}
}
