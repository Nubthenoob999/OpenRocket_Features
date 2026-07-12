package info.openrocket.core.aerodynamics.physicsaero.table;

public record TableAxes(double[] mach, double[] alphaRad, double[] betaRad) {
	public TableAxes { mach = copyValidate(mach, true); alphaRad = copyValidate(alphaRad, false); betaRad = copyValidate(betaRad, false); }
	@Override public double[] mach() { return mach.clone(); } @Override public double[] alphaRad() { return alphaRad.clone(); }
	@Override public double[] betaRad() { return betaRad.clone(); }
	public int cellCount() { return mach.length * alphaRad.length * betaRad.length; }
	public int index(int machIndex, int alphaIndex, int betaIndex) {
		if (machIndex < 0 || machIndex >= mach.length || alphaIndex < 0 || alphaIndex >= alphaRad.length || betaIndex < 0 || betaIndex >= betaRad.length)
			throw new IndexOutOfBoundsException();
		return ((machIndex * alphaRad.length) + alphaIndex) * betaRad.length + betaIndex;
	}
	private static double[] copyValidate(double[] input, boolean nonnegative) {
		double[] v = input.clone(); if (v.length == 0) throw new IllegalArgumentException("empty table axis");
		for (int i = 0; i < v.length; i++) if (!Double.isFinite(v[i]) || (nonnegative && v[i] < 0) || (i > 0 && v[i] <= v[i - 1]))
			throw new IllegalArgumentException("table axes must be finite and strictly increasing");
		return v;
	}
}
