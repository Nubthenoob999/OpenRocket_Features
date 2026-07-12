package info.openrocket.core.aerodynamics.physicsaero.thermal;
@FunctionalInterface public interface WallTemperatureProvider { double temperatureK(double surfaceDistanceM, double recoveryTemperatureK); }
