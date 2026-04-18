package info.openrocket.core.aerodynamics.rom.math;

/**
 * Drela e^N amplification-envelope utilities.
 *
 * <p>Primary reference: Drela & Giles, AIAA Journal 25(10), 1987.
 */
public final class ENTransition {
	private static final double ENVELOPE_COEFFICIENT = 0.01;
	private static final double ENVELOPE_OFFSET = 0.25;
	private static final double POLL_CONTAMINATION_THRESHOLD = 250.0;

	private ENTransition() {
	}

	public static double dNdReTheta(double shapeFactor) {
		double envelope = 2.4 * shapeFactor - 3.7 + 2.5 * Math.tanh(1.5 * shapeFactor - 4.65);
		return ENVELOPE_COEFFICIENT * Math.sqrt(envelope * envelope + ENVELOPE_OFFSET);
	}

	public static double stepN(double amplificationFactor, double shapeFactor, double deltaReTheta) {
		return amplificationFactor + dNdReTheta(shapeFactor) * deltaReTheta;
	}

	public static boolean isTransitioned(double amplificationFactor, double criticalN) {
		return amplificationFactor >= criticalN;
	}

	public static double michelTransitionReTheta(double reynoldsX) {
		double boundedRe = Math.max(1.0, reynoldsX);
		return 1.174 * (1.0 + 22400.0 / boundedRe) * Math.pow(boundedRe, 0.46);
	}

	public static boolean pollAttachmentLineContamination(double reynoldsThetaLeadingEdge, double sweepAngle) {
		double attachmentLineParameter = reynoldsThetaLeadingEdge * Math.sin(Math.abs(sweepAngle));
		return attachmentLineParameter > POLL_CONTAMINATION_THRESHOLD;
	}
}
