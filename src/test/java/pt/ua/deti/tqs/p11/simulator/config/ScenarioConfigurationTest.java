package pt.ua.deti.tqs.p11.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ScenarioConfigurationTest {

  private static final String MINIMAL =
      """
      fleetId: fleet-07
      simulation: { emissionIntervalSeconds: 5, seed: 1, faultProbability: 0 }
      hubs:
        - { id: hub-01, name: Estação de Aveiro, position: { lat: 40.6443, lon: -8.6455 } }
      vehicles:
        - { id: bike-0001, type: E_BIKE, hub: hub-01, battery: 90 }
      """;

  private static final String NOT_READABLE = "file not found or not readable";

  @TempDir Path dir;

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(ScenarioConfiguration.class);

  private ApplicationContextRunner runnerWithFile(String location) {
    return runner.withPropertyValues("simulator.scenario.file=" + location);
  }

  private String write(String name, String yaml) throws IOException {
    return Files.writeString(dir.resolve(name), yaml).toUri().toString();
  }

  private static void assertStartupFailsWith(AssertableApplicationContext context, String message) {
    assertThat(context).hasFailed();
    assertThat(context.getStartupFailure())
        .rootCause()
        .isInstanceOf(InvalidScenarioException.class)
        .hasMessage(message);
  }

  @Test
  void start_withoutConfiguration_loadsRiaRideFromTheClasspath() {
    runner.run(context -> {
      assertThat(context).hasNotFailed().hasSingleBean(ScenarioConfig.class);
      assertThat(context.getBean(ScenarioConfig.class).fleetId()).isEqualTo("fleet-01");
    });
  }

  @Test
  void start_withAnExternalFile_loadsThatFile() throws IOException {
    runnerWithFile(write("scenario.yml", MINIMAL))
        .run(context -> {
          ScenarioConfig config = context.getBean(ScenarioConfig.class);

          assertThat(config.fleetId()).isEqualTo("fleet-07");
          assertThat(config.hubs()).hasSize(1);
          assertThat(config.vehicles()).hasSize(1);
        });
  }

  @Test
  void start_withAnInvalidFile_failsAndReportsTheProblemWithItsOrigin() throws IOException {
    String location = write("broken.yml", MINIMAL.replace("battery: 90", "battery: 150"));

    runnerWithFile(location)
        .run(context ->
            assertStartupFailsWith(
                context,
                "Invalid scenario in "
                    + location
                    + " (1 problem):\n"
                    + " - vehicles[0].battery: must be between 0 and 100 (was 150.0)"));
  }

  @Test
  void start_withAMissingFile_failsAndNamesIt() {
    String location = dir.resolve("missing.yml").toUri().toString();

    runnerWithFile(location)
        .run(context ->
            assertStartupFailsWith(
                context, "Invalid scenario in " + location + " (1 problem):\n - " + NOT_READABLE));
  }

  @Test
  void start_withAMissingClasspathFile_failsAndNamesIt() {
    runnerWithFile("classpath:scenarios/missing.yml")
        .run(context ->
            assertStartupFailsWith(
                context,
                "Invalid scenario in classpath:scenarios/missing.yml (1 problem):\n - "
                    + NOT_READABLE));
  }

  @Test
  void start_withADirectoryInsteadOfAFile_fails() {
    String location = dir.toUri().toString();

    runnerWithFile(location)
        .run(context ->
            assertStartupFailsWith(
                context, "Invalid scenario in " + location + " (1 problem):\n - " + NOT_READABLE));
  }
}
