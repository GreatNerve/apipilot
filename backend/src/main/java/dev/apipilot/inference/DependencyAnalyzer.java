package dev.apipilot.inference;

import dev.apipilot.spec.ApiModel;
import dev.apipilot.spec.Link;
import dev.apipilot.spec.Operation;
import dev.apipilot.spec.Response;
import dev.apipilot.spec.SchemaResolver;
import dev.apipilot.spec.SecurityScheme;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Works out, from the shape of an API alone, which operations manage the same resource and which
 * resources need values from which others. Nothing here knows about any business domain.
 */
@Component
public class DependencyAnalyzer {

  public DependencyGraph analyze(ApiModel model) {
    return analyze(model, GraphOverrides.NONE);
  }

  /** As {@link #analyze(ApiModel)}, with a person's corrections applied before ordering. */
  public DependencyGraph analyze(ApiModel model, GraphOverrides overrides) {
    return new Analysis(model).run(overrides);
  }

  private static final class Draft {
    final String key;
    @Nullable String idParam;
    final Map<Role, String> operations = new EnumMap<>(Role.class);
    final List<String> actions = new ArrayList<>();
    @Nullable IdSource idSource;

    Draft(String key, @Nullable String idParam) {
      this.key = key;
      this.idParam = idParam;
    }

    String name() {
      return Arrays.stream(key.split("/"))
          .filter(segment -> !segment.isEmpty() && !isParam(segment))
          .reduce((first, second) -> second)
          .orElse(key);
    }

    Set<String> operationIds() {
      Set<String> ids = new LinkedHashSet<>(operations.values());
      ids.addAll(actions);
      return ids;
    }
  }

  private record Field(String name, String pointer, boolean required) {}

  private static final class Analysis {

    private static final Pattern TOKEN_FIELD =
        Pattern.compile("(?i)^(access_?token|token|jwt|id_?token|auth_?token|bearer_?token)$");
    private static final Pattern LOGIN_PATH =
        Pattern.compile("(?i)(login|token|sign-?in|auth|session)");
    private static final Pattern REGISTER_PATH = Pattern.compile("(?i)(register|sign-?up|sign_up)");
    private static final Pattern PASSWORD_FIELD = Pattern.compile("(?i)^(password|passwd|pwd)$");
    private static final Pattern ID_SUFFIX = Pattern.compile("(?i)(_?id|_?uuid)$");

    private final ApiModel model;
    private final SchemaResolver schemas;
    private final Map<String, Operation> byId = new LinkedHashMap<>();
    private final Map<String, List<Operation>> byPath = new LinkedHashMap<>();
    private final Map<String, String> collectionIdParams = new LinkedHashMap<>();
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    private final List<String> standalone = new ArrayList<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    private final List<UnresolvedInput> unresolved = new ArrayList<>();

    Analysis(ApiModel model) {
      this.model = model;
      this.schemas = new SchemaResolver(model.schemas());
      for (Operation operation : model.operations()) {
        byId.put(operation.id(), operation);
        byPath.computeIfAbsent(operation.path(), path -> new ArrayList<>()).add(operation);
      }
    }

    DependencyGraph run(GraphOverrides overrides) {
      findCollections();
      model.operations().forEach(this::classify);
      drafts.values().forEach(this::orderActions);
      drafts.values().forEach(this::addPathDependencies);
      drafts.values().forEach(this::findIdSource);
      drafts.values().forEach(this::addBodyDependencies);
      apply(overrides);

      List<Dependency> dropped = new ArrayList<>();
      List<List<String>> levels = order(dropped);
      return new DependencyGraph(
          drafts.values().stream()
              .map(d ->
                  new Resource(
                      d.key,
                      d.name(),
                      d.idParam,
                      new EnumMap<>(d.operations),
                      List.copyOf(d.actions),
                      d.idSource))
              .toList(),
          List.copyOf(dependencies),
          List.copyOf(dropped),
          levels,
          List.copyOf(standalone),
          authPlans(),
          List.copyOf(unresolved));
    }

