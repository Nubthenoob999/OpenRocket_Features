package info.openrocket.core.tuning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VerticalKinematicsReconstructorTest {
	@Test
	public void pressureAnchorPullsAltitudeBackTowardBarometricTrace() {
		TelemetrySeries raw = new TelemetrySeries(TelemetrySchema.OPENROCKET_SIMULATION);
		TelemetryParserDiagnostics.Mutable diagnostics =
				new TelemetryParserDiagnostics.Mutable(TelemetrySchema.OPENROCKET_SIMULATION.name());
		for (int i = 0; i < 80; i++) {
			double t = i * 0.10;
			double anchoredAltitude = i * 4.0;
			double driftingAltitude = anchoredAltitude + i * 1.5;
			double velocity = 40.0;
			double pressure = 101325.0 * Math.pow(1.0 - anchoredAltitude / 44330.0, 1.0 / 0.190263);
			raw.addPoint(t, driftingAltitude, velocity, null, null, 0.0, pressure, 20.0);
			diagnostics.incRowsRead();
			diagnostics.incRowsAccepted();
		}
		raw.setParserDiagnostics(diagnostics.freeze());

		VerticalKinematicsReconstructor.ReconstructionResult reconstruction =
				VerticalKinematicsReconstructor.reconstruct(raw);

		double rawEndError = Math.abs(raw.getAltitudeMetersAgl().get(raw.size() - 1) - pressureAltitude(raw, raw.size() - 1));
		double correctedEndError = Math.abs(reconstruction.getCorrectedSeries().getAltitudeMetersAgl().get(raw.size() - 1)
				- pressureAltitude(raw, raw.size() - 1));

		assertTrue(Double.isFinite(reconstruction.getDiagnostics().getRawDriftMeanAbsMeters()));
		assertTrue(Double.isFinite(reconstruction.getDiagnostics().getPressureAnchorCorrectionMeanAbsMeters()));
		assertTrue(correctedEndError < rawEndError, "Pressure anchor should reduce long-horizon altitude drift");
	}

	@Test
	public void preLaunchBiasIsEstimatedAndRemovedBeforeIntegration() {
		TelemetrySeries raw = new TelemetrySeries(TelemetrySchema.OPENROCKET_SIMULATION);
		TelemetryParserDiagnostics.Mutable diagnostics =
				new TelemetryParserDiagnostics.Mutable(TelemetrySchema.OPENROCKET_SIMULATION.name());
		for (int i = 0; i < 60; i++) {
			double t = i * 0.10;
			double altitude = i < 10 ? 0.0 : (i - 10) * 6.0;
			double velocity = i < 10 ? 0.0 : 60.0;
			double accel = i < 10 ? 0.75 : 2.25;
			double pressure = 101325.0 * Math.pow(1.0 - altitude / 44330.0, 1.0 / 0.190263);
			raw.addPoint(t, altitude, velocity, null, null, accel, pressure, 20.0);
			diagnostics.incRowsRead();
			diagnostics.incRowsAccepted();
		}
		raw.setParserDiagnostics(diagnostics.freeze());

		VerticalKinematicsReconstructor.ReconstructionResult reconstruction =
				VerticalKinematicsReconstructor.reconstruct(raw);

		assertEquals(0.75, reconstruction.getDiagnostics().getAccelBiasEstimateMetersPerSec2(), 1e-9);
		assertTrue(reconstruction.getDiagnostics().getPreLaunchSampleCount() >= 3);
		assertTrue(Double.isFinite(reconstruction.getDiagnostics().getLaunchDetectedTimeSec()));
		assertEquals(0.0, reconstruction.getCorrectedSeries().getAccelerationZMetersPerSec2().get(0), 1e-9);
	}

	private static double pressureAltitude(TelemetrySeries series, int index) {
		double baseline = series.getPressurePa().get(0);
		double pressure = series.getPressurePa().get(index);
		return 44330.0 * (1.0 - Math.pow(pressure / baseline, 0.190263));
	}
}
