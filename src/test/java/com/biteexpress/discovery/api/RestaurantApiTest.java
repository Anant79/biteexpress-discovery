package com.biteexpress.discovery.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteexpress.discovery.domain.RestaurantDraft;
import com.biteexpress.discovery.persistence.RestaurantDocuments;
import com.biteexpress.discovery.persistence.RestaurantRepository;
import com.biteexpress.discovery.service.CatalogVocabulary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-end API tests against a real MongoDB (needs Docker running). */
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class RestaurantApiTest {

  @Container @ServiceConnection
  static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  private static final String TENANT = "biteexpress";

  @Autowired MockMvc mvc;
  @Autowired RestaurantRepository repository;
  @Autowired CatalogVocabulary vocabulary;

  @BeforeEach
  void clean() {
    repository.deleteAll();
    vocabulary.invalidate(TENANT);
  }

  // Cyber Hub, Gurgaon ~ (28.4950 N, 77.0890 E)
  private void seed(
      long id, String name, String city, List<String> cuisines, double lat, double lng,
      int cost, double rating, int votes) {
    RestaurantDraft draft =
        new RestaurantDraft(
            id, name, "addr", "loc", "loc, " + city, city, 1, lat, lng, cuisines, cost,
            "Indian Rupees(Rs.)", 2, false, true, false, false, rating, "Green", "Good", votes);
    repository.bulkUpsert(TENANT, List.of(RestaurantDocuments.build(TENANT, draft)));
    vocabulary.invalidate(TENANT);
  }

  private void seedGurgaon() {
    seed(1, "Dosa Junction", "Gurgaon", List.of("South Indian"), 28.4950, 77.0890, 400, 4.3, 500);
    seed(2, "Pasta Lab", "Gurgaon", List.of("Italian"), 28.5040, 77.0890, 1500, 4.6, 400);
    seed(3, "Vada Pav Corner", "Gurgaon", List.of("Street Food"), 28.4960, 77.0900, 200, 4.1, 300);
    seed(4, "Marine Grill", "Mumbai", List.of("Seafood"), 18.9440, 72.8228, 1800, 4.0, 150);
  }

  private static final String VALID_BODY =
      """
      {"name":"Test Kitchen","address":"1 Main Rd","city":"Gurgaon","countryCode":1,
       "latitude":28.49,"longitude":77.08,"cuisines":["South Indian","Chinese"],
       "averageCostForTwo":600,"currency":"Indian Rupees(Rs.)","priceRange":2}
      """;

  @Test
  void createThenGetRoundTrip() throws Exception {
    String location =
        mvc.perform(post("/v1/restaurants").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.name").value("Test Kitchen"))
            .andExpect(jsonPath("$.country").value("India"))
            .andReturn()
            .getResponse()
            .getHeader("Location");

    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cuisines", hasSize(2)))
        .andExpect(jsonPath("$.latitude").value(28.49))
        .andExpect(jsonPath("$.longitude").value(77.08))
        .andExpect(jsonPath("$.rating.text").value("Not rated"));
  }

  @Test
  void createRejectsInvalidPayload() throws Exception {
    mvc.perform(
            post("/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"latitude\":200}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").exists())
        .andExpect(jsonPath("$.errors.latitude").exists());
  }

  @Test
  void getUnknownAndMalformedIds() throws Exception {
    mvc.perform(get("/v1/restaurants/64b7f0f2a1b2c3d4e5f60718")).andExpect(status().isNotFound());
    mvc.perform(get("/v1/restaurants/not-an-id")).andExpect(status().isBadRequest());
  }

  @Test
  void tenantsAreIsolated() throws Exception {
    String location =
        mvc.perform(post("/v1/restaurants").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andReturn()
            .getResponse()
            .getHeader("Location");
    mvc.perform(get(location).header("X-Tenant-Id", "other-brand"))
        .andExpect(status().isNotFound());
  }

  @Test
  void listFiltersByCityAndCuisineSortedByRating() throws Exception {
    seedGurgaon();
    mvc.perform(get("/v1/restaurants").param("city", "gurgaon"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(3)))
        .andExpect(jsonPath("$.items[0].name").value("Pasta Lab")) // 4.6 first
        .andExpect(jsonPath("$.items[0].distanceKm").doesNotExist());

    mvc.perform(get("/v1/restaurants").param("cuisines", "south indian"))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].name").value("Dosa Junction"));
  }

  @Test
  void distanceSortRadiusAndComputedDistance() throws Exception {
    seedGurgaon();
    mvc.perform(get("/v1/restaurants").param("lat", "28.4950").param("lng", "77.0890"))
        .andExpect(jsonPath("$.items[0].name").value("Dosa Junction"))
        .andExpect(jsonPath("$.items[0].distanceKm").value(0.0))
        .andExpect(jsonPath("$.items[1].name").value("Vada Pav Corner"));

    mvc.perform(
            get("/v1/restaurants")
                .param("lat", "28.4950").param("lng", "77.0890").param("radiusKm", "5"))
        .andExpect(jsonPath("$.items", hasSize(3))); // Mumbai excluded

    mvc.perform(get("/v1/restaurants").param("sort", "distance"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void paginationReportsHasMore() throws Exception {
    seedGurgaon();
    mvc.perform(get("/v1/restaurants").param("size", "2").param("page", "0"))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.hasMore").value(true));
    mvc.perform(get("/v1/restaurants").param("size", "2").param("page", "1"))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  void naturalLanguagePriceAndCity() throws Exception {
    seedGurgaon();
    mvc.perform(get("/v1/restaurants/search").param("q", "Restaurants in Gurgaon under Rs. 1000 for two"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interpretedAs.maxCost").value(1000))
        .andExpect(jsonPath("$.interpretedAs.city").value("gurgaon"))
        .andExpect(jsonPath("$.items", hasSize(2))); // Dosa Junction + Vada Pav Corner
  }

  @Test
  void naturalLanguageBestWithCuisine() throws Exception {
    seedGurgaon();
    mvc.perform(get("/v1/restaurants/search").param("q", "Best restaurants in Gurgaon with South Indian Food"))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].name").value("Dosa Junction"));
  }

  @Test
  void naturalLanguageNearestNeedsLocationThenWorks() throws Exception {
    seedGurgaon();
    mvc.perform(get("/v1/restaurants/search").param("q", "Nearest place that serves Vada Pav"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("lat and lng")));

    mvc.perform(
            get("/v1/restaurants/search")
                .param("q", "Nearest place that serves Vada Pav")
                .param("lat", "28.4950").param("lng", "77.0890"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("Vada Pav Corner"))
        .andExpect(jsonPath("$.items[0].distanceKm").exists());
  }

  @Test
  void searchWithoutQueryBehavesLikeList() throws Exception {
    seedGurgaon();
    mvc.perform(get("/v1/restaurants/search").param("city", "mumbai"))
        .andExpect(jsonPath("$.items", hasSize(1)));
  }
}
