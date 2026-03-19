package info.openrocket.core.aerodynamics.rom;

/**
 * Precomputed Cd(M, Re, alpha) surface stored as two 3D grids:
 * one for powered flight (plume-on) and one for coast (plume-off).
 *
 * Axes are stored as strictly monotonically increasing double arrays.
 * Grid values are Cd referenced to maximum cross-sectional area.
 */
public class DragSurface {

	// Axis arrays - must be monotonically increasing
	public final double[] machAxis;     // e.g. 50 points, 0.01 to 4.0
	public final double[] logReAxis;    // e.g. 20 points, log10(1e4) to log10(1e8)
	public final double[] alphaAxis;    // e.g. 10 points, 0.0 to 15.0 degrees

	// Cd grids - shape [machAxis.length][logReAxis.length][alphaAxis.length]
	public final double[][][] cdPlumeOff;   // coast phase
	public final double[][][] cdPlumeOn;    // powered phase

	// Metadata
	public final String geometryHash;       // SHA-256 of the geometry parameters
	public final double looRmsePercent;     // leave-one-out error, 0 if not computed
	public final long buildTimestampMs;

	public DragSurface(double[] machAxis, double[] logReAxis, double[] alphaAxis,
						  double[][][] cdPlumeOff, double[][][] cdPlumeOn,
						  String geometryHash, double looRmsePercent) {
		this.machAxis = machAxis;
		this.logReAxis = logReAxis;
		this.alphaAxis = alphaAxis;
		this.cdPlumeOff = cdPlumeOff;
		this.cdPlumeOn = cdPlumeOn;
		this.geometryHash = geometryHash;
		this.looRmsePercent = looRmsePercent;
		this.buildTimestampMs = System.currentTimeMillis();
	}
}
