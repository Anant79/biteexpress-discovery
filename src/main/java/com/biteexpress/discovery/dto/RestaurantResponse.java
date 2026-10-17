package com.biteexpress.discovery.dto;

import java.time.Instant;
import java.util.List;

public record RestaurantResponse(
    String id,
    Long externalId,
    String name,
    String address,
    String locality,
    String localityVerbose,
    String city,
    String country,
    Integer countryCode,
    Double latitude,
    Double longitude,
    List<String> cuisines,
    Pricing pricing,
    Services services,
    Rating rating,
    Instant createdAt,
    Instant updatedAt) {

  public record Pricing(Integer averageCostForTwo, String currency, Integer priceRange) {}

  public record Services(
      boolean tableBooking,
      boolean onlineDelivery,
      boolean deliveringNow,
      boolean switchToOrderMenu) {}

  public record Rating(Double average, String color, String text, Integer votes) {}
}
