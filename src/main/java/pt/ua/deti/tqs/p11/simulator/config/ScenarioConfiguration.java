package pt.ua.deti.tqs.p11.simulator.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Loads the scenario at start-up. If the file is missing or invalid the context does not start, and
 * the log says what is wrong and where.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ScenarioProperties.class)
public class ScenarioConfiguration {

  @Bean
  ScenarioLoader scenarioLoader() {
    return new ScenarioLoader(new ScenarioParser());
  }

  @Bean
  ScenarioConfig scenarioConfig(
      ScenarioProperties properties, ResourceLoader resourceLoader, ScenarioLoader loader)
      throws IOException {
    Resource resource = resourceLoader.getResource(properties.file());
    // Not readable covers a missing file, a directory and a file without permission.
    if (!resource.isReadable()) {
      throw new InvalidScenarioException(
          properties.file(), List.of("file not found or not readable"));
    }
    try (InputStream input = resource.getInputStream()) {
      return loader.load(input, properties.file());
    }
  }
}
