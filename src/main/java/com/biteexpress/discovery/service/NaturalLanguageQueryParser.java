package com.biteexpress.discovery.service;

import com.biteexpress.discovery.domain.Keys;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based natural-language understanding for restaurant search. Deliberately deterministic and
 * dependency-free: it turns a sentence into the same structured filters the listing endpoint uses,
 * and the API echoes the interpretation back so a client can show "we understood: ...".
 *
 * <p>It only recognises cities and cuisines that actually exist in the catalogue (passed in), so a
 * word like "pav" falls through to free-text matching instead of being mistaken for a cuisine.
 */
public final class NaturalLanguageQueryParser {

  /** What we understood from the sentence. */
  public record Parsed(
      String city,
      List<String> cuisines,
      Integer maxCost,
      Double minRating,
      boolean nearest,
      boolean best,
      String text) {}

  private static final Pattern PRICE =
      Pattern.compile(
          "(?:under|below|less than|within|upto|up to|maximum|max|<=|<)\\s*(?:rs\\.?|inr|₹)?\\s*(\\d{2,6})(?!\\d)");
  private static final Pattern RATING_A =
      Pattern.compile("(?:rated|rating)\\s*(?:above|over|of|at least|>=|>)?\\s*(\\d(?:\\.\\d)?)(?!\\d)");
  private static final Pattern RATING_B =
      Pattern.compile("(\\d(?:\\.\\d)?)\\s*\\+?\\s*(?:stars?|rating)(?![a-z])");

  private static final List<String> NEAREST_WORDS =
      List.of("nearest", "closest", "nearby", "near me", "near by");
  private static final List<String> BEST_WORDS =
      List.of("highest rated", "top rated", "best", "top");

  private static final Set<String> STOP_WORDS =
      Set.of(
          "a", "an", "the", "in", "at", "near", "around", "of", "for", "to", "and", "or", "with",
          "that", "which", "who", "serves", "serve", "serving", "has", "have", "having",
          "restaurant", "restaurants", "place", "places", "food", "foods", "cuisine", "cuisines",
          "eat", "find", "show", "me", "some", "good", "great", "two", "rs", "inr", "above", "over",
          "under", "below", "within", "get", "want", "looking", "i", "need", "where", "can",
          "cheap", "affordable", "budget", "rated", "rating", "stars", "star");

  public Parsed parse(String raw, Set<String> knownCities, Set<String> knownCuisines) {
    Text t =
        new Text(
            (raw == null ? "" : raw)
                .toLowerCase(Locale.ROOT)
                .replaceAll("(?<=\\d),(?=\\d{3})", "")); // 1,000 -> 1000

    Integer maxCost = null;
    Matcher price = PRICE.matcher(t.value);
    if (price.find()) {
      maxCost = Integer.parseInt(price.group(1));
      t.cut(price);
    }

    Double minRating = null;
    Matcher rating = RATING_A.matcher(t.value);
    if (!rating.find()) {
      rating = RATING_B.matcher(t.value);
      if (!rating.find()) {
        rating = null;
      }
    }
    if (rating != null) {
      double value = Double.parseDouble(rating.group(1));
      if (value <= 5.0) {
        minRating = value;
        t.cut(rating);
      }
    }

    // Punctuation is only meaningful for the price/rating patterns above.
    t.value = " " + t.value.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ") + " ";

    boolean nearest = takeAny(t, NEAREST_WORDS);
    boolean best = takeAny(t, BEST_WORDS);

    // Longest names first so "south indian" wins over "indian" and "new delhi" over "delhi".
    List<String> cityCandidates = new ArrayList<>(knownCities);
    cityCandidates.addAll(Keys.cityAliasKeys());
    cityCandidates.sort(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s));
    String city = null;
    for (String candidate : cityCandidates) {
      if (t.take(candidate)) {
        city = Keys.city(candidate);
        break;
      }
    }

    List<String> cuisineCandidates = new ArrayList<>(knownCuisines);
    cuisineCandidates.sort(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s));
    Set<String> cuisines = new LinkedHashSet<>();
    for (String candidate : cuisineCandidates) {
      if (t.take(candidate)) {
        cuisines.add(candidate);
      }
    }

    List<String> leftovers = new ArrayList<>();
    for (String token : t.value.trim().split("\\s+")) {
      if (!token.isBlank() && !STOP_WORDS.contains(token)) {
        leftovers.add(token);
      }
    }

    return new Parsed(
        city,
        List.copyOf(cuisines),
        maxCost,
        minRating,
        nearest,
        best,
        leftovers.isEmpty() ? null : String.join(" ", leftovers));
  }

  private static boolean takeAny(Text t, List<String> phrases) {
    boolean found = false;
    for (String phrase : phrases) {
      found |= t.take(phrase);
    }
    return found;
  }

  /** Mutable working copy of the sentence from which recognised phrases are removed. */
  private static final class Text {
    String value;

    Text(String value) {
      this.value = value;
    }

    void cut(Matcher m) {
      value = value.substring(0, m.start()) + " " + value.substring(m.end());
    }

    boolean take(String phrase) {
      Matcher m =
          Pattern.compile("(?<![a-z0-9])" + Pattern.quote(phrase) + "(?![a-z0-9])")
              .matcher(value);
      if (!m.find()) {
        return false;
      }
      cut(m);
      return true;
    }
  }
}
