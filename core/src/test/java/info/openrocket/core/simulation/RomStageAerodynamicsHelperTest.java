package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.RomAerodynamicCalculator.BoundaryEvent;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator.FlightRegime;

class RomStageAerodynamicsHelperTest {

	@Test
	void detectFlightRegimeFollowsEventStateOrder() {
		assertEquals(FlightRegime.PRE_LAUNCH,
				RomStageAerodynamicsHelper.detectFlightRegime(false, false, false, false, false));
		assertEquals(FlightRegime.POWERED_ASCENT,
				RomStageAerodynamicsHelper.detectFlightRegime(true, false, false, false, true));
		assertEquals(FlightRegime.COAST_ASCENT,
				RomStageAerodynamicsHelper.detectFlightRegime(true, false, false, false, false));
		assertEquals(FlightRegime.POST_APOGEE,
				RomStageAerodynamicsHelper.detectFlightRegime(true, true, false, false, false));
		assertEquals(FlightRegime.RECOVERY,
				RomStageAerodynamicsHelper.detectFlightRegime(true, true, false, true, false));
		assertEquals(FlightRegime.LANDED,
				RomStageAerodynamicsHelper.detectFlightRegime(true, true, true, true, false));
	}

	@Test
	void detectNextBoundaryChoosesEarliestRelevantEvent() {
		List<FlightEvent> events = Arrays.asList(
				new FlightEvent(FlightEvent.Type.ALTITUDE, 0.10, null),
				new FlightEvent(FlightEvent.Type.APOGEE, 2.50, null),
				new FlightEvent(FlightEvent.Type.BURNOUT, 1.25, null),
				new FlightEvent(FlightEvent.Type.RECOVERY_DEVICE_DEPLOYMENT, 1.26, null));

		RomStageAerodynamicsHelper.BoundaryMarker marker =
				RomStageAerodynamicsHelper.detectNextBoundary(events, 0.50);

		assertEquals(BoundaryEvent.BURNOUT, marker.boundaryEvent());
		assertEquals(1.25, marker.boundaryTimeSeconds(), 1e-12);
	}

	@Test
	void detectNextBoundarySkipsPastEventsAndKeepsRecoveryBoundaries() {
		List<FlightEvent> events = Arrays.asList(
				new FlightEvent(FlightEvent.Type.BURNOUT, 0.90, null),
				new FlightEvent(FlightEvent.Type.RECOVERY_DEVICE_DEPLOYMENT, 1.20, null),
				new FlightEvent(FlightEvent.Type.APOGEE, 1.80, null));

		RomStageAerodynamicsHelper.BoundaryMarker marker =
				RomStageAerodynamicsHelper.detectNextBoundary(events, 1.00);

		assertEquals(BoundaryEvent.RECOVERY_DEVICE_DEPLOYMENT, marker.boundaryEvent());
		assertEquals(1.20, marker.boundaryTimeSeconds(), 1e-12);
	}
}
