package info.openrocket.core.aerodynamics.rom.core.basis;

/**
 * Local hp-EIM POD region in geometry feature space.
 */
public final class LocalPodRegion {

	public final double[] centroidFeatureVector;
	public final double sigma;
	public final PodBasis basisOff;
	public final PodBasis basisOn;
	public final String regionLabel;

	public LocalPodRegion(double[] centroidFeatureVector, double sigma,
			PodBasis basisOff, PodBasis basisOn, String regionLabel) {
		this.centroidFeatureVector = centroidFeatureVector;
		this.sigma = sigma;
		this.basisOff = basisOff;
		this.basisOn = basisOn;
		this.regionLabel = regionLabel;
	}

	public double weight(double[] queryFeatures) {
		double distSq = 0.0;
		for (int i = 0; i < centroidFeatureVector.length; i++) {
			double d = queryFeatures[i] - centroidFeatureVector[i];
			distSq += d * d;
		}
		double denom = 2.0 * sigma * sigma;
		if (denom <= 1e-18) {
			return distSq < 1e-18 ? 1.0 : 0.0;
		}
		return Math.exp(-distSq / denom);
	}
}
