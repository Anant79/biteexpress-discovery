package com.biteexpress.discovery.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Normalisation helpers: lower-cased "keys" are what we index and filter on. */
public final class Keys {
  private static final Map<String, String> CITY_ALIASES =
      Map.of(
          "gurugram", "gurgaon",
          "bengaluru", "bangalore",
          "bombay", "mumbai",
          "calcutta", "kolkata",
          "madras", "chennai",
          "delhi", "new delhi");

  private Keys() {}

  public static String of(String value) {
    if (value == null) {
      return null;
    }
    return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  /** Same as {@link #of} but also maps well-known city aliases (Gurugram -> gurgaon). */
  public static String city(String value) {
    String key = of(value);
    return key == null ? null : CITY_ALIASES.getOrDefault(key, key);
  }

  public static Set<String> cityAliasKeys() {
    return CITY_ALIASES.keySet();
  }
}
