package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;

public class LiveWeatherServiceTest {

	@Test
	public void testBuildOpenMeteoUriUsesArchiveForOlderDates() {
		Clock clock = Clock.fixed(Instant.parse("2026-04-01T12:00:00Z"), ZoneOffset.UTC);
		LiveWeatherService service = new LiveWeatherService(null, clock);

		URI uri = service.buildOpenMeteoUri(new LiveWeatherService.LiveWeatherRequest(
				35.0, -76.0, LocalDate.of(2026, 3, 20), LocalTime.of(14, 0)));

		assertTrue(uri.toString().startsWith("https://archive-api.open-meteo.com/v1/archive"));
		assertTrue(uri.toString().contains("wind_speed_unit=ms"));
	}

	@Test
	public void testParseOpenMeteoResponseMapsLevels() throws Exception {
		Clock clock = Clock.fixed(Instant.parse("2026-04-01T12:00:00Z"), ZoneOffset.UTC);
		LiveWeatherService service = new LiveWeatherService(null, clock);
		LiveWeatherService.LiveWeatherRequest request = new LiveWeatherService.LiveWeatherRequest(
				35.0, -76.0, LocalDate.of(2026, 4, 1), LocalTime.of(14, 0));

		String json = """
				{
				  "hourly": {
				    "time": ["2026-04-01T13:00", "2026-04-01T14:00"],
				    "temperature_2m": [17.5, 18.2],
				    "wind_speed_10m": [1.0, 2.0],
				    "wind_direction_10m": [90, 180],
				    "wind_speed_80m": [3.0, 4.0],
				    "wind_direction_80m": [100, 190],
				    "wind_speed_1000hPa": [5.0, 6.0],
				    "wind_direction_1000hPa": [110, 200],
				    "wind_speed_975hPa": [7.0, 8.0],
				    "wind_direction_975hPa": [120, 210],
				    "wind_speed_950hPa": [9.0, 10.0],
				    "wind_direction_950hPa": [130, 220],
				    "wind_speed_925hPa": [11.0, 12.0],
				    "wind_direction_925hPa": [140, 230],
				    "wind_speed_900hPa": [13.0, 14.0],
				    "wind_direction_900hPa": [150, 240],
				    "wind_speed_875hPa": [15.0, 16.0],
				    "wind_direction_875hPa": [160, 250],
				    "wind_speed_850hPa": [17.0, 18.0],
				    "wind_direction_850hPa": [170, 260],
				    "wind_speed_825hPa": [19.0, 20.0],
				    "wind_direction_825hPa": [180, 270],
				    "wind_speed_800hPa": [21.0, 22.0],
				    "wind_direction_800hPa": [190, 280],
				    "wind_speed_775hPa": [23.0, 24.0],
				    "wind_direction_775hPa": [200, 290],
				    "wind_speed_750hPa": [25.0, 26.0],
				    "wind_direction_750hPa": [210, 300],
				    "wind_speed_725hPa": [27.0, 28.0],
				    "wind_direction_725hPa": [220, 310],
				    "wind_speed_700hPa": [29.0, 30.0],
				    "wind_direction_700hPa": [230, 320],
				    "wind_speed_675hPa": [31.0, 32.0],
				    "wind_direction_675hPa": [240, 330],
				    "wind_speed_650hPa": [33.0, 34.0],
				    "wind_direction_650hPa": [250, 340],
				    "wind_speed_625hPa": [35.0, 36.0],
				    "wind_direction_625hPa": [260, 350],
				    "wind_speed_600hPa": [37.0, 38.0],
				    "wind_direction_600hPa": [270, 0],
				    "wind_speed_575hPa": [39.0, 40.0],
				    "wind_direction_575hPa": [280, 10],
				    "wind_speed_550hPa": [41.0, 42.0],
				    "wind_direction_550hPa": [290, 20],
				    "wind_speed_525hPa": [43.0, 44.0],
				    "wind_direction_525hPa": [300, 30],
				    "wind_speed_500hPa": [45.0, 46.0],
				    "wind_direction_500hPa": [310, 40],
				    "wind_speed_475hPa": [47.0, 48.0],
				    "wind_direction_475hPa": [320, 50],
				    "wind_speed_450hPa": [49.0, 50.0],
				    "wind_direction_450hPa": [330, 60],
				    "wind_speed_425hPa": [51.0, 52.0],
				    "wind_direction_425hPa": [340, 70],
				    "wind_speed_400hPa": [53.0, 54.0],
				    "wind_direction_400hPa": [350, 80],
				    "wind_speed_375hPa": [55.0, 56.0],
				    "wind_direction_375hPa": [0, 90],
				    "wind_speed_350hPa": [57.0, 58.0],
				    "wind_direction_350hPa": [10, 100],
				    "wind_speed_325hPa": [59.0, 60.0],
				    "wind_direction_325hPa": [20, 110],
				    "wind_speed_300hPa": [61.0, 62.0],
				    "wind_direction_300hPa": [30, 120],
				    "wind_speed_275hPa": [63.0, 64.0],
				    "wind_direction_275hPa": [40, 130],
				    "wind_speed_250hPa": [65.0, 66.0],
				    "wind_direction_250hPa": [50, 140],
				    "wind_speed_225hPa": [67.0, 68.0],
				    "wind_direction_225hPa": [60, 150],
				    "wind_speed_200hPa": [69.0, 70.0],
				    "wind_direction_200hPa": [70, 160],
				    "wind_speed_175hPa": [71.0, 72.0],
				    "wind_direction_175hPa": [80, 170],
				    "wind_speed_150hPa": [73.0, 74.0],
				    "wind_direction_150hPa": [90, 180],
				    "wind_speed_125hPa": [75.0, 76.0],
				    "wind_direction_125hPa": [100, 190],
				    "wind_speed_100hPa": [77.0, 78.0],
				    "wind_direction_100hPa": [110, 200],
				    "wind_speed_70hPa": [79.0, 80.0],
				    "wind_direction_70hPa": [120, 210],
				    "wind_speed_50hPa": [81.0, 82.0],
				    "wind_direction_50hPa": [130, 220],
				    "wind_speed_40hPa": [83.0, 84.0],
				    "wind_direction_40hPa": [140, 230],
				    "wind_speed_30hPa": [85.0, 86.0],
				    "wind_direction_30hPa": [150, 240],
				    "wind_speed_20hPa": [87.0, 88.0],
				    "wind_direction_20hPa": [160, 250],
				    "wind_speed_15hPa": [89.0, 90.0],
				    "wind_direction_15hPa": [170, 260],
				    "wind_speed_10hPa": [91.0, 92.0],
				    "wind_direction_10hPa": [180, 270]
				  }
				}
				""";

		LiveWeatherService.LiveWeatherResult result = service.parseOpenMeteoResponse(json, request);

		assertEquals("Open-Meteo", result.source());
		assertEquals(46, result.levels().size());
		assertEquals(10, result.levels().get(0).altitudeMeters());
		assertEquals(2.0, result.levels().get(0).speedMetersPerSecond(), 1e-9);
		assertEquals(180.0, result.levels().get(0).directionDegrees(), 1e-9);
		assertEquals(26000, result.levels().get(result.levels().size() - 1).altitudeMeters());
		assertEquals(18.2, result.surfaceTemperatureC(), 1e-9);
	}

	@Test
	public void testApplyLevelsToModelReplacesExistingLevels() {
		MultiLevelPinkNoiseWindModel model = new MultiLevelPinkNoiseWindModel();
		model.addWindLevel(100, 3.0, 0.2);

		LiveWeatherService.applyLevelsToModel(model, List.of(
				new LiveWeatherService.LiveWeatherLevel(10, 2.5, 180.0),
				new LiveWeatherService.LiveWeatherLevel(80, 4.0, 190.0)));

		assertEquals(2, model.getLevels().size());
		assertEquals(10.0, model.getLevels().get(0).getAltitude(), 1e-9);
		assertEquals(2.5, model.getLevels().get(0).getSpeed(), 1e-9);
		assertEquals(Math.toRadians(180.0), model.getLevels().get(0).getDirection(), 1e-9);
	}
}
