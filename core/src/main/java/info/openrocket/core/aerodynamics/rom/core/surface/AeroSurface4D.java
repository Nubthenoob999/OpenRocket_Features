package info.openrocket.core.aerodynamics.rom.core.surface;

/**
 * Precomputed 4D aerodynamic coefficient surface over (M, Re, alpha, beta).
 */
public final class AeroSurface4D {

	public final double[] machAxis;
	public final double[] logReAxis;
	public final double[] alphaAxis;
	public final double[] betaAxis;

	public final double[][][][] cdPlumeOff;
	public final double[][][][] cdPlumeOn;
	public final double[][][][] cdBody;

	public final double[][][][] CN;
	public final double[][][][] Cm;

	public final String geometryHash;
	public final long buildTimestampMs;
	public final int finCount;
	public final double looRmsePercent;

	public AeroSurface4D(double[] machAxis, double[] logReAxis,
			double[] alphaAxis, double[] betaAxis,
			double[][][][] cdPlumeOff, double[][][][] cdPlumeOn,
			double[][][][] cdBody,
			double[][][][] CN, double[][][][] Cm,
			String geometryHash, int finCount,
			double looRmsePercent) {
		this.machAxis = machAxis;
		this.logReAxis = logReAxis;
		this.alphaAxis = alphaAxis;
		this.betaAxis = betaAxis;
		this.cdPlumeOff = cdPlumeOff;
		this.cdPlumeOn = cdPlumeOn;
		this.cdBody = cdBody;
		this.CN = CN;
		this.Cm = Cm;
		this.geometryHash = geometryHash;
		this.buildTimestampMs = System.currentTimeMillis();
		this.finCount = finCount;
		this.looRmsePercent = looRmsePercent;
		validateAxes();
	}

	public AeroSurface4D(double[] machAxis, double[] logReAxis,
			double[] alphaAxis, double[] betaAxis,
			double[][][][] cdPlumeOff, double[][][][] cdPlumeOn,
			double[][][][] cdBody,
			double[][][][] CN, double[][][][] Cm,
			String geometryHash, int finCount) {
		this(machAxis, logReAxis, alphaAxis, betaAxis,
				cdPlumeOff, cdPlumeOn, cdBody, CN, Cm,
				geometryHash, finCount, 0.0);
	}

	public double getDeltaCdFin(int im, int ir, int ia, int ib) {
		return cdPlumeOff[im][ir][ia][ib] - cdBody[im][ir][ia][ib];
	}

	private void validateAxes() {
		assertStrictlyIncreasing(machAxis, "machAxis");
		assertStrictlyIncreasing(logReAxis, "logReAxis");
		assertStrictlyIncreasing(alphaAxis, "alphaAxis");
		assertStrictlyIncreasing(betaAxis, "betaAxis");
		if (alphaAxis.length == 0 || alphaAxis[0] != 0.0) {
			throw new IllegalArgumentException("alphaAxis must start at 0.0");
		}
		if (betaAxis.length == 0 || betaAxis[0] != 0.0) {
			throw new IllegalArgumentException("betaAxis must start at 0.0");
		}
	}

	private static void assertStrictlyIncreasing(double[] axis, String name) {
		if (axis == null || axis.length == 0) {
			throw new IllegalArgumentException(name + " must have at least one point");
		}
		if (axis.length == 1) {
			return;
		}
		for (int i = 1; i < axis.length; i++) {
			if (axis[i] <= axis[i - 1]) {
				throw new IllegalArgumentException(name + " is not strictly increasing at i=" + i);
			}
		}
	}
}
