package com.biteexpress.discovery.persistence;

import com.biteexpress.discovery.domain.CountryCodes;
import com.biteexpress.discovery.domain.Keys;
import com.biteexpress.discovery.domain.RestaurantDraft;
import java.util.Date;
import java.util.List;
import org.bson.Document;

/** The single place that defines the shape of a restaurant document in MongoDB. */
public final class RestaurantDocuments {
  public static final int SCHEMA_VERSION = 1;

  private RestaurantDocuments() {}

  public static Document build(String tenantId, RestaurantDraft d) {
    Date now = new Date();
    Document doc = new Document("tenantId", tenantId);
    put(doc, "externalId", d.externalId());
    doc.append("name", d.name());
    put(doc, "address", d.address());
    put(doc, "locality", d.locality());
    put(doc, "localityVerbose", d.localityVerbose());
    doc.append("city", d.city()).append("cityKey", Keys.city(d.city()));
    doc.append("countryCode", d.countryCode()).append("country", CountryCodes.nameOf(d.countryCode()));

    if (d.hasValidLocation()) {
      // GeoJSON order is [longitude, latitude]. Getting this backwards is the classic bug.
      doc.append(
          "location",
          new Document("type", "Point").append("coordinates", List.of(d.longitude(), d.latitude())));
    }

    List<String> cuisines = d.cuisines() == null ? List.of() : d.cuisines();
    doc.append("cuisines", cuisines);
    doc.append("cuisineKeys", cuisines.stream().map(Keys::of).distinct().toList());

    Document pricing = new Document();
    put(pricing, "averageCostForTwo", d.averageCostForTwo());
    put(pricing, "currency", d.currency());
    put(pricing, "priceRange", d.priceRange());
    doc.append("pricing", pricing);

    doc.append(
        "services",
        new Document("tableBooking", d.tableBooking())
            .append("onlineDelivery", d.onlineDelivery())
            .append("deliveringNow", d.deliveringNow())
            .append("switchToOrderMenu", d.switchToOrderMenu()));

    doc.append(
        "rating",
        new Document("average", d.ratingAverage())
            .append("color", d.ratingColor())
            .append("text", d.ratingText())
            .append("votes", d.votes()));

    doc.append("schemaVersion", SCHEMA_VERSION);
    doc.append("createdAt", now).append("updatedAt", now);
    return doc;
  }

  private static void put(Document doc, String key, Object value) {
    if (value != null) {
      doc.append(key, value);
    }
  }
}
