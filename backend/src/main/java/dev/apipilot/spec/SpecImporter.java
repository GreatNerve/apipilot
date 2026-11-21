package dev.apipilot.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads OpenAPI 3.x or Swagger 2.0 content (JSON or YAML) into an {@link ApiModel}. */
@Component
public class SpecImporter {

  private static final String JSON = "application/json";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  public ApiModel importSpec(String content) {
    ParseOptions options = new ParseOptions();
    options.setResolve(true);
    SwaggerParseResult result;
    try {
      result = new OpenAPIParser().readContents(content, null, options);
    } catch (RuntimeException e) {
      throw new SpecImportException("The content is not a readable API description", e);
    }
    OpenAPI openApi = result.getOpenAPI();
    if (openApi == null) {
      throw new SpecImportException(
          "The content is not an OpenAPI or Swagger description: "
              + String.join("; ", Optional.ofNullable(result.getMessages()).orElse(List.of())));
    }
    return new Conversion(openApi).toModel();
  }

  /** Holds the document being converted so helpers can look up components. */
  private static final class Conversion {

    private final OpenAPI openApi;
    private final Components components;
    private final com.fasterxml.jackson.databind.ObjectMapper swaggerMapper;

    Conversion(OpenAPI openApi) {
      this.openApi = openApi;
      this.components = Optional.ofNullable(openApi.getComponents()).orElseGet(Components::new);
      this.swaggerMapper =
          openApi.getSpecVersion() == SpecVersion.V31 ? Json31.mapper() : Json.mapper();
    }

    ApiModel toModel() {
      List<Operation> operations = new ArrayList<>();
      if (openApi.getPaths() != null) {
        openApi
            .getPaths()
            .forEach((path, item) ->
                item.readOperationsMap()
                    .forEach((method, operation) ->
                        operations.add(operation(path, item, method.name(), operation))));
      }
      if (operations.isEmpty()) {
        throw new SpecImportException("The spec contains no operations");
      }
      return new ApiModel(
          openApi.getInfo() == null ? null : openApi.getInfo().getTitle(),
          openApi.getInfo() == null ? null : openApi.getInfo().getVersion(),
          servers(),
          List.copyOf(operations),
          securitySchemes(),
          schemas(),
          openApi.getSpecVersion() == SpecVersion.V31);
    }

    private List<String> servers() {
      if (openApi.getServers() == null) {
        return List.of();
      }
      // The parser adds "/" when a spec declares no server; that carries no information.
      return openApi.getServers().stream()
          .map(io.swagger.v3.oas.models.servers.Server::getUrl)
          .filter(url -> url != null && !url.equals("/"))
          .toList();
    }

    private Operation operation(
        String path, PathItem item, String method, io.swagger.v3.oas.models.Operation source) {
      String id = source.getOperationId() != null ? source.getOperationId() : method + " " + path;
      return new Operation(
          id,
          method,
          path,
          source.getSummary(),
          source.getTags() == null ? List.of() : List.copyOf(source.getTags()),
          parameters(item, source),
          requestBody(source),
          responses(source),
          security(source));
    }

    /** Path-level parameters apply to every operation unless one redefines the same name. */
    private List<Parameter> parameters(PathItem item, io.swagger.v3.oas.models.Operation source) {
      Map<String, Parameter> merged = new LinkedHashMap<>();
      for (List<io.swagger.v3.oas.models.parameters.Parameter> group :
          List.of(
              Optional.ofNullable(item.getParameters()).orElse(List.of()),
              Optional.ofNullable(source.getParameters()).orElse(List.of()))) {
        for (io.swagger.v3.oas.models.parameters.Parameter raw : group) {
          io.swagger.v3.oas.models.parameters.Parameter parameter =
              deref(raw.get$ref(), components.getParameters(), raw);
          merged.put(
              parameter.getIn() + ":" + parameter.getName(),
              new Parameter(
                  parameter.getName(),
                  parameter.getIn(),
                  Boolean.TRUE.equals(parameter.getRequired()),
                  json(parameter.getSchema())));
        }
      }
      return List.copyOf(merged.values());
    }