    private void apply(GraphOverrides overrides) {
      dependencies.removeIf(
          d -> overrides.removed().contains(new EdgeRef(d.from(), d.to(), d.input())));
      for (EdgeRef edge : overrides.added()) {
        if (drafts.containsKey(edge.from())
            && drafts.containsKey(edge.to())
            && !edge.from().equals(edge.to())) {
          dependencies.add(
              new Dependency(edge.from(), edge.to(), edge.input(), true, 1.0, "Added manually"));
        }
      }
    }

    // ---- Grouping ----------------------------------------------------------------------------

    /** Every "fixed segment followed by a parameter" marks the fixed part as a collection. */
    private void findCollections() {
      for (String path : byPath.keySet()) {
        List<String> segments = segments(path);
        for (int i = 1; i < segments.size(); i++) {
          if (isParam(segments.get(i)) && !isParam(segments.get(i - 1))) {
            collectionIdParams.putIfAbsent(join(segments, i), paramName(segments.get(i)));
          }
        }
      }
    }

    private void classify(Operation operation) {
      List<String> segments = segments(operation.path());
      int n = segments.size();
      if (n == 0) {
        standalone.add(operation.id());
        return;
      }
      String method = operation.method();
      if (isParam(segments.get(n - 1))) {
        Draft draft = draft(join(segments, n - 1));
        if (draft.idParam == null) {
          draft.idParam = paramName(segments.get(n - 1));
        }
        assign(draft, operation, itemRole(method));
      } else if (collectionIdParams.containsKey(operation.path())) {
        assign(draft(operation.path()), operation, collectionRole(method));
      } else if (n >= 2 && isParam(segments.get(n - 2))) {
        classifyBelowItem(operation, segments);
      } else if (n >= 2 && collectionIdParams.containsKey(join(segments, n - 1))) {
        draft(join(segments, n - 1)).actions.add(operation.id());
      } else {
        classifyStandalonePath(operation);
      }
    }

    /** A fixed segment under an item is either a sub-resource (it can be read) or an action. */
    private void classifyBelowItem(Operation operation, List<String> segments) {
      Optional<Operation> get = at(operation.path(), "GET");
      if (get.isEmpty()) {
        draft(join(segments, segments.size() - 2)).actions.add(operation.id());
        return;
      }
      boolean list = isArrayResponse(get.get());
      Role role =
          switch (operation.method()) {
            case "GET" -> list ? Role.LIST : Role.READ;
            case "POST" -> Role.CREATE;
            default -> itemRole(operation.method());
          };
      assign(draft(operation.path()), operation, role);
    }

    private void classifyStandalonePath(Operation operation) {
      Optional<Operation> get = at(operation.path(), "GET");
      boolean hasUpdate =
          at(operation.path(), "PUT").isPresent() || at(operation.path(), "PATCH").isPresent();
      boolean hasCreate = at(operation.path(), "POST").isPresent();
      if (get.isPresent() && hasUpdate && !isArrayResponse(get.get())) {
        Role role = operation.method().equals("POST") ? Role.CREATE : itemRole(operation.method());
        assign(draft(operation.path()), operation, role);
      } else if (get.isPresent() && hasCreate) {
        assign(draft(operation.path()), operation, collectionRole(operation.method()));
      } else {
        standalone.add(operation.id());
      }
    }

    private static @Nullable Role itemRole(String method) {
      return switch (method) {
        case "GET" -> Role.READ;
        case "PUT" -> Role.UPDATE;
        case "PATCH" -> Role.PATCH;
        case "DELETE" -> Role.DELETE;
        default -> null;
      };
    }

    private static @Nullable Role collectionRole(String method) {
      return switch (method) {
        case "POST" -> Role.CREATE;
        case "GET" -> Role.LIST;
        case "PUT" -> Role.UPDATE;
        case "PATCH" -> Role.PATCH;
        default -> null;
      };
    }

