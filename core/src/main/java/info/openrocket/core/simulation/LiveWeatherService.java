package info.openrocket.core.simulation;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;
import info.openrocket.core.models.wind.WindModel;

public class LiveWeatherService {
	private static final DateTimeFormatter REQUEST_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter FETCHED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
			.withZone(ZoneId.systemDefault());
	private static final String FORECAST_BASE_URL = "https://api.open-meteo.com/v1/forecast";
	private static final String ARCHIVE_BASE_URL = "https://archive-api.open-meteo.com/v1/archive";
	private static final String SOURCE_LABEL = "Open-Meteo";
	private static final List<PressureLevel> PRESSURE_LEVELS = List.of(
			new PressureLevel(1013, 10, "_10m"),
			new PressureLevel(1004, 80, "_80m"),
			new PressureLevel(1000, 110, "_1000hPa"),
			new PressureLevel(975, 320, "_975hPa"),
			new PressureLevel(950, 500, "_950hPa"),
			new PressureLevel(925, 800, "_925hPa"),
			new PressureLevel(900, 1000, "_900hPa"),
			new PressureLevel(875, 1200, "_875hPa"),
			new PressureLevel(850, 1500, "_850hPa"),
			new PressureLevel(825, 1700, "_825hPa"),
			new PressureLevel(800, 1900, "_800hPa"),
			new PressureLevel(775, 2200, "_775hPa"),
			new PressureLevel(750, 2500, "_750hPa"),
			new PressureLevel(725, 2700, "_725hPa"),
			new PressureLevel(700, 3000, "_700hPa"),
			new PressureLevel(675, 3300, "_675hPa"),
			new PressureLevel(650, 3600, "_650hPa"),
			new PressureLevel(625, 3900, "_625hPa"),
			new PressureLevel(600, 4200, "_600hPa"),
			new PressureLevel(575, 4500, "_575hPa"),
			new PressureLevel(550, 4900, "_550hPa"),
			new PressureLevel(525, 5100, "_525hPa"),
			new PressureLevel(500, 5600, "_500hPa"),
			new PressureLevel(475, 6000, "_475hPa"),
			new PressureLevel(450, 6300, "_450hPa"),
			new PressureLevel(425, 6800, "_425hPa"),
			new PressureLevel(400, 7200, "_400hPa"),
			new PressureLevel(375, 7600, "_375hPa"),
			new PressureLevel(350, 8100, "_350hPa"),
			new PressureLevel(325, 8600, "_325hPa"),
			new PressureLevel(300, 9200, "_300hPa"),
			new PressureLevel(275, 9700, "_275hPa"),
			new PressureLevel(250, 10400, "_250hPa"),
			new PressureLevel(225, 11000, "_225hPa"),
			new PressureLevel(200, 11800, "_200hPa"),
			new PressureLevel(175, 12600, "_175hPa"),
			new PressureLevel(150, 13500, "_150hPa"),
			new PressureLevel(125, 14600, "_125hPa"),
			new PressureLevel(100, 15800, "_100hPa"),
			new PressureLevel(70, 17700, "_70hPa"),
			new PressureLevel(50, 19300, "_50hPa"),
			new PressureLevel(40, 20000, "_40hPa"),
			new PressureLevel(30, 22000, "_30hPa"),
			new PressureLevel(20, 23000, "_20hPa"),
			new PressureLevel(15, 24000, "_15hPa"),
			new PressureLevel(10, 26000, "_10hPa"));

	private final HttpClient httpClient;
	private final Clock clock;

