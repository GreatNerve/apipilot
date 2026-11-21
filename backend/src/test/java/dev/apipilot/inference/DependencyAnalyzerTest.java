package dev.apipilot.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.apipilot.spec.ApiModel;
import dev.apipilot.spec.Operation;
import dev.apipilot.spec.SpecImporter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DependencyAnalyzerTest {

  private static final DependencyAnalyzer ANALYZER = new DependencyAnalyzer();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sample-api.json",
        "petstore.yaml",
        "swagger2.json",
        "edge-cases.yaml",
        "tricky-dependencies.yaml",
        "public/petstore3.json",
        "public/museum.yaml",
        "public/realworld.yml"
      })
  void everyOperationIsPlacedExactlyOnce(String fixture) {
    ApiModel model = model(fixture);
    DependencyGraph graph = ANALYZER.analyze(model);

    List<String> placed = new ArrayList<>(graph.standaloneOperations());
    for (Resource resource : graph.resources()) {
      placed.addAll(resource.operations().values());
      placed.addAll(resource.actions());
    }

    assertThat(placed)
        .containsExactlyInAnyOrderElementsOf(
            model.operations().stream().map(Operation::id).toList());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sample-api.json",
        "tricky-dependencies.yaml",
        "public/petstore3.json",
        "public/museum.yaml",
        "public/realworld.yml"
      })
  void everyResourceComesAfterTheResourcesItDependsOn(String fixture) {
    DependencyGraph graph = ANALYZER.analyze(model(fixture));
    List<String> order = graph.levels().stream().flatMap(List::stream).toList();

    assertThat(order)
        .containsExactlyInAnyOrderElementsOf(
            graph.resources().stream().map(Resource::key).toList());
    for (Dependency dependency : graph.dependencies()) {
      assertThat(order.indexOf(dependency.to()))
          .as("%s must come before %s", dependency.to(), dependency.from())
          .isLessThan(order.indexOf(dependency.from()));
    }
  }

  @Nested
  class SampleApi {

    private final DependencyGraph graph = ANALYZER.analyze(model("sample-api.json"));

    @Test
    void groupsOperationsIntoResourcesByPath() {
      assertThat(graph.resources())
          .extracting(Resource::key)
          .containsExactlyInAnyOrder(
              "/categories",
              "/workspaces",
              "/workspaces/{workspaceId}/members",
              "/workspaces/{workspaceId}/billing-profiles",
              "/documents");
    }

    @Test
    void assignsCrudRolesFromMethodAndPathShape() {
      assertThat(resource(graph, "/workspaces").operations().keySet())
          .containsExactlyInAnyOrder(Role.CREATE, Role.LIST, Role.READ, Role.UPDATE, Role.DELETE);
      assertThat(resource(graph, "/documents").operations().keySet())
          .containsExactlyInAnyOrder(Role.CREATE, Role.LIST, Role.READ, Role.PATCH, Role.DELETE);
      assertThat(resource(graph, "/categories").operations().keySet())
          .containsExactlyInAnyOrder(Role.LIST, Role.READ);
    }

    @Test
    void treatsAPostBelowAnItemAsAnActionOnThatResource() {
      assertThat(resource(graph, "/documents").actions()).containsExactly("publish");
    }

    @Test
    void nestedPathMakesTheChildDependOnItsParent() {
      assertThat(graph.dependencies())
          .filteredOn(d -> d.from().equals("/workspaces/{workspaceId}/members"))
          .extracting(Dependency::to, Dependency::input, Dependency::required)
          .containsExactly(tuple("/workspaces", "path:workspaceId", true));
    }

    @Test
    void bodyFieldNamedAfterAnotherResourceIdMakesADependency() {
      assertThat(graph.dependencies())
          .filteredOn(d -> d.from().equals("/documents"))
          .extracting(Dependency::to, Dependency::input)
          .containsExactlyInAnyOrder(
              tuple("/workspaces", "body:/workspaceId"), tuple("/categories", "body:/categoryId"));
    }

    @Test
    void resourcesWithoutDependenciesFormTheFirstLevel() {
      assertThat(graph.levels().getFirst()).containsExactlyInAnyOrder("/categories", "/workspaces");
      assertThat(graph.levels().get(1))
          .containsExactlyInAnyOrder(
              "/workspaces/{workspaceId}/members",
              "/workspaces/{workspaceId}/billing-profiles",
              "/documents");
    }

    @Test
    void createdResourceGetsItsIdFromTheCreateResponse() {
      IdSource source = resource(graph, "/workspaces").idSource();

      assertThat(resource(graph, "/workspaces").idParam()).isEqualTo("workspaceId");
      assertThat(source.kind()).isEqualTo(IdSource.Kind.CREATE_RESPONSE);
      assertThat(source.pointer()).isEqualTo("/id");
    }

    @Test
    void readOnlyResourceGetsItsIdFromTheListResponse() {
      IdSource source = resource(graph, "/categories").idSource();

      assertThat(source.kind()).isEqualTo(IdSource.Kind.LIST_RESPONSE);
      assertThat(source.operationId()).isEqualTo("list_2");
      assertThat(source.pointer()).isEqualTo("/0/id");
    }

    @Test
    void findsTheTokenAndRegistrationOperationsForBearerAuth() {
      assertThat(graph.auth()).hasSize(1);
      AuthPlan plan = graph.auth().getFirst();
      assertThat(plan.scheme()).isEqualTo("bearerAuth");
      assertThat(plan.tokenOperationId()).isEqualTo("token");
      assertThat(plan.tokenPointer()).isEqualTo("/accessToken");
      assertThat(plan.registerOperationId()).isEqualTo("register");
      assertThat(plan.valueTemplate()).isEqualTo("Bearer {{token}}");
    }

    @Test
    void nothingIsLeftUnresolved() {
      assertThat(graph.unresolved()).isEmpty();
      assertThat(graph.droppedForCycles()).isEmpty();
    }
  }

  @Nested
  class TrickyDependencies {

    private final DependencyGraph graph = ANALYZER.analyze(model("tricky-dependencies.yaml"));

    @Test
    void breaksACycleAtTheOptionalReference() {
      assertThat(graph.droppedForCycles())
          .extracting(Dependency::from, Dependency::to, Dependency::input)
          .containsExactly(tuple("/teams", "/users", "body:/leaderUserId"));
      List<String> order = graph.levels().stream().flatMap(List::stream).toList();
      assertThat(order.indexOf("/teams")).isLessThan(order.indexOf("/users"));
    }

    @Test
    void ignoresAResourceReferencingItself() {
      assertThat(graph.dependencies()).noneMatch(d -> d.from().equals(d.to()));
    }

    @Test
    void reportsARequiredReferenceThatNothingProduces() {
      assertThat(graph.unresolved())
          .extracting(UnresolvedInput::operationId, UnresolvedInput::input)
          .contains(tuple("createUser", "body:/ownerId"), tuple("getReport", "path:reportCode"));
    }

    @Test
    void usesAnOpenApiLinkToFindWhereAnIdComesFrom() {
      IdSource source = resource(graph, "/accounts").idSource();

      assertThat(source.kind()).isEqualTo(IdSource.Kind.OTHER_RESPONSE);
      assertThat(source.operationId()).isEqualTo("signup");
      assertThat(source.pointer()).isEqualTo("/key");
      assertThat(source.confidence()).isEqualTo(1.0);
    }
  }

  @Nested
  class Museum {

    private final DependencyGraph graph = ANALYZER.analyze(model("public/museum.yaml"));

    @Test
    void findsAnIdThatIsNotCalledId() {
      Resource events = resource(graph, "/special-events");

      assertThat(events.idParam()).isEqualTo("eventId");
      assertThat(events.idSource().pointer()).isEqualTo("/eventId");
    }

    @Test
    void ticketsDependOnEventsThroughTheSharedIdName() {
      assertThat(graph.dependencies())
          .extracting(Dependency::from, Dependency::to, Dependency::input)
          .contains(tuple("/tickets", "/special-events", "body:/eventId"));
    }

    @Test
    void operationWithNoResourceIsStandalone() {
      assertThat(graph.standaloneOperations()).containsExactly("getMuseumHours");
    }
  }

  @Nested
  class RealWorld {

    private final DependencyGraph graph = ANALYZER.analyze(model("public/realworld.yml"));

    @Test
    void findsAnIdInsideAResponseWrapperObject() {
      IdSource source = resource(graph, "/articles").idSource();

      assertThat(resource(graph, "/articles").idParam()).isEqualTo("slug");
      assertThat(source.kind()).isEqualTo(IdSource.Kind.CREATE_RESPONSE);
      assertThat(source.pointer()).isEqualTo("/article/slug");
    }

    @Test
    void commentsAreNestedUnderArticles() {
      assertThat(graph.dependencies())
          .extracting(Dependency::from, Dependency::to, Dependency::input)
          .contains(tuple("/articles/{slug}/comments", "/articles", "path:slug"));
    }

    @Test
    void postAndDeleteOnTheSameSubPathAreActionsInThatOrder() {
      assertThat(resource(graph, "/articles").actions())
          .containsSubsequence("CreateArticleFavorite", "DeleteArticleFavorite");
    }

    @Test
    void getAndPutWithoutAnIdFormASingletonResource() {
      assertThat(resource(graph, "/user").operations().keySet())
          .containsExactlyInAnyOrder(Role.READ, Role.UPDATE);
      assertThat(resource(graph, "/user").idParam()).isNull();
    }

    @Test
    void idWithNoCreateOperationIsTakenFromAnotherOperationThatReturnsIt() {
      IdSource source = resource(graph, "/profiles").idSource();

      assertThat(source.kind()).isEqualTo(IdSource.Kind.OTHER_RESPONSE);
      assertThat(source.pointer()).isEqualTo("/user/username");
    }

    @Test
    void findsANestedTokenForApiKeyAuth() {
      AuthPlan plan = graph.auth().getFirst();

      assertThat(plan.tokenOperationId()).isEqualTo("Login");
      assertThat(plan.tokenPointer()).isEqualTo("/user/token");
      assertThat(plan.registerOperationId()).isEqualTo("CreateUser");
      assertThat(plan.valueTemplate()).isEqualTo("{{token}}");
    }
  }

  @Nested
  class Petstore3 {

    private final DependencyGraph graph = ANALYZER.analyze(model("public/petstore3.json"));

    @Test
    void handlesSingularCollectionPathsAndUpdateOnTheCollection() {
      Resource pet = resource(graph, "/pet");

      assertThat(pet.operations())
          .containsEntry(Role.CREATE, "addPet")
          .containsEntry(Role.UPDATE, "updatePet")
          .containsEntry(Role.READ, "getPetById")
          .containsEntry(Role.DELETE, "deletePet");
      assertThat(pet.actions())
          .contains("findPetsByStatus", "findPetsByTags", "updatePetWithForm", "uploadFile");
    }

    @Test
    void prefersAnExactlyNamedFieldOverAGenericId() {
      Resource user = resource(graph, "/user");

      assertThat(user.idParam()).isEqualTo("username");
      assertThat(user.idSource().pointer()).isEqualTo("/username");
    }

    @Test
    void orderDependsOnPet() {
      assertThat(graph.dependencies())
          .extracting(Dependency::from, Dependency::to, Dependency::input)
          .contains(tuple("/store/order", "/pet", "body:/petId"));
    }

    @Test
    void authWithoutATokenOperationNeedsAUserSuppliedValue() {
      assertThat(graph.auth()).isNotEmpty().allMatch(plan -> plan.tokenOperationId() == null);
    }
  }

  @Nested
  class Overrides {

    private final ApiModel sample = model("sample-api.json");

    @Test
    void removedDependencyNoLongerAffectsTheOrder() {
      GraphOverrides overrides =
          new GraphOverrides(
              List.of(new EdgeRef("/documents", "/categories", "body:/categoryId")), List.of());

      DependencyGraph graph = ANALYZER.analyze(sample, overrides);

      assertThat(graph.dependencies())
          .noneMatch(d -> d.from().equals("/documents") && d.to().equals("/categories"));
      assertThat(graph.dependencies())
          .anyMatch(d -> d.from().equals("/documents") && d.to().equals("/workspaces"));
    }

    @Test
    void addedDependencyIsCertainAndMovesTheResourceToALaterLevel() {
      GraphOverrides overrides =
          new GraphOverrides(
              List.of(), List.of(new EdgeRef("/workspaces", "/categories", "body:/categoryId")));

      DependencyGraph graph = ANALYZER.analyze(sample, overrides);

      assertThat(graph.levels().getFirst()).containsExactly("/categories");
      assertThat(graph.levels().get(1)).containsExactly("/workspaces");
      assertThat(graph.dependencies())
          .filteredOn(d -> d.from().equals("/workspaces"))
          .extracting(Dependency::confidence, Dependency::reason)
          .containsExactly(tuple(1.0, "Added manually"));
    }

    @Test
    void addedDependencyBetweenUnknownResourcesIsIgnored() {
      GraphOverrides overrides =
          new GraphOverrides(List.of(), List.of(new EdgeRef("/nope", "/workspaces", "body:/x")));

      assertThat(ANALYZER.analyze(sample, overrides).dependencies())
          .noneMatch(d -> d.from().equals("/nope"));
    }
  }

  private static Resource resource(DependencyGraph graph, String key) {
    return graph.resources().stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow();
  }

  private static ApiModel model(String fixture) {
    try (InputStream in = DependencyAnalyzerTest.class.getResourceAsStream("/specs/" + fixture)) {
      return new SpecImporter().importSpec(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
