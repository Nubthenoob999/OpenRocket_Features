package info.openrocket.core.aerodynamics.physicsaero.roughness;
public record SurfaceRoughness(double equivalentSandGrainM, String type, String source) {
	public SurfaceRoughness { if (equivalentSandGrainM < 0 || !Double.isFinite(equivalentSandGrainM) || type == null || source == null) throw new IllegalArgumentException("invalid roughness"); }
}