	public LiveWeatherService() {
		this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(), Clock.systemDefaultZone());
	}

	LiveWeatherService(HttpClient httpClient, Clock clock) {
		this.httpClient = httpClient;
		this.clock = clock;
	}

	public LiveWeatherResult fetchWeather(LiveWeatherRequest request) throws LiveWeatherException {
		validateRequest(request);
		HttpRequest httpRequest = HttpRequest.newBuilder(buildOpenMeteoUri(request))
				.header("Accept", "application/json")
				.GET()
				.build();

		HttpResponse<String> response;
		try {
			response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new LiveWeatherException("Unable to fetch live weather data.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LiveWeatherException("Live weather request was interrupted.", e);
		}

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new LiveWeatherException("Live weather request failed with HTTP " + response.statusCode() + ".");
		}

		return parseOpenMeteoResponse(response.body(), request);
	}

	public static void applyLevelsToModel(MultiLevelPinkNoiseWindModel model, List<LiveWeatherLevel> levels) {
		model.clearLevels();
		model.setAltitudeReference(WindModel.AltitudeReference.MSL);
		for (LiveWeatherLevel level : levels) {
			model.addWindLevel(level.altitudeMeters(), level.speedMetersPerSecond(),
					Math.toRadians(level.directionDegrees()));
		}
		model.sortLevels();
	}

	URI buildOpenMeteoUri(LiveWeatherRequest request) {
		String baseUrl = request.date().isBefore(LocalDate.now(clock).minusDays(7))
				? ARCHIVE_BASE_URL
				: FORECAST_BASE_URL;

		List<String> hourlyFields = new ArrayList<>();
		hourlyFields.add("temperature_2m");
		for (PressureLevel level : PRESSURE_LEVELS) {
			hourlyFields.add("wind_speed" + level.suffix());
			hourlyFields.add("wind_direction" + level.suffix());
		}

		StringBuilder builder = new StringBuilder(baseUrl);
		builder.append("?latitude=").append(encode(Double.toString(request.latitude())));
		builder.append("&longitude=").append(encode(Double.toString(request.longitude())));
		builder.append("&start_date=").append(encode(request.date().toString()));
		builder.append("&end_date=").append(encode(request.date().toString()));
		builder.append("&timezone=auto");
		builder.append("&wind_speed_unit=ms");
		builder.append("&hourly=").append(encode(String.join(",", hourlyFields)));
		return URI.create(builder.toString());
	}

	LiveWeatherResult parseOpenMeteoResponse(String responseBody, LiveWeatherRequest request) throws LiveWeatherException {
		JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
		JsonObject hourly = getRequiredObject(root, "hourly");
		JsonArray times = getRequiredArray(hourly, "time");
		String targetTimestamp = request.date() + "T" + request.time().format(REQUEST_TIME_FORMAT);
		int selectedIndex = findTimeIndex(times, targetTimestamp);
		if (selectedIndex < 0) {
			throw new LiveWeatherException("No wind data was returned for the selected launch time.");
		}

		List<LiveWeatherLevel> levels = new ArrayList<>();
		for (PressureLevel level : PRESSURE_LEVELS) {
			double speed = getDoubleAt(hourly, "wind_speed" + level.suffix(), selectedIndex);
			double direction = getDoubleAt(hourly, "wind_direction" + level.suffix(), selectedIndex);
			levels.add(new LiveWeatherLevel(level.altitudeMeters(), speed, direction));
		}
		if (levels.isEmpty()) {
			throw new LiveWeatherException("No wind levels were available from the weather provider.");
		}

		Double surfaceTemperatureC = null;
		JsonArray temperatures = hourly.getAsJsonArray("temperature_2m");
		if (temperatures != null && selectedIndex < temperatures.size() && !temperatures.get(selectedIndex).isJsonNull()) {
			surfaceTemperatureC = temperatures.get(selectedIndex).getAsDouble();
		}

		return new LiveWeatherResult(levels, SOURCE_LABEL, surfaceTemperatureC, Instant.now(clock), true);
	}

	private void validateRequest(LiveWeatherRequest request) throws LiveWeatherException {
		Objects.requireNonNull(request, "request");
		if (Double.isNaN(request.latitude()) || request.latitude() < -90 || request.latitude() > 90) {
			throw new LiveWeatherException("Latitude must be between -90 and 90 degrees.");
		}
		if (Double.isNaN(request.longitude()) || request.longitude() < -180 || request.longitude() > 180) {
			throw new LiveWeatherException("Longitude must be between -180 and 180 degrees.");
		}
		if (request.date() == null) {
			throw new LiveWeatherException("Launch date is required.");
		}
		if (request.time() == null) {
			throw new LiveWeatherException("Launch time is required.");
		}
	}

	private static JsonObject getRequiredObject(JsonObject object, String key) throws LiveWeatherException {
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonObject()) {
			throw new LiveWeatherException("Weather provider response is missing '" + key + "'.");
		}
		return element.getAsJsonObject();
	}

	private static JsonArray getRequiredArray(JsonObject object, String key) throws LiveWeatherException {
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonArray()) {
			throw new LiveWeatherException("Weather provider response is missing '" + key + "'.");
		}
		return element.getAsJsonArray();
	}

	private static int findTimeIndex(JsonArray times, String targetTimestamp) {
		for (int i = 0; i < times.size(); i++) {
			if (targetTimestamp.equals(times.get(i).getAsString())) {
				return i;
			}
		}
		return -1;
	}

	private static double getDoubleAt(JsonObject hourly, String fieldName, int index) throws LiveWeatherException {
		JsonArray array = getRequiredArray(hourly, fieldName);
		if (index >= array.size()) {
			throw new LiveWeatherException("Weather provider response is missing values for '" + fieldName + "'.");
		}
		JsonElement value = array.get(index);
		if (value == null || value.isJsonNull()) {
			throw new LiveWeatherException("Weather provider returned an empty value for '" + fieldName + "'.");
		}
		return value.getAsDouble();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private record PressureLevel(int hpa, int altitudeMeters, String suffix) {
	}

	public record LiveWeatherRequest(double latitude, double longitude, LocalDate date, LocalTime time) {
	}

	public record LiveWeatherLevel(int altitudeMeters, double speedMetersPerSecond, double directionDegrees) {
	}

	public record LiveWeatherResult(List<LiveWeatherLevel> levels, String source, Double surfaceTemperatureC,
			Instant fetchedAt, boolean altitudeReferenceIsMsl) {
		public LiveWeatherResult {
			levels = List.copyOf(levels);
		}

		public String fetchedAtLabel() {
			return FETCHED_AT_FORMAT.format(fetchedAt);
		}
	}

	public static class LiveWeatherException extends Exception {
		public LiveWeatherException(String message) {
			super(message);
		}

		public LiveWeatherException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
