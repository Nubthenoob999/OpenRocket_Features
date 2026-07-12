package info.openrocket.core.aerodynamics.physicsaero.math;

import java.util.function.DoubleUnaryOperator;

/** Deterministic bracket-preserving Brent-Dekker solver. */
public final class BrentRootSolver implements BracketedRootSolver {
	@Override public double solve(DoubleUnaryOperator f, double lower, double upper, double tolerance, int maxIterations) {
		double a = lower, b = upper, fa = f.applyAsDouble(a), fb = f.applyAsDouble(b);
		if (!Double.isFinite(fa + fb) || fa * fb > 0) throw new IllegalArgumentException("root is not bracketed");
		if (fa == 0) return a;
		if (fb == 0) return b;
		double c = a, fc = fa, d = b - a, e = d;
		for (int i = 0; i < maxIterations; i++) {
			if (fb * fc > 0) { c = a; fc = fa; d = b - a; e = d; }
			if (Math.abs(fc) < Math.abs(fb)) {
				double oldB = b, oldFb = fb;
				a = oldB; fa = oldFb; b = c; fb = fc; c = oldB; fc = oldFb;
			}
			double tol = 2 * Math.ulp(Math.abs(b)) + tolerance / 2;
			double midpoint = (c - b) / 2;
			if (Math.abs(midpoint) <= tol || fb == 0) return b;
			if (Math.abs(e) >= tol && Math.abs(fa) > Math.abs(fb)) {
				double s = fb / fa, p, q;
				if (a == c) { p = 2 * midpoint * s; q = 1 - s; }
				else {
					q = fa / fc; double r = fb / fc;
					p = s * (2 * midpoint * q * (q - r) - (b - a) * (r - 1));
					q = (q - 1) * (r - 1) * (s - 1);
				}
				if (p > 0) q = -q; else p = -p;
				double oldE = e; e = d;
				if (2 * p < Math.min(3 * midpoint * q - Math.abs(tol * q), Math.abs(oldE * q))) d = p / q;
				else { d = midpoint; e = midpoint; }
			} else { d = midpoint; e = midpoint; }
			a = b; fa = fb;
			b += Math.abs(d) > tol ? d : Math.copySign(tol, midpoint);
			fb = f.applyAsDouble(b);
		}
		throw new IllegalStateException("root solver iteration limit exceeded");
	}
}