    private static void assign(Draft draft, Operation operation, @Nullable Role role) {
      if (role == null || draft.operations.putIfAbsent(role, operation.id()) != null) {
        draft.actions.add(operation.id());
      }
    }

    /** Undoing actions (DELETE) run after the actions they undo. */
    private void orderActions(Draft draft) {
      draft.actions.sort(
          Comparator.comparing(id -> byId.get(id).method().equals("DELETE") ? 1 : 0));
    }

    private Draft draft(String key) {
      return drafts.computeIfAbsent(key, k -> new Draft(k, collectionIdParams.get(k)));
    }

    // ---- Dependencies ------------------------------------------------------------------------

    private void addPathDependencies(Draft draft) {
      List<String> segments = segments(draft.key);
      for (int i = 1; i < segments.size(); i++) {
        if (!isParam(segments.get(i))) {
          continue;
        }
        String param = paramName(segments.get(i));
        String parentKey = join(segments, i);
        if (drafts.containsKey(parentKey) && !parentKey.equals(draft.key)) {
          dependencies.add(
              new Dependency(
                  draft.key,
                  parentKey,
                  "path:" + param,
                  true,
                  1.0,
                  "The path of %s contains {%s}, which identifies one item of %s"
                      .formatted(draft.name(), param, drafts.get(parentKey).name())));
        } else {
          unresolved.add(
              new UnresolvedInput(
                  draft.operationIds().iterator().next(),
                  "path:" + param,
                  "No resource in the spec owns the path parameter {%s}".formatted(param)));
        }
      }
    }

    private void addBodyDependencies(Draft draft) {
      String createId = draft.operations.get(Role.CREATE);
      if (createId == null || byId.get(createId).requestBody() == null) {
        return;
      }
      for (Field field : fields(byId.get(createId).requestBody().schema())) {
        if (field.name().equals(draft.idParam) || isGenericId(field.name())) {
          continue;
        }
        Optional<Dependency> match = matchReference(draft, field);
        if (match.isPresent()) {
          if (!match.get().to().equals(draft.key)) {
            dependencies.add(match.get());
          }
        } else if (field.required() && ID_SUFFIX.matcher(field.name()).find()) {
          unresolved.add(
              new UnresolvedInput(
                  createId,
                  "body:" + field.pointer(),
                  "Required field %s looks like a reference, but no resource in the spec matches it"
                      .formatted(field.name())));
        }
      }
    }

    private Optional<Dependency> matchReference(Draft from, Field field) {
      for (Draft target : drafts.values()) {
        if (field.name().equals(target.idParam)) {
          return Optional.of(
              bodyDependency(
                  from,
                  target,
                  field,
                  0.9,
                  "Field %s has the same name as the ID parameter of %s"));
        }
      }
      if (!ID_SUFFIX.matcher(field.name()).find()) {
        return Optional.empty();
      }
      String stem = normalize(ID_SUFFIX.matcher(field.name()).replaceFirst(""));
      for (Draft target : drafts.values()) {
        if (stem.equals(singular(target.name()))
            || stem.equals(singular(lastWord(target.name())))) {
          return Optional.of(
              bodyDependency(from, target, field, 0.75, "Field %s is named after the resource %s"));
        }
      }
      for (Draft target : drafts.values()) {
        String name = singular(lastWord(target.name()));
        if (name.length() >= 3 && stem.endsWith(name)) {
          return Optional.of(
              bodyDependency(from, target, field, 0.5, "Field %s ends with the resource name %s"));
        }
      }
      return Optional.empty();
    }

    private static Dependency bodyDependency(
        Draft from, Draft to, Field field, double confidence, String reason) {
      return new Dependency(
          from.key,
          to.key,
          "body:" + field.pointer(),
          field.required(),
          confidence,
          reason.formatted(field.name(), to.name()));
    }

    // ---- ID sources --------------------------------------------------------------------------

