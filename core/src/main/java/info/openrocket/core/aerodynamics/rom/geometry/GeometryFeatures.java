package info.openrocket.core.aerodynamics.rom.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GeometryFeatures {
	private final String geometryHash;
	private final double bodyLength;
	private final double referenceLength;
	private final double referenceArea;
	private final double maxRadius;
	private final double maxDiameter;
	private final double baseArea;
	private final int shoulderCount;
	private final int boattailCount;
	private final int slopeChangeCount;
	private final double[] xStations;
	private final double[] radiusStations;
	private final double[] areaStations;
	private final double[] areaSlope;
	private final double[] areaCurvature;
	private final List<AxisymmetricGeometry> axisymmetricBodies;
	private final List<FinGeometry> fins;
	private final List<SurfacePatch> surfacePatches;

	public GeometryFeatures(String geometryHash, double bodyLength, double referenceLength, double referenceArea,
			double maxRadius, double maxDiameter, double baseArea, int shoulderCount, int boattailCount,
			int slopeChangeCount, double[] xStations, double[] radiusStations, double[] areaStations,
			double[] areaSlope, double[] areaCurvature, List<AxisymmetricGeometry> axisymmetricBodies,
			List<FinGeometry> fins, List<SurfacePatch> surfacePatches) {
		this.geometryHash = geometryHash;
		this.bodyLength = bodyLength;
		this.referenceLength = referenceLength;
		this.referenceArea = referenceArea;
		this.maxRadius = maxRadius;
		this.maxDiameter = maxDiameter;
		this.baseArea = baseArea;
		this.shoulderCount = shoulderCount;
		this.boattailCount = boattailCount;
		this.slopeChangeCount = slopeChangeCount;
		this.xStations = xStations.clone();
		this.radiusStations = radiusStations.clone();
		this.areaStations = areaStations.clone();
		this.areaSlope = areaSlope.clone();
		this.areaCurvature = areaCurvature.clone();
		this.axisymmetricBodies = Collections.unmodifiableList(new ArrayList<>(axisymmetricBodies));
		this.fins = Collections.unmodifiableList(new ArrayList<>(fins));
		this.surfacePatches = Collections.unmodifiableList(new ArrayList<>(surfacePatches));
	}

	public String getGeometryHash() {
		return geometryHash;
	}

	public double getBodyLength() {
		return bodyLength;
	}

	public double getReferenceLength() {
		return referenceLength;
	}

	public double getReferenceArea() {
		return referenceArea;
	}

	public double getMaxRadius() {
		return maxRadius;
	}

	public double getMaxDiameter() {
		return maxDiameter;
	}

	public double getBaseArea() {
		return baseArea;
	}

	public int getShoulderCount() {
		return shoulderCount;
	}

	public int getBoattailCount() {
		return boattailCount;
	}

	public int getSlopeChangeCount() {
		return slopeChangeCount;
	}

	public double[] getXStations() {
		return xStations.clone();
	}

	public double[] getRadiusStations() {
		return radiusStations.clone();
	}

	public double[] getAreaStations() {
		return areaStations.clone();
	}

	public double[] getAreaSlope() {
		return areaSlope.clone();
	}

	public double[] getAreaCurvature() {
		return areaCurvature.clone();
	}

	public List<AxisymmetricGeometry> getAxisymmetricBodies() {
		return axisymmetricBodies;
	}

	public List<FinGeometry> getFins() {
		return fins;
	}

	public List<SurfacePatch> getSurfacePatches() {
		return surfacePatches;
	}

	public double getTotalFinPlanformArea() {
		double area = 0.0;
		for (FinGeometry fin : fins) {
			area += fin.getPlanformArea() * fin.getFinCount();
		}
		return area;
	}

	public double radiusAt(double x) {
		return interpolate(xStations, radiusStations, x);
	}

	public double areaSlopeAt(double x) {
		return interpolate(xStations, areaSlope, x);
	}

	public double areaCurvatureAt(double x) {
		return interpolate(xStations, areaCurvature, x);
	}

	private static double interpolate(double[] xAxis, double[] values, double query) {
		if (xAxis.length == 0) {
			return 0.0;
		}
		if (query <= xAxis[0]) {
			return values[0];
		}
		int last = xAxis.length - 1;
		if (query >= xAxis[last]) {
			return values[last];
		}
		for (int i = 1; i < xAxis.length; i++) {
			if (query <= xAxis[i]) {
				double x0 = xAxis[i - 1];
				double x1 = xAxis[i];
				double t = (query - x0) / Math.max(1e-9, x1 - x0);
				return values[i - 1] + t * (values[i] - values[i - 1]);
			}
		}
		return values[last];
	}
}
