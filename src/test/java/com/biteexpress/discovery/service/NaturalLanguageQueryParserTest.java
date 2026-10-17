package com.biteexpress.discovery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.biteexpress.discovery.service.NaturalLanguageQueryParser.Parsed;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NaturalLanguageQueryParserTest {
  private final NaturalLanguageQueryParser parser = new NaturalLanguageQueryParser();
  private final Set<String> cities = Set.of("gurgaon", "new delhi", "mumbai");
  private final Set<String> cuisines =
      Set.of("south indian", "north indian", "chinese", "street food", "fast food", "indian");

  @Test
  void bestRestaurantsInCityWithCuisine() {
    Parsed p = parser.parse("Best restaurants in Gurgaon with South Indian Food", cities, cuisines);
    assertEquals("gurgaon", p.city());
    assertEquals(List.of("south indian"), p.cuisines());
    assertTrue(p.best());
    assertFalse(p.nearest());
    assertNull(p.text());
  }

  @Test
  void priceCeiling() {
    Parsed p = parser.parse("Restaurants in Gurgaon under Rs. 1000 for two", cities, cuisines);
    assertEquals("gurgaon", p.city());
    assertEquals(1000, p.maxCost());
    assertNull(p.text());
  }

  @Test
  void nearestWithUnknownDishFallsBackToFreeText() {
    Parsed p = parser.parse("Nearest place that serves Vada Pav", cities, cuisines);
    assertTrue(p.nearest());
    assertEquals("vada pav", p.text());
    assertNull(p.city());
  }

  @Test
  void cityAliasAndIndianCurrencyFormats() {
    Parsed p = parser.parse("cheap chinese in Gurugram below ₹1,500", cities, cuisines);
    assertEquals("gurgaon", p.city());
    assertEquals(1500, p.maxCost());
    assertEquals(List.of("chinese"), p.cuisines());
  }

  @Test
  void longestCuisineWinsAndRatingIsParsed() {
    Parsed p = parser.parse("top rated street food in delhi, 4+ stars", cities, cuisines);
    assertEquals("new delhi", p.city());
    assertEquals(List.of("street food"), p.cuisines());
    assertEquals(4.0, p.minRating());
    assertTrue(p.best());
  }

  @Test
  void bareNumberIsNotMistakenForRating() {
    Parsed p = parser.parse("pizza places over 500", cities, cuisines);
    assertNull(p.minRating());
  }

  @Test
  void nullAndEmptyAreSafe() {
    assertNull(parser.parse(null, cities, cuisines).text());
    assertNull(parser.parse("", cities, cuisines).text());
  }
}
