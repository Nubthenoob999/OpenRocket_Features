package info.openrocket.core.aerodynamics.physicsaero.transonic;
/** Dedicated asymmetric transonic excess; branch blending never creates this peak. */
public final class TransonicDragRiseModel {
	/*
	 * Drag-divergence Mach is conventionally identified at a very small drag
	 * increment, not at fifteen percent of the eventual peak.  Keeping that
	 * increment explicit also makes the two growth pieces meet continuously.
	 */
	private static final double DRAG_DIVERGENCE_DELTA_CD = 0.002;

	public record Parameters(double criticalMach, double dragDivergenceMach, double peakMach,
			double peakDeltaCd, double recoveryMach, double growthExponent, double decayRate,
			String sourceId) {
		public Parameters {
			if (criticalMach <= 0 || dragDivergenceMach <= criticalMach
					|| peakMach <= dragDivergenceMach || peakDeltaCd < 0
					|| recoveryMach <= peakMach || growthExponent <= 1 || decayRate <= 0
					|| sourceId == null) {
				throw new IllegalArgumentException();
			}
		}
	}

	public double deltaCd(double mach, double sonicFraction, Parameters parameters) {
		if (!Double.isFinite(mach + sonicFraction) || sonicFraction < 0 || sonicFraction > 1) {
			throw new IllegalArgumentException("invalid transonic progression");
		}
		if (mach <= parameters.criticalMach() || parameters.peakDeltaCd() == 0) {
			return 0;
		}

		double divergenceDelta = Math.min(DRAG_DIVERGENCE_DELTA_CD,
				0.02 * parameters.peakDeltaCd());
		if (mach < parameters.dragDivergenceMach()) {
			double x = unitInterval((mach - parameters.criticalMach())
					/ (parameters.dragDivergenceMach() - parameters.criticalMach()));
			double smooth = smoothStep(x);
			// The sonic progression damps the early rise but is constructed to equal
			// one, with zero slope, at drag divergence.
			double progression = sonicFraction + smooth * (1 - sonicFraction);
			return divergenceDelta * Math.pow(smooth * progression,
					parameters.growthExponent() / 2);
		}
		if (mach <= parameters.peakMach()) {
			double x = unitInterval((mach - parameters.dragDivergenceMach())
					/ (parameters.peakMach() - parameters.dragDivergenceMach()));
			double growth = Math.pow(smoothStep(x), parameters.growthExponent() / 2);
			return divergenceDelta + (parameters.peakDeltaCd() - divergenceDelta) * growth;
		}
		/*
		 * NACA RM L9I30 figures 8-12 show the post-rise zero-lift drag remaining
		 * near its supersonic plateau through the measured recovery point.  The
		 * recoveryMach parameter previously had no effect, so the closure began
		 * shedding wave drag immediately after the peak.  Retain the plateau to
		 * that source-bounded point, then use a Gaussian tail: its zero initial
		 * slope preserves continuous dCd/dM at the recovery handoff.
		 */
		if (mach <= parameters.recoveryMach()) {
			return parameters.peakDeltaCd();
		}
		double recoveryDistance = mach - parameters.recoveryMach();
		return parameters.peakDeltaCd()
				* Math.exp(-parameters.decayRate() * recoveryDistance * recoveryDistance);
	}

	private static double unitInterval(double value) {
		return Math.max(0, Math.min(1, value));
	}

	private static double smoothStep(double value) {
		return value * value * (3 - 2 * value);
	}
}
