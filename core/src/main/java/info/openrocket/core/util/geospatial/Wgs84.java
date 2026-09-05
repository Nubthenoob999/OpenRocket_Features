package info.openrocket.core.util.geospatial;

/** WGS84 geodetic/ECEF/local-ENU transforms used by coordinate-centred scenery. */
public final class Wgs84 {
	private static final double A = 6378137.0;
	private static final double E2 = 6.69437999014e-3;

	private Wgs84() { }

	public record GeoPoint(double latitudeDeg, double longitudeDeg, double altitudeMeters) { }
	public record EnuPoint(double eastMeters, double northMeters, double upMeters) { }
	public record Bounds(double southDeg, double westDeg, double northDeg, double eastDeg,
			boolean crossesAntimeridian) { }

	public static EnuPoint toEnu(GeoPoint origin, GeoPoint point) {
		double[] o = ecef(origin);
		double[] p = ecef(point);
		double dx = p[0] - o[0];
		double dy = p[1] - o[1];
		double dz = p[2] - o[2];
		double lat = Math.toRadians(origin.latitudeDeg());
		double lon = Math.toRadians(origin.longitudeDeg());
		double sinLat = Math.sin(lat), cosLat = Math.cos(lat);
		double sinLon = Math.sin(lon), cosLon = Math.cos(lon);
		return new EnuPoint(
				-sinLon * dx + cosLon * dy,
				-sinLat * cosLon * dx - sinLat * sinLon * dy + cosLat * dz,
				cosLat * cosLon * dx + cosLat * sinLon * dy + sinLat * dz);
	}

	public static GeoPoint fromEnu(GeoPoint origin, EnuPoint point) {
		double lat = Math.toRadians(origin.latitudeDeg());
		double lon = Math.toRadians(origin.longitudeDeg());
		double sinLat = Math.sin(lat), cosLat = Math.cos(lat);
		double sinLon = Math.sin(lon), cosLon = Math.cos(lon);
		double[] o = ecef(origin);
		double x = o[0] - sinLon * point.eastMeters()
				- sinLat * cosLon * point.northMeters() + cosLat * cosLon * point.upMeters();
		double y = o[1] + cosLon * point.eastMeters()
				- sinLat * sinLon * point.northMeters() + cosLat * sinLon * point.upMeters();
		double z = o[2] + cosLat * point.northMeters() + sinLat * point.upMeters();
		return fromEcef(x, y, z);
	}

	public static Bounds bounds(GeoPoint origin, double halfExtentMeters) {
		GeoPoint sw = fromEnu(origin, new EnuPoint(-halfExtentMeters, -halfExtentMeters, 0));
		GeoPoint ne = fromEnu(origin, new EnuPoint(halfExtentMeters, halfExtentMeters, 0));
		double west = normalizeLongitude(sw.longitudeDeg());
		double east = normalizeLongitude(ne.longitudeDeg());
		return new Bounds(sw.latitudeDeg(), west, ne.latitudeDeg(), east, west > east);
	}

	public static double normalizeLongitude(double longitudeDeg) {
		double value = longitudeDeg % 360.0;
		if (value <= -180.0) value += 360.0;
		if (value > 180.0) value -= 360.0;
		return value;
	}

	private static double[] ecef(GeoPoint point) {
		double lat = Math.toRadians(point.latitudeDeg());
		double lon = Math.toRadians(point.longitudeDeg());
		double sinLat = Math.sin(lat), cosLat = Math.cos(lat);
		double n = A / Math.sqrt(1.0 - E2 * sinLat * sinLat);
		double r = n + point.altitudeMeters();
		return new double[] {
				r * cosLat * Math.cos(lon),
				r * cosLat * Math.sin(lon),
				(n * (1.0 - E2) + point.altitudeMeters()) * sinLat
		};
	}

	private static GeoPoint fromEcef(double x, double y, double z) {
		double lon = Math.atan2(y, x);
		double p = Math.hypot(x, y);
		double lat = Math.atan2(z, p * (1.0 - E2));
		double altitude = 0;
		for (int i = 0; i < 8; i++) {
			double sinLat = Math.sin(lat);
			double n = A / Math.sqrt(1.0 - E2 * sinLat * sinLat);
			altitude = p / Math.max(1e-12, Math.cos(lat)) - n;
			lat = Math.atan2(z, p * (1.0 - E2 * n / (n + altitude)));
		}
		return new GeoPoint(Math.toDegrees(lat), normalizeLongitude(Math.toDegrees(lon)), altitude);
	}
}
