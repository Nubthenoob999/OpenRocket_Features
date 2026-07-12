package info.openrocket.core.structures.materials;

/**
 * Adapts the material assigned to a rocket component for the structures tool.
 *
 * <p>OpenRocket materials currently persist density and in-plane shear modulus,
 * but not Young's modulus, tensile/compressive allowables, or Poisson ratio.
 * Those unavailable properties intentionally remain unknown here.  In
 * particular, this class must never infer them from a material name: a name
 * match is a tool-level override and can produce an unsafe result for a
 * component that uses a different laminate, grade, infill, or orientation.</p>
 */
public final class MaterialLibrary {
	public static final StructuralMaterial USER_DEFINED = new StructuralMaterial(
			"Material properties missing", Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
			Double.NaN, null);

	private MaterialLibrary() {
	}

	public static StructuralMaterial fromOpenRocketMaterial(info.openrocket.core.material.Material material) {
		if (material == null) {
			return USER_DEFINED;
		}
		double shearModulus = material.getInPlaneShearModulus();
		if (!Double.isFinite(shearModulus) || shearModulus <= 0) {
			shearModulus = Double.NaN;
		}
		double density = material.getDensity();
		if (!Double.isFinite(density) || density <= 0) {
			density = Double.NaN;
		}
		double youngsModulus = material.getYoungsModulus();
		double tensileStrength = material.getTensileStrength();
		double compressiveStrength = material.getCompressiveStrength();
		double poissonRatio = material.getPoissonRatio();
		return new StructuralMaterial(material.getName(), youngsModulus, shearModulus, tensileStrength, Double.NaN,
				compressiveStrength, density, Double.isFinite(poissonRatio) ? poissonRatio : null);
	}
}
