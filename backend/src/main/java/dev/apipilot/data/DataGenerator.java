package dev.apipilot.data;

import dev.apipilot.spec.SchemaResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.datafaker.Faker;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Produces request data that satisfies a schema. Free text gets a random token so that repeated
 * calls never collide on a unique constraint the spec cannot express.
 */
public final class DataGenerator {

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
  private static final int MAX_DEPTH = 4;
  private static final int TOKEN_LENGTH = 8;
  private static final String TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

  private record Hint(Pattern name, Function<DataGenerator, String> value) {}

  /** Checked in order against the lower-cased field name with separators removed. */
  private static final List<Hint> NAME_HINTS =
      List.of(
          hint("email", DataGenerator::email),
          hint("phone|mobile|^tel|fax", g -> g.faker.numerify("+1##########")),
          hint("password|passwd|secret", g -> "Aa1!" + g.token() + g.token().toUpperCase()),
          hint("firstname|givenname", g -> g.faker.name().firstName()),
          hint("lastname|surname|familyname", g -> g.faker.name().lastName()),
          hint("username|login|handle", g -> "user_" + g.token()),
          hint("url|uri|website|link|image|avatar|photo", g -> "https://example.com/" + g.token()),
          hint("slug", g -> g.faker.word().noun().toLowerCase(Locale.ROOT) + "-" + g.token()),
          hint("city", g -> g.faker.address().city()),
          hint("country", g -> g.faker.address().country()),
          hint("zip|postal", g -> g.faker.numerify("#####")),
          hint("street|address", g -> g.faker.address().streetAddress()),
          hint("company|organi[sz]ation", g -> g.faker.company().name() + " " + g.token()),
          hint("currency", g -> "USD"),
          hint(
              "description|summary|bio|body|content|text|comment|note|message",
              g -> g.faker.lorem().sentence() + " " + g.token()),
          hint(
              "name|title|label",
              g -> g.faker.word().adjective() + " " + g.faker.word().noun() + " " + g.token()));

  private final SchemaResolver schemas;
  private final Faker faker = new Faker(Locale.ENGLISH);

  public DataGenerator(Map<String, JsonNode> schemas) {
    this.schemas = new SchemaResolver(schemas);
  }

  public JsonNode generate(@Nullable JsonNode schema) {
    return generate(schema, "", 0);
  }

  private JsonNode generate(@Nullable JsonNode rawSchema, String fieldName, int depth) {
    JsonNode schema = schemas.resolve(rawSchema);
    if (schema.has("const")) {
      return schema.get("const");
    }
    if (schema.path("enum").has(0)) {
      JsonNode values = schema.get("enum");
      return values.get(faker.random().nextInt(values.size()));
    }
    return switch (typeOf(schema)) {
      case "object" -> object(schema, depth);
      case "array" -> array(schema, fieldName, depth);
      case "integer" -> NODES.numberNode(Math.round(number(schema, true)));
      case "number" -> NODES.numberNode(number(schema, false));
      case "boolean" -> NODES.booleanNode(faker.bool().bool());
      case "null" -> NODES.nullNode();
      default -> NODES.stringNode(string(schema, fieldName));
    };
  }

  private static String typeOf(JsonNode schema) {
    JsonNode type = schema.path("type");
    if (type.isArray()) {
      for (JsonNode option : type) {
        if (!option.asString().equals("null")) {
          return option.asString();
        }
      }
      return "null";
    }
    if (type.isString()) {
      return type.asString();
    }
    if (schema.has("properties")) {
      return "object";
    }
    return schema.has("items") ? "array" : "string";
  }

  private ObjectNode object(JsonNode schema, int depth) {
    ObjectNode object = NODES.objectNode();
    Set<String> required = schemas.required(schema);
    schemas
        .properties(schema)
        .forEach((name, property) -> {
          boolean readOnly = schemas.resolve(property).path("readOnly").asBoolean(false);
          // Optional parts stop at a fixed depth so self-referencing schemas end.
          boolean include = required.contains(name) || depth < MAX_DEPTH;
          if (!readOnly && include) {
            object.set(name, generate(property, name, depth + 1));
          }
        });
    return object;
  }

  private ArrayNode array(JsonNode schema, String fieldName, int depth) {
    ArrayNode array = NODES.arrayNode();
    int minItems = schema.path("minItems").asInt(0);
    int count = depth < MAX_DEPTH ? Math.max(minItems, 1) : minItems;
    for (int i = 0; i < count; i++) {
      array.add(generate(schema.get("items"), fieldName, depth + 1));
    }
    return array;
  }

