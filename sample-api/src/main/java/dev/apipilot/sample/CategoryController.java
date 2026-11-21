package dev.apipilot.sample;

import dev.apipilot.sample.Model.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only reference data: there is no way to create a category through the API. */
@RestController
@RequestMapping("/categories")
class CategoryController {

  private final Store store;

  CategoryController(Store store) {
    this.store = store;
  }

  @GetMapping
  List<Category> list() {
    return store.categories();
  }

  @GetMapping("/{categoryId}")
  Category get(@PathVariable UUID categoryId) {
    return store.category(categoryId);
  }
}
