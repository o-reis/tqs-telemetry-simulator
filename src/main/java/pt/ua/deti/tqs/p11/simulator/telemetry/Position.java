package pt.ua.deti.tqs.p11.simulator.telemetry;

/** WGS84 position in decimal degrees. */
public record Position(double lat, double lon) {

  public Position {
    if (!(lat >= -90 && lat <= 90)) {
      throw new IllegalArgumentException("Latitude must be between -90 and 90: " + lat);
    }
    if (!(lon >= -180 && lon <= 180)) {
      throw new IllegalArgumentException("Longitude must be between -180 and 180: " + lon);
    }
  }
}
