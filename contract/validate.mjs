// Checks the contract examples against the JSON Schemas.
//   - every file in examples/valid must validate
//   - every file in examples/invalid must be rejected
//   - every schema (except the envelope) must have at least one valid example
// The schema used for an example is chosen by the part of the file name before the first "-",
// e.g. "telemetry-in-hub.json" is checked against "telemetry.schema.json".

import { readdirSync, readFileSync } from "node:fs";
import { basename, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const root = dirname(fileURLToPath(import.meta.url));
const schemaDir = join(root, "schemas");

const readJson = (path) => JSON.parse(readFileSync(path, "utf8"));
const jsonFiles = (dir) => readdirSync(dir).filter((f) => f.endsWith(".json")).sort();

// strict: true makes unknown keywords and typos in a schema an error, not a silent no-op.
const ajv = new Ajv2020({ strict: true, allErrors: true });
addFormats(ajv);

const schemaIdByName = new Map();
for (const file of jsonFiles(schemaDir)) {
  const schema = readJson(join(schemaDir, file));
  ajv.addSchema(schema);
  schemaIdByName.set(basename(file, ".schema.json"), schema.$id);
}

let failures = 0;
const fail = (message) => {
  failures += 1;
  console.error(`FAIL ${message}`);
};

const validExamplesPerSchema = new Map();

for (const [folder, shouldBeValid] of [["valid", true], ["invalid", false]]) {
  const dir = join(root, "examples", folder);
  for (const file of jsonFiles(dir)) {
    const schemaName = file.split("-")[0];
    const schemaId = schemaIdByName.get(schemaName);
    if (!schemaId) {
      fail(`examples/${folder}/${file}: no schema named "${schemaName}"`);
      continue;
    }
    const validate = ajv.getSchema(schemaId);
    const isValid = validate(readJson(join(dir, file)));
    if (isValid === shouldBeValid) {
      console.log(`ok   examples/${folder}/${file}`);
      if (shouldBeValid) {
        validExamplesPerSchema.set(schemaName, (validExamplesPerSchema.get(schemaName) ?? 0) + 1);
      }
    } else if (shouldBeValid) {
      fail(`examples/${folder}/${file} should be valid: ${ajv.errorsText(validate.errors)}`);
    } else {
      fail(`examples/${folder}/${file} should be rejected but validated`);
    }
  }
}

for (const name of schemaIdByName.keys()) {
  if (name !== "envelope" && !validExamplesPerSchema.has(name)) {
    fail(`schema "${name}" has no valid example`);
  }
}

if (failures > 0) {
  console.error(`\n${failures} problem(s) found`);
  process.exit(1);
}
console.log("\nAll examples behave as expected");