  private double number(JsonNode schema, boolean integer) {
    double step = integer ? 1 : 0.01;
    double min = schema.path("minimum").asDouble(1);
    if (schema.path("exclusiveMinimum").isNumber()) {
      min = schema.get("exclusiveMinimum").asDouble() + step;
    } else if (schema.path("exclusiveMinimum").asBoolean(false)) {
      min += step;
    }
    double max = schema.path("maximum").asDouble(Math.max(min, 0) + 1000);
    if (schema.path("exclusiveMaximum").isNumber()) {
      max = schema.get("exclusiveMaximum").asDouble() - step;
    } else if (schema.path("exclusiveMaximum").asBoolean(false)) {
      max -= step;
    }
    if (!schema.has("minimum") && !schema.has("exclusiveMinimum") && max < min) {
      min = max - 1000;
    }
    double value = min + faker.random().nextDouble() * (max - min);
    double multipleOf = schema.path("multipleOf").asDouble(0);
    if (multipleOf > 0) {
      return Math.max(Math.ceil(min / multipleOf), Math.floor(value / multipleOf)) * multipleOf;
    }
    if (integer) {
      return Math.min(Math.max(Math.round(value), Math.ceil(min)), Math.floor(max));
    }
    return Math.min(Math.max(Math.round(value * 100) / 100.0, min), max);
  }

  private String string(JsonNode schema, String fieldName) {
    if (schema.path("pattern").isString()) {
      return faker.regexify(schema.get("pattern").asString().replaceAll("^\\^|\\$$", ""));
    }
    String byFormat = byFormat(schema.path("format").asString(""));
    if (byFormat != null) {
      return byFormat;
    }
    String normalized = fieldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    String value =
        NAME_HINTS.stream()
            .filter(hint -> hint.name().matcher(normalized).find())
            .findFirst()
            .map(hint -> hint.value().apply(this))
            .orElseGet(() -> faker.word().noun() + "-" + token());
    return fit(value, schema);
  }

  private @Nullable String byFormat(String format) {
    return switch (format) {
      case "email", "idn-email" -> email();
      case "uuid" -> UUID.randomUUID().toString();
      case "date" -> LocalDate.now().plusDays(faker.random().nextInt(1, 365)).toString();
      case "date-time" ->
          Instant.now()
              .plus(faker.random().nextInt(1, 365), ChronoUnit.DAYS)
              .truncatedTo(ChronoUnit.SECONDS)
              .toString();
      case "time" ->
          "%02d:%02d:00Z".formatted(faker.random().nextInt(24), faker.random().nextInt(60));
      case "uri", "url", "iri", "uri-reference" -> "https://example.com/" + token();
      case "hostname" -> token() + ".example.com";
      case "ipv4" -> faker.internet().ipV4Address();
      case "ipv6" -> faker.internet().ipV6Address();
      case "password" -> "Aa1!" + token() + token().toUpperCase(Locale.ROOT);
      case "byte" -> Base64.getEncoder().encodeToString(token().getBytes());
      case "binary" -> token();
      case "phone", "tel" -> faker.numerify("+1##########");
      default -> null;
    };
  }

  private String email() {
    return faker.name().firstName().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "")
        + "."
        + token()
        + "@example.com";
  }

  /** Pads or cuts to the schema's length limits, keeping the random token at the end. */
  private String fit(String value, JsonNode schema) {
    int min = schema.path("minLength").asInt(0);
    int max = schema.path("maxLength").asInt(Integer.MAX_VALUE);
    StringBuilder fitted = new StringBuilder(value);
    while (fitted.length() < min) {
      fitted.append(token());
    }
    if (fitted.length() <= max) {
      return fitted.toString();
    }
    String suffix = fitted.substring(fitted.length() - Math.min(TOKEN_LENGTH, max));
    return fitted.substring(0, max - suffix.length()) + suffix;
  }

  private String token() {
    StringBuilder token = new StringBuilder(TOKEN_LENGTH);
    for (int i = 0; i < TOKEN_LENGTH; i++) {
      token.append(TOKEN_CHARS.charAt(faker.random().nextInt(TOKEN_CHARS.length())));
    }
    return token.toString();
  }

  private static Hint hint(String namePattern, Function<DataGenerator, String> value) {
    return new Hint(Pattern.compile(namePattern), value);
  }
}
