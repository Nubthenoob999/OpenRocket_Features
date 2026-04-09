package info.openrocket.core.airbrakesplugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AirbrakeSimulationListenerTest {

	@Test
	public void legacyCombinedReferenceAreaKeepsTotalCdOnSharedArea() {
		double dynP = 5000.0;
		double rocketArea = 0.02;
		double airbrakeArea = 0.03;
		double rocketCd = 1.0;
		double rocketDrag = rocketCd * dynP * rocketArea;
		double airbrakeDrag = 50.0;

		double combinedArea = rocketArea + airbrakeArea;
		double totalCd = (rocketDrag + airbrakeDrag) / (dynP * combinedArea);

		assertEquals(0.6, totalCd, 1e-12);
	}

	@Test
	public void fallbackAreaMatchesLegacyConversionWhenConfigAreaMissing() {
		double dynP = 4000.0;
		double rocketArea = 0.02;
		double fallbackAirbrakeArea = 0.01;
		double rocketDrag = 80.0;
		double airbrakeDrag = 40.0;

		double totalCd = (rocketDrag + airbrakeDrag) / (dynP * (rocketArea + fallbackAirbrakeArea));

		assertEquals(1.0, totalCd, 1e-12);
	}
}
