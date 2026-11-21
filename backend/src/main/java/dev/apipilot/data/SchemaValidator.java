package dev.apipilot.data;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import dev.apipilot.spec.ApiModel;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Checks JSON values against schemas of one API, resolving references into its components. */
public final class SchemaValidator {

  private final SchemaRegistry registry;
  private final ObjectNode components = JsonNodeFactory.instance.objectNode();

  public SchemaValidator(ApiModel model) {
    registry =
        SchemaRegistry.withDefaultDialect(
            model.openApi31() ? Dialects.getOpenApi31() : Dialects.getOpenApi30());
    ObjectNode schemas = components.putObject("schemas");
    model.schemas().forEach(schemas::set);
  }

  /** Returns one message per violation, each starting with the JSON path of the offending value. */
  public List<String> validate(JsonNode schema, JsonNode value) {
    // "#/components/schemas/X" references resolve against the document root, so the schema is
    // wrapped in a document that carries the components.
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.set("components", components);
    document.putArray("allOf").add(schema);
    return registry.getSchema(document).validate(value).stream()
        .map(error -> error.getInstanceLocation() + ": " + error.getMessage())
        .toList();
  }
}
