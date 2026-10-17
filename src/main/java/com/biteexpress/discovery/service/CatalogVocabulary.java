package com.biteexpress.discovery.service;

import com.biteexpress.discovery.persistence.RestaurantRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-tenant cache of the cities and cuisines present in the catalogue, used by the natural-language
 * parser. A short TTL keeps multiple API instances roughly consistent without a message bus.
 */
@Component
public class CatalogVocabulary {
  private static final long TTL_MILLIS = Duration.ofMinutes(5).toMillis();

  public record Snapshot(Set<String> cities, Set<String> cuisines, long loadedAtMillis) {}

  private final RestaurantRepository repository;
  private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

  public CatalogVocabulary(RestaurantRepository repository) {
    this.repository = repository;
  }

  public Snapshot forTenant(String tenantId) {
    Snapshot cached = cache.get(tenantId);
    long now = System.currentTimeMillis();
    if (cached != null && now - cached.loadedAtMillis() < TTL_MILLIS) {
      return cached;
    }
    Snapshot fresh =
        new Snapshot(
            repository.distinctKeys(tenantId, "cityKey"),
            repository.distinctKeys(tenantId, "cuisineKeys"),
            now);
    cache.put(tenantId, fresh);
    return fresh;
  }

  public void invalidate(String tenantId) {
    cache.remove(tenantId);
  }
}
