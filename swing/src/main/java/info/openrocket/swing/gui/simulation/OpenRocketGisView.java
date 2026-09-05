package info.openrocket.swing.gui.simulation;

import java.awt.Color;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.style.Style;
import org.geotools.data.DataUtilities;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.map.FeatureLayer;
import org.geotools.ows.wms.WebMapServer;
import org.geotools.ows.wms.map.WMSLayer;
import org.geotools.styling.StyleBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import info.openrocket.core.util.BuildProperties;

/**
 * GeoTools 35.x adapters used by the Monte Carlo map.
 *
 * <p>The feature sources deliberately use EPSG:4326. GeoTools reprojects them into the map
 * viewport CRS, while the map panel retains Web Mercator for predictable interactive zooming.</p>
 */
final class OpenRocketGisView {
	static {
		configureEnvironment();
	}

	static final SimpleFeatureType POINT_TYPE = featureType(
			"LandingData", "the_geom:Point:srid=4326", Point.class);
	static final SimpleFeatureType LINE_TYPE = featureType(
			"EllipseLineData", "the_geom:LineString:srid=4326", LineString.class);
	static final SimpleFeatureType POLYGON_TYPE = featureType(
			"EllipseData", "the_geom:Polygon:srid=4326", Polygon.class);

	private OpenRocketGisView() { }

	static void configureEnvironment() {
		System.setProperty("org.geotools.referencing.forceXY", "true");
		System.setProperty("jsse.enableSNIExtension", "true");
		if (System.getProperty("http.agent") == null) {
			System.setProperty("http.agent", "Project-Imperia/" + BuildProperties.getVersion());
		}
	}

	static FeatureLayer featureLayer(String title, SimpleFeatureType type,
			List<? extends Geometry> geometries, Style style) {
		DefaultFeatureCollection collection = new DefaultFeatureCollection(title, type);
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
		for (int index = 0; index < geometries.size(); index++) {
			builder.add(geometries.get(index));
			collection.add(builder.buildFeature(type.getTypeName() + "." + index));
			builder.reset();
		}

		FeatureLayer layer = new FeatureLayer(new CollectionFeatureSource(collection), style);
		layer.setTitle(title);
		return layer;
	}

	static WMSLayer baseMapLayer(WebMapServer server, org.geotools.ows.wms.Layer layer) {
		WMSLayer baseMap = new WMSLayer(server, layer, "default", "image/png");
		baseMap.setTitle("USGS Topographic Basemap");
		return baseMap;
	}

	static Style polygonStyle(Color outline, float width) {
		StyleBuilder styles = new StyleBuilder();
		return styles.createStyle(styles.createPolygonSymbolizer(
				styles.createStroke(outline, width), styles.createFill(outline, 35.0 / 255.0)));
	}

	private static SimpleFeatureType featureType(String name, String specification,
			Class<? extends Geometry> expectedGeometry) {
		try {
			SimpleFeatureType type = DataUtilities.createType(name, specification);
			Class<?> actualGeometry = type.getGeometryDescriptor().getType().getBinding();
			if (!expectedGeometry.equals(actualGeometry)) {
				throw new IllegalStateException("GeoTools created " + actualGeometry.getName()
						+ " instead of " + expectedGeometry.getName());
			}
			return type;
		} catch (Exception exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}
}

