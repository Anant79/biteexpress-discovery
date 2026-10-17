package com.biteexpress.discovery.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.biteexpress.discovery.domain.RestaurantQuery;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** Pure unit tests (no database): assert the shape of the generated aggregation pipeline. */
class RestaurantPipelinesTest {

  private RestaurantQuery query(Double lat, Double lng, String sort, String text) {
    return new RestaurantQuery(
        null, "Gurgaon", List.of("South Indian"), null, 1000, null, lat, lng, null, sort, null,
        text, 0, 20);
  }

  @Test
  void startsWithGeoNearAndSortsByDistanceWhenLocationGiven() {
    List<Document> p = RestaurantPipelines.search("t1", query(28.49, 77.08, null, null));
    Document near = p.get(0).get("$geoNear", Document.class);
    assertEquals("distanceMeters", near.getString("distanceField"));
    Document filter = near.get("query", Document.class);
    assertEquals("t1", filter.getString("tenantId"));
    assertEquals("gurgaon", filter.getString("cityKey"));
    assertTrue(p.get(1).get("$sort", Document.class).containsKey("distanceMeters"));
  }

  @Test
  void usesMatchAndRatingSortWithoutLocation() {
    List<Document> p = RestaurantPipelines.search("t1", query(null, null, null, null));
    assertTrue(p.get(0).containsKey("$match"));
    assertTrue(p.get(1).get("$sort", Document.class).containsKey("rating.average"));
  }

  @Test
  void escapesRegexMetacharactersInFreeText() {
    assertEquals("a\\.b\\(c\\)", RestaurantPipelines.escape("a.b(c)"));
  }

  @Test
  void priceCeilingExcludesUnknownCost() {
    List<Document> p = RestaurantPipelines.search("t1", query(null, null, null, null));
    Document cost =
        p.get(0).get("$match", Document.class).get("pricing.averageCostForTwo", Document.class);
    assertEquals(0, cost.getInteger("$gt"));
    assertEquals(1000, cost.getInteger("$lte"));
  }
}
