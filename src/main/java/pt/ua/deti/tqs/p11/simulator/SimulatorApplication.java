package pt.ua.deti.tqs.p11.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Fleet telemetry simulator. Publishes vehicle telemetry over MQTT, following contract/. */
@SpringBootApplication
public class SimulatorApplication {

  public static void main(String[] args) {
    SpringApplication.run(SimulatorApplication.class, args);
  }
}