    private Body requestBody(io.swagger.v3.oas.models.Operation source) {
      if (source.getRequestBody() == null) {
        return null;
      }
      io.swagger.v3.oas.models.parameters.RequestBody body =
          deref(
              source.getRequestBody().get$ref(),
              components.getRequestBodies(),
              source.getRequestBody());
      return preferredContent(body.getContent())
          .map(entry ->
              new Body(
                  entry.getKey(),
                  Boolean.TRUE.equals(body.getRequired()),
                  json(entry.getValue().getSchema())))
          .orElse(null);
    }

    private List<Response> responses(io.swagger.v3.oas.models.Operation source) {
      if (source.getResponses() == null) {
        return List.of();
      }
      List<Response> responses = new ArrayList<>();
      source
          .getResponses()
          .forEach((status, raw) -> {
            ApiResponse response = deref(raw.get$ref(), components.getResponses(), raw);
            Optional<Map.Entry<String, MediaType>> content =
                preferredContent(response.getContent());
            responses.add(
                new Response(
                    status,
                    content.map(Map.Entry::getKey).orElse(null),
                    content.map(entry -> json(entry.getValue().getSchema())).orElse(null),
                    links(response)));
          });
      return List.copyOf(responses);
    }

    private List<Link> links(ApiResponse response) {
      if (response.getLinks() == null) {
        return List.of();
      }
      List<Link> links = new ArrayList<>();
      response
          .getLinks()
          .forEach((name, raw) -> {
            io.swagger.v3.oas.models.links.Link link =
                deref(raw.get$ref(), components.getLinks(), raw);
            links.add(
                new Link(
                    name,
                    link.getOperationId(),
                    link.getParameters() == null ? Map.of() : Map.copyOf(link.getParameters())));
          });
      return List.copyOf(links);
    }

    private List<String> security(io.swagger.v3.oas.models.Operation source) {
      List<SecurityRequirement> requirements =
          source.getSecurity() != null ? source.getSecurity() : openApi.getSecurity();
      if (requirements == null) {
        return List.of();
      }
      return requirements.stream().flatMap(r -> r.keySet().stream()).distinct().toList();
    }

    private Map<String, SecurityScheme> securitySchemes() {
      Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
      if (components.getSecuritySchemes() != null) {
        components
            .getSecuritySchemes()
            .forEach((name, scheme) ->
                schemes.put(
                    name,
                    new SecurityScheme(
                        scheme.getType() == null ? null : scheme.getType().toString(),
                        scheme.getScheme(),
                        scheme.getIn() == null ? null : scheme.getIn().toString(),
                        scheme.getName())));
      }
      return schemes;
    }

    private Map<String, JsonNode> schemas() {
      Map<String, JsonNode> schemas = new LinkedHashMap<>();
      if (components.getSchemas() != null) {
        components.getSchemas().forEach((name, schema) -> schemas.put(name, json(schema)));
      }
      return schemas;
    }

    private static Optional<Map.Entry<String, MediaType>> preferredContent(Content content) {
      if (content == null || content.isEmpty()) {
        return Optional.empty();
      }
      return content.entrySet().stream()
          .filter(e -> e.getKey().startsWith(JSON))
          .findFirst()
          .or(() ->
              content.entrySet().stream().filter(e -> e.getKey().endsWith("+json")).findFirst())
          .or(() -> content.entrySet().stream().findFirst());
    }

    /** Returns the component a {@code $ref} points at, or the value itself when it is inline. */
    private static <T> T deref(String ref, Map<String, T> registry, T inline) {
      if (ref == null) {
        return inline;
      }
      String name = ref.substring(ref.lastIndexOf('/') + 1);
      T target = registry == null ? null : registry.get(name);
      if (target == null) {
        throw new SpecImportException("Unresolved reference: " + ref);
      }
      return target;
    }

    private JsonNode json(Schema<?> schema) {
      if (schema == null) {
        return null;
      }
      try {
        return MAPPER.readTree(swaggerMapper.writeValueAsString(schema));
      } catch (JsonProcessingException e) {
        throw new SpecImportException("A schema in the spec could not be read", e);
      }
    }
  }
}
