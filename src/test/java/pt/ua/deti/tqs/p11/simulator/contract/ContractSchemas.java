package pt.ua.deti.tqs.p11.simulator.contract;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Validates messages against the JSON Schemas in contract/schemas, the single source of truth of
 * the wire format. Schemas are registered under their $id, so their relative $refs resolve offline.
 */
public final class ContractSchemas {

  private static final Path SCHEMA_DIR = Path.of("contract", "schemas");
  private static final String ID_PREFIX = "https://fleet-telemetry-contract.example/v1/";

  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(
          SpecificationVersion.DRAFT_2020_12, builder -> builder.schemas(loadSchemasById()));

  private ContractSchemas() {}

  /** Returns the validation errors of a telemetry message; empty when it follows the contract. */
  public static List<Error> validateTelemetry(String json) {
    return validate("telemetry.schema.json", json);
  }

  /** Returns the validation errors of a response message; empty when it follows the contract. */
  public static List<Error> validateResponse(String json) {
    return validate("response.schema.json", json);
  }

  /** Returns the validation errors of an event message; empty when it follows the contract. */
  public static List<Error> validateEvent(String json) {
    return validate("event.schema.json", json);
  }

  private static List<Error> validate(String schemaFile, String json) {
    Schema schema = REGISTRY.getSchema(SchemaLocation.of(ID_PREFIX + schemaFile));
    return schema.validate(
        json,
        InputFormat.JSON,
        context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
  }

  private static Map<String, String> loadSchemasById() {
    Map<String, String> schemas = new HashMap<>();
    try (Stream<Path> files = Files.list(SCHEMA_DIR)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".schema.json")).toList()) {
        schemas.put(ID_PREFIX + file.getFileName(), Files.readString(file));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the contract schemas in " + SCHEMA_DIR, e);
    }
    return schemas;
  }
}
