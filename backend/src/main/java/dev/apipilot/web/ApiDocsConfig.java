package dev.apipilot.web;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;

/** Shapes the API description that the frontend's TypeScript types are generated from. */
@Configuration
class ApiDocsConfig {

  static {
    // A JsonNode field carries arbitrary JSON (a JSON Schema), not the JsonNode bean's getters.
    SpringDocUtils.getConfig()
        .replaceWithSchema(JsonNode.class, new ObjectSchema().additionalProperties(true));
  }

  /** Our records are non-null by default, so every component not marked nullable is required. */
  @Bean
  ModelConverter requiredUnlessNullable() {
    return new ModelConverter() {
      @Override
      public Schema<?> resolve(
          AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema<?> schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
        if (schema == null || !raw.isRecord() || !raw.getPackageName().startsWith("dev.apipilot")) {
          return schema;
        }
        String ref = schema.get$ref();
        Schema<?> model =
            ref == null
                ? schema
                : context.getDefinedModels().get(ref.substring(ref.lastIndexOf('/') + 1));
        List<String> required =
            Arrays.stream(raw.getRecordComponents())
                .filter(c -> !c.getAnnotatedType().isAnnotationPresent(Nullable.class))
                .map(RecordComponent::getName)
                .toList();
        if (model != null && !required.isEmpty()) {
          model.setRequired(required);
        }
        return schema;
      }
    };
  }
}
