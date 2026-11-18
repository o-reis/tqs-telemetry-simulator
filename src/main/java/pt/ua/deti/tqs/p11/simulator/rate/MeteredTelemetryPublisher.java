package pt.ua.deti.tqs.p11.simulator.rate;

import java.util.concurrent.CompletableFuture;
import pt.ua.deti.tqs.p11.simulator.telemetry.TelemetryPublisher;
import pt.ua.deti.tqs.p11.simulator.telemetry.VehicleTelemetry;

/**
 * Decorator: publishes through another {@link TelemetryPublisher} and counts every reading as sent,
 * then as acknowledged or failed when the broker answers. The engine does not change.
 */
public class MeteredTelemetryPublisher implements TelemetryPublisher {
  private final TelemetryPublisher delegate;
  private final EmissionRateMeter meter;

  public MeteredTelemetryPublisher(TelemetryPublisher delegate, EmissionRateMeter meter) {
    this.delegate = delegate;
    this.meter = meter;
  }

  @Override
  public CompletableFuture<Void> publish(VehicleTelemetry telemetry) {
    meter.recordSent();
    CompletableFuture<Void> published = delegate.publish(telemetry);
    published.whenComplete((ok, error) -> {
      if (error == null) {
        meter.recordAcknowledged();
      } else {
        meter.recordFailed();
      }
    });
    return published;
  }
}
