package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

public final class HeadShapeFactorClosure {
	public double h1(double h) {
		if (h <= 1.1) throw new IllegalArgumentException("H outside Head closure");
		return h <= 1.6 ? 3.3 + 0.8234 / Math.pow(h - 1.1, 1.287) : 3.3 + 1.5501 / Math.pow(h - 0.6778, 3.064);
	}
	public double h(double h1) {
		if (h1 <= 3.3) throw new IllegalArgumentException("H1 outside Head closure");
		double lo = 1.100001, hi = 4.0;
		for (int i = 0; i < 80; i++) { double mid = 0.5 * (lo + hi); if (h1(mid) > h1) lo = mid; else hi = mid; }
		return 0.5 * (lo + hi);
	}
}
