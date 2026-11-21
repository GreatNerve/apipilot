package dev.apipilot.spec;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns a schema that may be a reference or a composition into one plain object schema, one level
 * at a time, so self-referencing schemas are safe to walk.
 */
public final class SchemaResolver {

  private static final String REF_PREFIX = "#/components/schemas/";
  private static final int MAX_REF_CHAIN = 20;
  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private final Map<String, JsonNode> schemas;

  public SchemaResolver(Map<String, JsonNode> schemas) {
    this.schemas = schemas;
  }

  /**
   * Follows {@code $ref}, merges {@code allOf} members, and takes the first variant of a {@code
   * oneOf} or {@code anyOf}. Properties of the result are left as they are in the spec.
   */
  public JsonNode resolve(@Nullable JsonNode schema) {
    return resolve(schema, 0);
  }

  public Map<String, JsonNode> properties(JsonNode resolved) {
    Map<String, JsonNode> properties = new LinkedHashMap<>();
    resolved.path("properties").properties().forEach(e -> properties.put(e.getKey(), e.getValue()));
    return properties;
  }

  public Set<String> required(JsonNode resolved) {
    Set<String> required = new LinkedHashSet<>();
    resolved.path("required").forEach(name -> required.add(name.asString()));
    return required;
  }

  public boolean isArray(JsonNode resolved) {
    return "array".equals(resolved.path("type").asString(""));
  }

  private JsonNode resolve(@Nullable JsonNode schema, int depth) {
    if (schema == null || !schema.isObject() || depth > MAX_REF_CHAIN) {
      return NODES.objectNode();
    }
    if (schema.has("$ref")) {
      String ref = schema.get("$ref").asString();
      return resolve(
          ref.startsWith(REF_PREFIX) ? schemas.get(ref.substring(REF_PREFIX.length())) : null,
          depth + 1);
    }
    if (schema.has("allOf")) {
      return merge(schema, "allOf", schema.get("allOf"), depth);
    }
    for (String keyword : List.of("oneOf", "anyOf")) {
      if (schema.path(keyword).has(0)) {
        return merge(schema, keyword, NODES.arrayNode().add(schema.get(keyword).get(0)), depth);
      }
    }
    return schema;
  }

  private JsonNode merge(JsonNode schema, String keyword, JsonNode members, int depth) {
    ObjectNode merged = NODES.objectNode();
    ObjectNode properties = NODES.objectNode();
    Set<String> required = new LinkedHashSet<>();
    collect(schema, merged, properties, required, keyword);
    for (JsonNode member : members) {
      collect(resolve(member, depth + 1), merged, properties, required, keyword);
    }
    if (!properties.isEmpty()) {
      merged.set("properties", properties);
    }
    if (!required.isEmpty()) {
      ArrayNode names = merged.putArray("required");
      required.forEach(names::add);
    }
    return merged;
  }

  private void collect(
      JsonNode source,
      ObjectNode merged,
      ObjectNode properties,
      Set<String> required,
      String skip) {
    source
        .properties()
        .forEach(entry -> {
          switch (entry.getKey()) {
            case "properties" ->
                entry
                    .getValue()
                    .properties()
                    .forEach(p -> properties.set(p.getKey(), p.getValue()));
            case "required" -> entry.getValue().forEach(name -> required.add(name.asString()));
            default -> {
              if (!entry.getKey().equals(skip)) {
                merged.set(entry.getKey(), entry.getValue());
              }
            }
          }
        });
  }
}
