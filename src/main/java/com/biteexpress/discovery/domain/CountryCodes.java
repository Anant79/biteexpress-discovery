package com.biteexpress.discovery.domain;

import java.util.Map;

/** Country codes used by the Zomato dataset. */
public final class CountryCodes {
  private static final Map<Integer, String> NAMES =
      Map.ofEntries(
          Map.entry(1, "India"),
          Map.entry(14, "Australia"),
          Map.entry(30, "Brazil"),
          Map.entry(37, "Canada"),
          Map.entry(94, "Indonesia"),
          Map.entry(148, "New Zealand"),
          Map.entry(162, "Philippines"),
          Map.entry(166, "Qatar"),
          Map.entry(184, "Singapore"),
          Map.entry(189, "South Africa"),
          Map.entry(191, "Sri Lanka"),
          Map.entry(208, "Turkey"),
          Map.entry(214, "UAE"),
          Map.entry(215, "United Kingdom"),
          Map.entry(216, "United States"));

  private CountryCodes() {}

  public static String nameOf(int code) {
    return NAMES.getOrDefault(code, "Unknown");
  }
}
