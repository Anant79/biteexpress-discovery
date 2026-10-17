package com.biteexpress.discovery.ingest;

import com.biteexpress.discovery.domain.RestaurantDraft;
import com.biteexpress.discovery.persistence.RestaurantDocuments;
import com.biteexpress.discovery.persistence.RestaurantRepository;
import com.biteexpress.discovery.service.CatalogVocabulary;
import com.mongodb.bulk.BulkWriteResult;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads the Zomato CSV on startup. Cleanup rules (all visible in toDraft): latin-1 decoding, blank
 * names skipped, 0/0 coordinates treated as "no location", cuisines split into an array, Yes/No
 * flags to booleans. Loading is an idempotent upsert keyed on the dataset's Restaurant ID.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class CsvSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(CsvSeeder.class);
  private static final int BATCH = 1000;
  private static final Path SAMPLE = Path.of("data/sample_restaurants.csv");

  private final RestaurantRepository repository;
  private final CatalogVocabulary vocabulary;
  private final String path;
  private final String tenantId;

  public CsvSeeder(
      RestaurantRepository repository,
      CatalogVocabulary vocabulary,
      @Value("${app.seed.path}") String path,
      @Value("${app.default-tenant}") String tenantId) {
    this.repository = repository;
    this.vocabulary = vocabulary;
    this.path = path;
    this.tenantId = tenantId;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    Path file = Path.of(path);
    if (!Files.exists(file)) {
      if (Files.exists(SAMPLE)) {
        log.warn("{} not found, loading the small sample file {} instead", file, SAMPLE);
        file = SAMPLE;
      } else {
        log.warn("No seed file found at {}; starting with an empty catalogue", file);
        return;
      }
    }

    int read = 0;
    int skipped = 0;
    long inserted = 0;
    long updated = 0;
    List<Document> batch = new ArrayList<>(BATCH);

    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1);
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build()
                .parse(reader)) {
      for (CSVRecord row : parser) {
        read++;
        Optional<RestaurantDraft> draft = toDraft(row);
        if (draft.isEmpty()) {
          skipped++;
          continue;
        }
        batch.add(RestaurantDocuments.build(tenantId, draft.get()));
        if (batch.size() == BATCH) {
          BulkWriteResult r = repository.bulkUpsert(tenantId, batch);
          inserted += r.getUpserts().size();
          updated += r.getModifiedCount();
          batch.clear();
        }
      }
    }
    if (!batch.isEmpty()) {
      BulkWriteResult r = repository.bulkUpsert(tenantId, batch);
      inserted += r.getUpserts().size();
      updated += r.getModifiedCount();
    }
    vocabulary.invalidate(tenantId);
    log.info("Seed finished: read={} skipped={} inserted={} updated={}", read, skipped, inserted, updated);
  }

  static Optional<RestaurantDraft> toDraft(CSVRecord r) {
    String name = get(r, "Restaurant Name");
    Long externalId = parseLong(get(r, "Restaurant ID"));
    if (name.isBlank() || externalId == null) {
      return Optional.empty();
    }
    List<String> cuisines =
        Arrays.stream(get(r, "Cuisines").split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
    Integer countryCode = parseInt(get(r, "Country Code"));
    Double rating = parseDouble(get(r, "Aggregate rating"));
    Integer votes = parseInt(get(r, "Votes"));
    return Optional.of(
        new RestaurantDraft(
            externalId,
            name,
            get(r, "Address"),
            get(r, "Locality"),
            get(r, "Locality Verbose"),
            get(r, "City"),
            countryCode == null ? 0 : countryCode,
            parseDouble(get(r, "Latitude")),
            parseDouble(get(r, "Longitude")),
            cuisines,
            parseInt(get(r, "Average Cost for two")),
            get(r, "Currency"),
            parseInt(get(r, "Price range")),
            yes(get(r, "Has Table booking")),
            yes(get(r, "Has Online delivery")),
            yes(get(r, "Is delivering now")),
            yes(get(r, "Switch to order menu")),
            rating == null ? 0.0 : rating,
            get(r, "Rating color"),
            get(r, "Rating text"),
            votes == null ? 0 : votes));
  }

  private static String get(CSVRecord r, String column) {
    return r.isMapped(column) && r.isSet(column) ? r.get(column).trim() : "";
  }

  private static boolean yes(String v) {
    return "yes".equalsIgnoreCase(v);
  }

  private static Long parseLong(String v) {
    try {
      return Long.parseLong(v);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Integer parseInt(String v) {
    try {
      return Integer.parseInt(v);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Double parseDouble(String v) {
    try {
      return Double.parseDouble(v);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
