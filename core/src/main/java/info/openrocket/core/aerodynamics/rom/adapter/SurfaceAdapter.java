package info.openrocket.core.aerodynamics.rom.adapter;

import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.RomSurfaceHashUtil;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

/**
 * Converts a 4D surface into a 3D beta=0 drag surface for fallback compatibility.
 */
public final class SurfaceAdapter {

	private SurfaceAdapter() {
	}

	public static DragSurface toBetaZeroDragSurface(AeroSurface4D s4d) {
		int nM = s4d.machAxis.length;
		int nR = s4d.logReAxis.length;
		int nA = s4d.alphaAxis.length;

		double[][][] off = new double[nM][nR][nA];
		double[][][] on = new double[nM][nR][nA];
		for (int im = 0; im < nM; im++) {
			for (int ir = 0; ir < nR; ir++) {
				for (int ia = 0; ia < nA; ia++) {
					off[im][ir][ia] = s4d.cdPlumeOff[im][ir][ia][0];
					on[im][ir][ia] = s4d.cdPlumeOn[im][ir][ia][0];
				}
			}
		}
		return new DragSurface(
				s4d.machAxis,
				s4d.logReAxis,
				s4d.alphaAxis,
				off,
				on,
				RomSurfaceHashUtil.tag(s4d.geometryHash, RomSurfaceHashUtil.MODE_4D),
				0.0);
	}
}