    private void findIdSource(Draft draft) {
      String idParam = draft.idParam;
      if (idParam == null) {
        return;
      }
      draft.idSource =
          fromLink(draft, idParam)
              .or(() -> fromCreate(draft, idParam))
              .or(() -> fromList(draft, idParam))
              .or(() -> fromAnotherOperation(draft, idParam))
              .orElse(null);
      if (draft.idSource == null) {
        unresolved.add(
            new UnresolvedInput(
                draft.operationIds().iterator().next(),
                "path:" + idParam,
                "No operation in the spec returns a value for {%s}".formatted(idParam)));
      }
    }

    private Optional<IdSource> fromLink(Draft draft, String idParam) {
      for (Operation operation : model.operations()) {
        for (Response response : operation.responses()) {
          for (Link link : response.links()) {
            String expression = link.parameters().get(idParam);
            if (link.operationId() != null
                && draft.operationIds().contains(link.operationId())
                && expression != null
                && expression.startsWith("$response.body#")) {
              boolean ownCreate = operation.id().equals(draft.operations.get(Role.CREATE));
              return Optional.of(
                  new IdSource(
                      ownCreate ? IdSource.Kind.CREATE_RESPONSE : IdSource.Kind.OTHER_RESPONSE,
                      operation.id(),
                      expression.substring("$response.body#".length()),
                      1.0));
            }
          }
        }
      }
      return Optional.empty();
    }

    private Optional<IdSource> fromCreate(Draft draft, String idParam) {
      String createId = draft.operations.get(Role.CREATE);
      if (createId == null) {
        return Optional.empty();
      }
      Operation create = byId.get(createId);
      List<String> names = idFieldNames(draft, idParam);
      for (int i = 0; i < names.size(); i++) {
        String name = names.get(i);
        Optional<Field> inResponse = field(successSchema(create), name);
        if (inResponse.isPresent()) {
          return Optional.of(
              new IdSource(
                  IdSource.Kind.CREATE_RESPONSE,
                  createId,
                  inResponse.get().pointer(),
                  i == 0 ? 0.95 : 0.85));
        }
      }
      if (create.requestBody() != null) {
        Optional<Field> inRequest = field(create.requestBody().schema(), idParam);
        if (inRequest.isPresent()) {
          return Optional.of(
              new IdSource(IdSource.Kind.CREATE_REQUEST, createId, inRequest.get().pointer(), 0.8));
        }
      }
      return Optional.empty();
    }

    private Optional<IdSource> fromList(Draft draft, String idParam) {
      String listId = draft.operations.get(Role.LIST);
      if (listId == null) {
        return Optional.empty();
      }
      JsonNode schema = schemas.resolve(successSchema(byId.get(listId)));
      String prefix = "";
      if (!schemas.isArray(schema)) {
        Optional<Map.Entry<String, JsonNode>> arrayProperty =
            schemas.properties(schema).entrySet().stream()
                .filter(e -> schemas.isArray(schemas.resolve(e.getValue())))
                .findFirst();
        if (arrayProperty.isEmpty()) {
          return Optional.empty();
        }
        prefix = "/" + escape(arrayProperty.get().getKey());
        schema = schemas.resolve(arrayProperty.get().getValue());
      }
      for (String name : idFieldNames(draft, idParam)) {
        Optional<Field> found = field(schema.get("items"), name);
        if (found.isPresent()) {
          return Optional.of(
              new IdSource(
                  IdSource.Kind.LIST_RESPONSE, listId, prefix + "/0" + found.get().pointer(), 0.8));
        }
      }
      return Optional.empty();
    }

    /** Last resort: some other operation returns a field with exactly the parameter's name. */
    private Optional<IdSource> fromAnotherOperation(Draft draft, String idParam) {
      if (isGenericId(idParam)) {
        return Optional.empty();
      }
      return model.operations().stream()
          .filter(operation -> !draft.operationIds().contains(operation.id()))
          .sorted(Comparator.comparing(operation -> operation.method().equals("POST") ? 0 : 1))
          .flatMap(operation ->
              field(successSchema(operation), idParam)
                  .map(found ->
                      new IdSource(
                          IdSource.Kind.OTHER_RESPONSE, operation.id(), found.pointer(), 0.6))
                  .stream())
          .findFirst();
    }

