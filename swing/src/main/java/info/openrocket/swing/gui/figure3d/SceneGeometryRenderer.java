package info.openrocket.swing.gui.figure3d;

import java.util.List;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;

import info.openrocket.core.util.TerrainFetcher.TerrainData;
import info.openrocket.core.util.geospatial.LaunchSiteScene.Building;
import info.openrocket.core.util.geospatial.LaunchSiteScene.LocalPoint;
import info.openrocket.core.util.geospatial.LaunchSiteScene.Road;

/** Draws terrain-draped roads and footprint-extruded OpenStreetMap buildings. */
public final class SceneGeometryRenderer {
	private SceneGeometryRenderer() { }

	public static void renderRoads(GL2 gl, TerrainData terrain, List<Road> roads) {
		if (terrain == null || roads == null || roads.isEmpty()) return;
		gl.glDisable(GL.GL_CULL_FACE);
		gl.glEnable(GL2.GL_COLOR_MATERIAL);
		gl.glColorMaterial(GL.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE);
		gl.glColor3f(0.22f, 0.23f, 0.24f);
		gl.glNormal3d(0, 1, 0);
		for (Road road : roads) {
			List<LocalPoint> points = road.points();
			for (int i = 0; i + 1 < points.size(); i++) {
				LocalPoint a = points.get(i), b = points.get(i + 1);
				double dx = b.eastMeters() - a.eastMeters();
				double dn = b.northMeters() - a.northMeters();
				double length = Math.hypot(dx, dn);
				if (length < 0.01) continue;
				double ox = -dn / length * road.widthMeters() * 0.5;
				double on = dx / length * road.widthMeters() * 0.5;
				emitRoadQuad(gl, terrain, a.eastMeters(), a.northMeters(), b.eastMeters(), b.northMeters(), ox, on);
			}
		}
		gl.glDisable(GL2.GL_COLOR_MATERIAL);
		gl.glEnable(GL.GL_CULL_FACE);
	}

	private static void emitRoadQuad(GL2 gl, TerrainData terrain, double ax, double an,
			double bx, double bn, double ox, double on) {
		gl.glBegin(GL2.GL_QUADS);
		emitDraped(gl, terrain, ax + ox, an + on, 0.12);
		emitDraped(gl, terrain, ax - ox, an - on, 0.12);
		emitDraped(gl, terrain, bx - ox, bn - on, 0.12);
		emitDraped(gl, terrain, bx + ox, bn + on, 0.12);
		gl.glEnd();
	}

	public static void renderBuildings(GL2 gl, TerrainData terrain, List<Building> buildings) {
		if (terrain == null || buildings == null || buildings.isEmpty()) return;
		gl.glDisable(GL.GL_CULL_FACE);
		gl.glEnable(GL2.GL_COLOR_MATERIAL);
		gl.glColorMaterial(GL.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE);
		for (Building building : buildings) {
			drawWalls(gl, terrain, building);
			drawRoof(gl, terrain, building);
		}
		gl.glDisable(GL2.GL_COLOR_MATERIAL);
		gl.glEnable(GL.GL_CULL_FACE);
	}

	private static void drawWalls(GL2 gl, TerrainData terrain, Building building) {
		List<LocalPoint> outline = building.outline();
		gl.glColor3f(0.65f, 0.62f, 0.57f);
		gl.glBegin(GL2.GL_QUADS);
		for (int i = 0; i + 1 < outline.size(); i++) {
			LocalPoint a = outline.get(i), b = outline.get(i + 1);
			double dx = b.eastMeters() - a.eastMeters();
			double dz = -(b.northMeters() - a.northMeters());
			double length = Math.hypot(dx, dz);
			if (length < 0.01) continue;
			gl.glNormal3d(dz / length, 0, -dx / length);
			double ay = terrain.relativeElevationAt(a.eastMeters(), a.northMeters());
			double by = terrain.relativeElevationAt(b.eastMeters(), b.northMeters());
			gl.glVertex3d(a.eastMeters(), ay, -a.northMeters());
			gl.glVertex3d(b.eastMeters(), by, -b.northMeters());
			gl.glVertex3d(b.eastMeters(), by + building.heightMeters(), -b.northMeters());
			gl.glVertex3d(a.eastMeters(), ay + building.heightMeters(), -a.northMeters());
		}
		gl.glEnd();
	}

	private static void drawRoof(GL2 gl, TerrainData terrain, Building building) {
		List<LocalPoint> triangles = building.roofTriangles();
		if (triangles.isEmpty()) return;
		gl.glColor3f(0.46f, 0.43f, 0.40f);
		gl.glNormal3d(0, 1, 0);
		gl.glBegin(GL2.GL_TRIANGLES);
		for (LocalPoint point : triangles) {
			double y = terrain.relativeElevationAt(point.eastMeters(), point.northMeters()) + building.heightMeters();
			gl.glVertex3d(point.eastMeters(), y, -point.northMeters());
		}
		gl.glEnd();
	}

	private static void emitDraped(GL2 gl, TerrainData terrain, double east, double north, double offset) {
		gl.glVertex3d(east, terrain.relativeElevationAt(east, north) + offset, -north);
	}
}
