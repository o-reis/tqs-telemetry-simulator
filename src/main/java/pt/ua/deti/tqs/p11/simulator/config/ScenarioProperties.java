package pt.ua.deti.tqs.p11.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the scenario file is, set with the environment variable SIMULATOR_SCENARIO_FILE.
 *
 * @param file a Spring resource location: {@code classpath:...} for a file inside the image or
 *     {@code file:...} for one mounted from outside
 */
@ConfigurationProperties("simulator.scenario")
public record ScenarioProperties(@DefaultValue("classpath:scenarios/ria-ride.yml") String file) {}
