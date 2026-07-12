package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

public final class RasaeroCoefficientFrameAdapter {
	public Result fromBodyAxes(double ca, double cn, double alphaRad) {
		return new Result(ca * Math.cos(alphaRad) + cn * Math.sin(alphaRad),
				cn * Math.cos(alphaRad) - ca * Math.sin(alphaRad));
	}
	public record Result(double cd, double cl) {}
}
