package info.openrocket.core.montecarlo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * A fixed per-run realization of gusts and a shear layer.
 *
 * The realization is a pure function of time and altitude.  It contains no
 * evolving random state, so repeated queries and queries made by co-located
 * separated stages return exactly the same disturbance.
 */
public final class RunWindDisturbanceProfile {

    final List<GustEvent> gusts = new ArrayList<>();
    final ShearLayer shear; // null if disabled

    RunWindDisturbanceProfile(List<GustEvent> gusts, ShearLayer shear) {
        if (gusts != null) this.gusts.addAll(gusts);
        this.shear = shear;
    }

	public int getGustCount() {
		return gusts.size();
	}

	/** Stable per-run representation for audit exports. */
	public String toAuditString() {
		StringBuilder value = new StringBuilder("gusts=[");
		for (int index = 0; index < gusts.size(); index++) {
			GustEvent gust = gusts.get(index);
			if (index > 0) value.append(';');
			value.append(gust.t0_s).append('|').append(gust.duration_s).append('|')
					.append(gust.peakDelta.u).append('|').append(gust.peakDelta.v);
		}
		value.append(']');
		if (shear != null) {
			value.append(",shear=").append(shear.centerAlt_m).append('|')
					.append(shear.thickness_m).append('|').append(shear.deltaTop.u)
					.append('|').append(shear.deltaTop.v);
		}
		return value.toString();
	}

    Vec2 deltaWindXY(double t_s, double alt_m) {
        Vec2 sum = new Vec2(0, 0);
        if (shear != null) {
            sum = sum.add(shear.deltaAtAltitude(alt_m));
        }
        if (!gusts.isEmpty()) {
            for (GustEvent g : gusts) {
                sum = sum.add(g.deltaAtTime(t_s));
            }
        }
        return sum;
    }

    static RunWindDisturbanceProfile sampleFromConfig(MonteCarloExtension ext, long seed) {
        if (ext == null) return new RunWindDisturbanceProfile(null, null);
		RandomStreamManager streams = new RandomStreamManager(seed);
		Random gustRng = streams.stream("wind.gusts");
		Random shearRng = streams.stream("wind.shear");

        final boolean gustEnabled = ext.isGustEventsEnabled();
        final boolean shearEnabled = ext.isShearLayerEnabled();

        List<GustEvent> gusts = new ArrayList<>();
        ShearLayer shear = null;

        // ------------------------------------------------------------
        // Gust events
        // ------------------------------------------------------------
        if (gustEnabled) {
            int n = clampInt(ext.getGustEventCount(), 0, 50);

            double tStart = Math.max(0.0, ext.getGustWindowStartS());
            double tEnd = Math.max(tStart, ext.getGustWindowEndS());
            if (tEnd <= tStart) {
                // Sensible fallback: first 15 seconds
                tStart = 0.0;
                tEnd = 15.0;
            }

            // Duration sampling
            double durMean = Math.max(0.05, ext.getGustDurationMeanS());
            double durSigma = Math.max(0.0, ext.getGustDurationSigmaS());

            // Peak magnitude sampling
            double peakMean = Math.max(0.0, ext.getGustPeakDeltaMeanMps());
            double peakSigma = Math.max(0.0, ext.getGustPeakDeltaSigmaMps());

            for (int i = 0; i < n; i++) {
				double t0 = lerp(tStart, tEnd, gustRng.nextDouble());
				double dur = sampleTruncNormal(gustRng, durMean, durSigma, 0.05, 60.0);
				double peak = sampleTruncNormal(gustRng, peakMean, peakSigma, 0.0, 200.0);

                // Direction: random 0..2pi (in the simulation's u/v frame)
				double theta = 2.0 * Math.PI * gustRng.nextDouble();
                Vec2 peakDelta = new Vec2(peak * Math.cos(theta), peak * Math.sin(theta));

                // Ensure the gust stays within [tStart,tEnd] if possible
                double maxDur = Math.max(0.05, tEnd - tStart);
                if (dur > maxDur) dur = maxDur;
                if (t0 + dur > tEnd) t0 = Math.max(tStart, tEnd - dur);

                gusts.add(new GustEvent(t0, dur, peakDelta));
            }
			gusts.sort(Comparator.comparingDouble(gust -> gust.t0_s));
        }

        // ------------------------------------------------------------
        // Shear layer
        // ------------------------------------------------------------
        if (shearEnabled) {
            double centerAlt = clamp(ext.getShearCenterAltM(), -1000.0, 20000.0);
            double thickness = clamp(ext.getShearThicknessM(), 1.0, 20000.0);

            double mean = Math.max(0.0, ext.getShearDeltaMeanMps());
            double sigma = Math.max(0.0, ext.getShearDeltaSigmaMps());
			double mag = sampleTruncNormal(shearRng, mean, sigma, 0.0, 200.0);

            // Direction random; can be aligned with prevailing wind in a future enhancement.
			double theta = 2.0 * Math.PI * shearRng.nextDouble();
            Vec2 deltaTop = new Vec2(mag * Math.cos(theta), mag * Math.sin(theta));

            shear = new ShearLayer(centerAlt, thickness, deltaTop);
        }

        return new RunWindDisturbanceProfile(gusts, shear);
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Truncated normal via rejection. Fine for small N (gustCount <= 50).
     */
    private static double sampleTruncNormal(Random rng, double mean, double sigma, double lo, double hi) {
        if (sigma <= 0.0) return clamp(mean, lo, hi);
        for (int k = 0; k < 64; k++) {
            double x = mean + rng.nextGaussian() * sigma;
            if (x >= lo && x <= hi) return x;
        }
        // fallback
        return clamp(mean, lo, hi);
    }
}
