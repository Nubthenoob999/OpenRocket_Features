package info.openrocket.core.aerodynamics.physicsaero.geometry;

public record ProtuberanceGeometry(String type, double lengthM, double projectedAreaM2, double frontalAreaM2) {
	public ProtuberanceGeometry { if (lengthM <= 0 || projectedAreaM2 < 0 || frontalAreaM2 < 0) throw new IllegalArgumentException("invalid protuberance geometry"); }
}
