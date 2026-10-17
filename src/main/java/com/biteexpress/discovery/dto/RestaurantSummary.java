package com.biteexpress.discovery.dto;

import java.util.List;

/** Listing item: exactly the attributes the assignment requires, plus cost for price-aware UIs. */
public record RestaurantSummary(
    String id,
    String name,
    String address,
    String city,
    String country,
    List<String> cuisines,
    Double averageRating,
    Double distanceKm,
    Integer averageCostForTwo,
    String currency) {}
