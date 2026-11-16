package pt.ua.deti.tqs.p11.simulator.config;

import java.io.InputStream;
import java.util.List;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Reads a scenario file (YAML) and turns it into a validated config. It works on a stream, so it
 * knows nothing about files, the classpath or Spring. The caller opens and closes the stream.
 */
public class ScenarioLoader {

  private final ScenarioParser parser;

  public ScenarioLoader(ScenarioParser parser) {
    this.parser = parser;
  }

  /**
   * @param origin where the stream comes from, used only in error messages
   * @throws InvalidScenarioException if the content is not valid YAML or breaks a rule
   */
  public ScenarioConfig load(InputStream input, String origin) {
    Object content = read(input, origin);
    if (content == null) {
      throw invalid(origin, "the file is empty");
    }
    if (!(content instanceof java.util.Map<?, ?> map)) {
      throw invalid(origin, "the top level must be an object");
    }
    try {
      return parser.parse(map);
    } catch (InvalidScenarioException e) {
      throw new InvalidScenarioException(origin, e.getProblems());
    }
  }

  private static Object read(InputStream input, String origin) {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    // SafeConstructor builds only maps, lists and scalars: a file can never instantiate a class.
    Yaml yaml = new Yaml(new SafeConstructor(options));
    try {
      return yaml.load(input);
    } catch (YAMLException e) {
      throw invalid(origin, "not valid YAML: " + e.getMessage());
    }
  }

  private static InvalidScenarioException invalid(String origin, String problem) {
    return new InvalidScenarioException(origin, List.of(problem));
  }
}
