package com.biteexpress.discovery.dto;

import java.util.List;
import org.bson.Document;

/** Maps raw BSON documents to API responses. */
public final class RestaurantMapper {
  private RestaurantMapper() {}

  public static RestaurantResponse toResponse(Document d) {
    Document pricing = sub(d, "pricing");
    Document services = sub(d, "services");
    Document rating = sub(d, "rating");
    Double lat = null;
    Double lng = null;
    Document location = d.get("location", Document.class);
    if (location != null) {
      List<?> c = location.get("coordinates", List.class);
      if (c != null && c.size() == 2) {
        lng = dbl(c.get(0));
        lat = dbl(c.get(1));
      }
    }
    return new RestaurantResponse(
        d.getObjectId("_id").toHexString(),
        longVal(d.get("externalId")),
        d.getString("name"),
        d.getString("address"),
        d.getString("locality"),
        d.getString("localityVerbose"),
        d.getString("city"),
        d.getString("country"),
        intg(d.get("countryCode")),
        lat,
        lng,
        d.getList("cuisines", String.class, List.of()),
        new RestaurantResponse.Pricing(
            intg(pricing.get("averageCostForTwo")),
            pricing.getString("currency"),
            intg(pricing.get("priceRange"))),
        new RestaurantResponse.Services(
            bool(services, "tableBooking"),
            bool(services, "onlineDelivery"),
            bool(services, "deliveringNow"),
            bool(services, "switchToOrderMenu")),
        new RestaurantResponse.Rating(
            dbl(rating.get("average")),
            rating.getString("color"),
            rating.getString("text"),
            intg(rating.get("votes"))),
        d.getDate("createdAt") == null ? null : d.getDate("createdAt").toInstant(),
        d.getDate("updatedAt") == null ? null : d.getDate("updatedAt").toInstant());
  }

  public static RestaurantSummary toSummary(Document d) {
    Document pricing = sub(d, "pricing");
    Double distanceKm = null;
    Double meters = dbl(d.get("distanceMeters"));
    if (meters != null) {
      distanceKm = Math.round(meters / 10.0) / 100.0; // metres -> km, 2 decimals
    }
    return new RestaurantSummary(
        d.getObjectId("_id").toHexString(),
        d.getString("name"),
        d.getString("address"),
        d.getString("city"),
        d.getString("country"),
        d.getList("cuisines", String.class, List.of()),
        dbl(sub(d, "rating").get("average")),
        distanceKm,
        intg(pricing.get("averageCostForTwo")),
        pricing.getString("currency"));
  }

  private static Document sub(Document d, String key) {
    Document s = d.get(key, Document.class);
    return s == null ? new Document() : s;
  }

  private static Double dbl(Object o) {
    return o instanceof Number n ? n.doubleValue() : null;
  }

  private static Integer intg(Object o) {
    return o instanceof Number n ? n.intValue() : null;
  }

  private static Long longVal(Object o) {
    return o instanceof Number n ? n.longValue() : null;
  }

  private static boolean bool(Document d, String key) {
    return Boolean.TRUE.equals(d.get(key));
  }
}
