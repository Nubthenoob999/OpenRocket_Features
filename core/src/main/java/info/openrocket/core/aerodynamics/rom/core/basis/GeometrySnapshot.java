package info.openrocket.core.aerodynamics.rom.core.basis;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

/**
 * A single (geometry, surface) pair used for POD basis construction.
 */
public final class GeometrySnapshot {

	public final RomGeometryInput geometry;
	public final String geometryHash;
	public final double[] cdOffFlat;
	public final double[] cdOnFlat;
	public final double[] machAxis;
	public final double[] logReAxis;
	public final double[] alphaAxis;
	public final double[] betaAxis;
	public final double[] featureVector;

	public GeometrySnapshot(RomGeometryInput geometry, AeroSurface4D surface) {
		this.geometry = geometry;
		this.geometryHash = surface.geometryHash;
		this.machAxis = surface.machAxis;
		this.logReAxis = surface.logReAxis;
		this.alphaAxis = surface.alphaAxis;
		this.betaAxis = surface.betaAxis;
		this.featureVector = geometry.toFeatureVector();

		int nM = surface.machAxis.length;
		int nR = surface.logReAxis.length;
		int nA = surface.alphaAxis.length;
		int nB = surface.betaAxis.length;
		int n = nM * nR * nA * nB;

		this.cdOffFlat = new double[n];
		this.cdOnFlat = new double[n];

		int idx = 0;
		for (int im = 0; im < nM; im++) {
			for (int ir = 0; ir < nR; ir++) {
				for (int ia = 0; ia < nA; ia++) {
					for (int ib = 0; ib < nB; ib++) {
						cdOffFlat[idx] = surface.cdPlumeOff[im][ir][ia][ib];
						cdOnFlat[idx] = surface.cdPlumeOn[im][ir][ia][ib];
						idx++;
					}
				}
			}
		}
	}
}
