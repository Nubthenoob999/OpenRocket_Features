package info.openrocket.core.util.geospatial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class OpenDataLaunchSiteSceneProviderTest {
	@Test
	void decodesTerrariumPixels() {
		assertEquals(0.0, OpenDataLaunchSiteSceneProvider.decodeTerrarium(0x800000), 1e-9);
		assertEquals(1.5, OpenDataLaunchSiteSceneProvider.decodeTerrarium(0x800180), 1e-9);
	}

	@Test
	void derivesBuildingAndRoadDimensions() {
		JsonObject building = new JsonObject();
		building.addProperty("building:levels", "4");
		assertEquals(12.0, OpenDataLaunchSiteSceneProvider.buildingHeight(building), 1e-9);
		building.addProperty("height", "30 ft");
		assertEquals(9.144, OpenDataLaunchSiteSceneProvider.buildingHeight(building), 1e-9);

		JsonObject road = new JsonObject();
		road.addProperty("lanes", "2");
		assertEquals(7.0, OpenDataLaunchSiteSceneProvider.roadWidth(road, "residential"), 1e-9);
	}
}