    /** The exact parameter name first; generic ID names only when the parameter is ID-like. */
    private static List<String> idFieldNames(Draft draft, String idParam) {
      Set<String> names = new LinkedHashSet<>();
      names.add(idParam);
      if (ID_SUFFIX.matcher(idParam).find()) {
        names.addAll(List.of("id", singular(lastWord(draft.name())) + "Id", "uuid", "_id"));
      }
      return List.copyOf(names);
    }

    // ---- Ordering ----------------------------------------------------------------------------

    private List<List<String>> order(List<Dependency> dropped) {
      List<List<String>> levels = new ArrayList<>();
      Set<String> remaining = new LinkedHashSet<>(drafts.keySet());
      while (!remaining.isEmpty()) {
        List<String> level =
            remaining.stream()
                .filter(key ->
                    dependencies.stream()
                        .noneMatch(d -> d.from().equals(key) && remaining.contains(d.to())))
                .toList();
        if (level.isEmpty()) {
          Dependency weakest =
              dependencies.stream()
                  .filter(d -> remaining.contains(d.from()) && remaining.contains(d.to()))
                  .filter(d -> reaches(d.to(), d.from(), remaining))
                  .min(
                      Comparator.comparing((Dependency d) -> d.required() ? 1 : 0)
                          .thenComparing(Dependency::confidence))
                  .orElseThrow();
          dependencies.remove(weakest);
          dropped.add(weakest);
          continue;
        }
        levels.add(level);
        level.forEach(remaining::remove);
      }
      return levels;
    }

    /** True when {@code target} can be reached from {@code start} by following dependencies. */
    private boolean reaches(String start, String target, Set<String> within) {
      Deque<String> queue = new ArrayDeque<>(List.of(start));
      Set<String> seen = new HashSet<>();
      while (!queue.isEmpty()) {
        String current = queue.poll();
        if (current.equals(target)) {
          return true;
        }
        if (seen.add(current)) {
          dependencies.stream()
              .filter(d -> d.from().equals(current) && within.contains(d.to()))
              .forEach(d -> queue.add(d.to()));
        }
      }
      return false;
    }

    // ---- Authentication ----------------------------------------------------------------------

    private List<AuthPlan> authPlans() {
      List<String> schemes =
          model.operations().stream().flatMap(o -> o.security().stream()).distinct().toList();
      if (schemes.isEmpty()) {
        return List.of();
      }
      Optional<Operation> tokenOperation =
          tokenOperation(operation -> operation.security().isEmpty())
              .or(() -> tokenOperation(operation -> true));
      Optional<Operation> registerOperation = tokenOperation.flatMap(this::registerOperation);
      return schemes.stream()
          .map(scheme ->
              new AuthPlan(
                  scheme,
                  tokenOperation.map(Operation::id).orElse(null),
                  tokenOperation
                      .flatMap(operation -> tokenField(operation))
                      .map(Field::pointer)
                      .orElse(null),
                  registerOperation.map(Operation::id).orElse(null),
                  valueTemplate(model.securitySchemes().get(scheme))))
          .toList();
    }

    private Optional<Operation> tokenOperation(Predicate<Operation> candidates) {
      return model.operations().stream()
          .filter(candidates)
          .filter(operation -> tokenField(operation).isPresent())
          .min(
              Comparator.comparing(
                  operation -> LOGIN_PATH.matcher(operation.path()).find() ? 0 : 1));
    }

    private Optional<Field> tokenField(Operation operation) {
      return fields(successSchema(operation)).stream()
          .filter(field -> TOKEN_FIELD.matcher(field.name()).matches())
          .findFirst();
    }

