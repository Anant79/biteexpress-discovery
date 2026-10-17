package com.biteexpress.discovery.dto;

import com.biteexpress.discovery.domain.RestaurantDraft;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRestaurantRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String address,
    @Size(max = 200) String locality,
    @Size(max = 300) String localityVerbose,
    @NotBlank @Size(max = 100) String city,
    @NotNull Integer countryCode,
    @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
    @NotEmpty List<@NotBlank String> cuisines,
    @PositiveOrZero Integer averageCostForTwo,
    @Size(max = 60) String currency,
    @Min(1) @Max(4) Integer priceRange,
    Boolean tableBooking,
    Boolean onlineDelivery,
    Boolean deliveringNow,
    Boolean switchToOrderMenu) {

  public RestaurantDraft toDraft() {
    return new RestaurantDraft(
        null,
        name.trim(),
        address.trim(),
        locality,
        localityVerbose,
        city.trim(),
        countryCode,
        latitude,
        longitude,
        cuisines.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList(),
        averageCostForTwo,
        currency,
        priceRange,
        Boolean.TRUE.equals(tableBooking),
        Boolean.TRUE.equals(onlineDelivery),
        Boolean.TRUE.equals(deliveringNow),
        Boolean.TRUE.equals(switchToOrderMenu),
        0.0, // new restaurants start unrated, same convention as the dataset
        "White",
        "Not rated",
        0);
  }
}
