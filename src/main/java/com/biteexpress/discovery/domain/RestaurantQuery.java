package com.biteexpress.discovery.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Normalised listing/search parameters. Spring binds query-string parameters straight into this
 * record, so the component names are the public HTTP parameter names.
 */
public record RestaurantQuery(
    String name,
    String city,
    List<String> cuisines,
    Double minRating,
    Integer maxCost,
    Integer minVotes,
    Double lat,
    Double lng,
    Double radiusKm,
    String sort,
    String order,
    String text,
    Integer page,
    Integer size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public RestaurantQuery {
    name = blankToNull(name);
    city = blankToNull(city);
    cuisines =
        cuisines == null
            ? List.of()
            : cuisines.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    sort = lower(sort);
    order = lower(order);
    text = blankToNull(text);
    page = page == null ? 0 : page;
    size = size == null ? DEFAULT_SIZE : size;
  }

  public boolean hasLocation() {
    return lat != null && lng != null;
  }

  public List<String> textTerms() {
    if (text == null) {
      return List.of();
    }
    return Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\s+"))
        .filter(t -> !t.isBlank())
        .toList();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String lower(String s) {
    String v = blankToNull(s);
    return v == null ? null : v.toLowerCase(Locale.ROOT);
  }
}