    private Optional<Operation> registerOperation(Operation tokenOperation) {
      List<Operation> candidates =
          model.operations().stream()
              .filter(o -> o.method().equals("POST") && !o.id().equals(tokenOperation.id()))
              .toList();
      return candidates.stream()
          .filter(o -> REGISTER_PATH.matcher(o.path()).find())
          .findFirst()
          .or(() ->
              candidates.stream()
                  .filter(o ->
                      o.requestBody() != null
                          && fields(o.requestBody().schema()).stream()
                              .anyMatch(f -> PASSWORD_FIELD.matcher(f.name()).matches()))
                  .findFirst());
    }

    private static @Nullable String valueTemplate(@Nullable SecurityScheme scheme) {
      if (scheme == null || scheme.type() == null) {
        return "Bearer {{token}}";
      }
      return switch (scheme.type()) {
        case "apiKey" -> "{{token}}";
        case "http" -> "basic".equalsIgnoreCase(scheme.scheme()) ? null : "Bearer {{token}}";
        default -> "Bearer {{token}}";
      };
    }

    // ---- Schema helpers ----------------------------------------------------------------------

    /** Top-level properties, then properties one object deep (for wrappers like {"data": {}}). */
    private List<Field> fields(@Nullable JsonNode schema) {
      JsonNode resolved = schemas.resolve(schema);
      Set<String> required = schemas.required(resolved);
      List<Field> top = new ArrayList<>();
      List<Field> nested = new ArrayList<>();
      schemas
          .properties(resolved)
          .forEach((name, property) -> {
            top.add(new Field(name, "/" + escape(name), required.contains(name)));
            JsonNode inner = schemas.resolve(property);
            Set<String> innerRequired = schemas.required(inner);
            schemas
                .properties(inner)
                .keySet()
                .forEach(innerName ->
                    nested.add(
                        new Field(
                            innerName,
                            "/" + escape(name) + "/" + escape(innerName),
                            required.contains(name) && innerRequired.contains(innerName))));
          });
      top.addAll(nested);
      return top;
    }

    private Optional<Field> field(@Nullable JsonNode schema, String name) {
      return fields(schema).stream().filter(field -> field.name().equals(name)).findFirst();
    }

    private @Nullable JsonNode successSchema(Operation operation) {
      return operation.responses().stream()
          .filter(response -> response.status().startsWith("2") && response.schema() != null)
          .map(Response::schema)
          .findFirst()
          .orElse(null);
    }

    private boolean isArrayResponse(Operation operation) {
      return schemas.isArray(schemas.resolve(successSchema(operation)));
    }

    private Optional<Operation> at(String path, String method) {
      return byPath.get(path).stream().filter(o -> o.method().equals(method)).findFirst();
    }
  }

  // ---- Path and name helpers -----------------------------------------------------------------

  private static List<String> segments(String path) {
    return Arrays.stream(path.split("/")).filter(segment -> !segment.isEmpty()).toList();
  }

  /** The path made of the first {@code count} segments. */
  private static String join(List<String> segments, int count) {
    return "/" + String.join("/", segments.subList(0, count));
  }

  private static boolean isParam(String segment) {
    return segment.startsWith("{") && segment.endsWith("}");
  }

  private static String paramName(String segment) {
    return segment.substring(1, segment.length() - 1);
  }

  private static boolean isGenericId(String name) {
    return name.equalsIgnoreCase("id") || name.equalsIgnoreCase("uuid") || name.equals("_id");
  }

  private static String lastWord(String name) {
    String[] words = name.split("[-_]");
    return words[words.length - 1];
  }

  private static String normalize(String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  private static String singular(String name) {
    String normalized = normalize(name);
    if (normalized.endsWith("ies")) {
      return normalized.substring(0, normalized.length() - 3) + "y";
    }
    if (normalized.endsWith("s") && !normalized.endsWith("ss")) {
      return normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static String escape(String pointerToken) {
    return pointerToken.replace("~", "~0").replace("/", "~1");
  }
}
