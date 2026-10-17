package com.biteexpress.discovery.persistence;

import com.biteexpress.discovery.domain.Keys;
import com.biteexpress.discovery.domain.RestaurantQuery;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

/**
 * Builds the aggregation pipeline for listing/search. Kept free of any database access so the
 * pipeline shape can be unit-tested and read top to bottom in an interview.
 *
 * <p>Why an aggregation and not a plain find()? {@code $geoNear} is the only way to get the
 * computed distance back per document, and it must be the first stage. When there is no location
 * the pipeline starts with an ordinary {@code $match}, so both cases share one code path.
 *
 * <p>Known limitation: {@code $geoNear} cannot be combined with {@code $text} in the same
 * pipeline, so free-text terms use anchored-by-nothing regex matches here. At production scale this
 * is where Atlas Search (one {@code $search} stage with text + geo + facets) would replace it.
 */
public final class RestaurantPipelines {
  private RestaurantPipelines() {}

  public static List<Document> search(String tenantId, RestaurantQuery q) {
    Document match = new Document("tenantId", tenantId);
    if (q.city() != null) {
      match.append("cityKey", Keys.city(q.city()));
    }
    if (!q.cuisines().isEmpty()) {
      match.append("cuisineKeys", new Document("$in", q.cuisines().stream().map(Keys::of).toList()));
    }
    if (q.minRating() != null) {
      match.append("rating.average", new Document("$gte", q.minRating()));
    }
    if (q.minVotes() != null) {
      match.append("rating.votes", new Document("$gte", q.minVotes()));
    }
    if (q.maxCost() != null) {
      // 0 means "unknown" in the source data, so it must not satisfy a price ceiling.
      match.append(
          "pricing.averageCostForTwo", new Document("$gt", 0).append("$lte", q.maxCost()));
    }

    List<Document> all = new ArrayList<>();
    if (q.name() != null) {
      all.add(new Document("name", regex(q.name())));
    }
    for (String term : q.textTerms()) {
      all.add(
          new Document(
              "$or",
              List.of(
                  new Document("name", regex(term)),
                  new Document("cuisines", regex(term)),
                  new Document("locality", regex(term)))));
    }
    if (!all.isEmpty()) {
      match.append("$and", all);
    }

    boolean geo = q.hasLocation();
    List<Document> pipeline = new ArrayList<>();
    if (geo) {
      Document near =
          new Document(
                  "near",
                  new Document("type", "Point").append("coordinates", List.of(q.lng(), q.lat())))
              .append("distanceField", "distanceMeters")
              .append("spherical", true)
              .append("query", match);
      if (q.radiusKm() != null) {
        near.append("maxDistance", q.radiusKm() * 1000.0);
      }
      pipeline.add(new Document("$geoNear", near));
    } else {
      pipeline.add(new Document("$match", match));
    }

    pipeline.add(new Document("$sort", sortSpec(q, geo)));
    pipeline.add(new Document("$skip", q.page() * q.size()));
    pipeline.add(new Document("$limit", q.size() + 1)); // +1 lets the service compute hasMore
    pipeline.add(
        new Document(
            "$project",
            new Document("name", 1)
                .append("address", 1)
                .append("locality", 1)
                .append("city", 1)
                .append("country", 1)
                .append("cuisines", 1)
                .append("rating.average", 1)
                .append("pricing.averageCostForTwo", 1)
                .append("pricing.currency", 1)
                .append("distanceMeters", 1)));
    return pipeline;
  }

  static Document sortSpec(RestaurantQuery q, boolean geo) {
    String key = q.sort() != null ? q.sort() : (geo ? "distance" : "rating");
    int dir;
    if ("asc".equals(q.order())) {
      dir = 1;
    } else if ("desc".equals(q.order())) {
      dir = -1;
    } else {
      dir = "rating".equals(key) ? -1 : 1; // best rating first, nearest/cheapest/A-Z first
    }
    Document sort = new Document();
    switch (key) {
      case "distance" -> sort.append("distanceMeters", dir);
      case "name" -> sort.append("name", dir);
      case "cost" -> sort.append("pricing.averageCostForTwo", dir);
      default -> sort.append("rating.average", dir).append("rating.votes", -1);
    }
    // Unique tie-breaker: without it, skip/limit pagination can repeat or drop rows.
    return sort.append("_id", 1);
  }

  private static Document regex(String term) {
    return new Document("$regex", escape(term)).append("$options", "i");
  }

  static String escape(String s) {
    return s.replaceAll("[\\\\.^$|?*+()\\[\\]{}]", "\\\\$0");
  }
}
