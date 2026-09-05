package info.openrocket.core.util.geospatial;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Loads a launch-site scene and publishes immutable progressive updates. */
public interface LaunchSiteSceneProvider extends AutoCloseable {
	CompletableFuture<LaunchSiteScene> load(LaunchSiteSceneRequest request,
			LaunchSiteScene initialScene, Consumer<LaunchSiteScene> progressListener);

	@Override
	void close();
}
