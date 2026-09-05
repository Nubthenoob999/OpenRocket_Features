package info.openrocket.core.util.geospatial;

import java.util.concurrent.atomic.AtomicBoolean;

/** Immutable request plus a thread-safe cancellation handle for one scenery load. */
public final class LaunchSiteSceneRequest {
	private final Wgs84.GeoPoint launchSite;
	private final double halfExtentMeters;
	private final int terrainGridSize;
	private final int imagerySize;
	private final double featureRadiusMeters;
	private final AtomicBoolean cancelled = new AtomicBoolean();

	public LaunchSiteSceneRequest(Wgs84.GeoPoint launchSite, double halfExtentMeters,
			int terrainGridSize, int imagerySize, double featureRadiusMeters) {
		if (launchSite == null || !Double.isFinite(launchSite.latitudeDeg())
				|| !Double.isFinite(launchSite.longitudeDeg())
				|| launchSite.latitudeDeg() < -90 || launchSite.latitudeDeg() > 90) {
			throw new IllegalArgumentException("Invalid launch coordinates");
		}
		this.launchSite = launchSite;
		this.halfExtentMeters = Math.max(1.0, halfExtentMeters);
		this.terrainGridSize = Math.max(3, terrainGridSize | 1);
		this.imagerySize = Math.max(256, imagerySize);
		this.featureRadiusMeters = Math.max(0, Math.min(featureRadiusMeters, halfExtentMeters));
	}

	public Wgs84.GeoPoint launchSite() { return launchSite; }
	public double halfExtentMeters() { return halfExtentMeters; }
	public int terrainGridSize() { return terrainGridSize; }
	public int imagerySize() { return imagerySize; }
	public double featureRadiusMeters() { return featureRadiusMeters; }
	public boolean isCancelled() { return cancelled.get(); }
	public void cancel() { cancelled.set(true); }
	public void checkCancelled() throws InterruptedException {
		if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Scene load cancelled");
	}
}
