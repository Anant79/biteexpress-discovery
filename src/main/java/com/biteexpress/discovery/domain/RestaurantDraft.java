package com.biteexpress.discovery.domain;

import java.util.List;

/**
 * Canonical, source-agnostic restaurant data. Both the REST API (POST) and the CSV loader produce a
 * draft; {@code RestaurantDocuments} turns it into the one MongoDB document shape.
 */
public record RestaurantDraft(
    Long externalId,
    String name,
    String address,
    String locality,
    String localityVerbose,
    String city,
    int countryCode,
    Double latitude,
    Double longitude,
    List<String> cuisines,
    Integer averageCostForTwo,
    String currency,
    Integer priceRange,
    boolean tableBooking,
    boolean onlineDelivery,
    boolean deliveringNow,
    boolean switchToOrderMenu,
    double ratingAverage,
    String ratingColor,
    String ratingText,
    int votes) {

  /** The dataset uses 0,0 for "unknown". Such rows are loaded without a location. */
  public boolean hasValidLocation() {
    if (latitude == null || longitude == null) {
      return false;
    }
    boolean inRange =
        latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    return inRange && !(latitude == 0.0 && longitude == 0.0);
  }
}
