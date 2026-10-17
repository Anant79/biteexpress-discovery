package com.biteexpress.discovery.service;

import com.biteexpress.discovery.domain.BadRequestException;
import com.biteexpress.discovery.domain.NotFoundException;
import com.biteexpress.discovery.domain.RestaurantQuery;
import com.biteexpress.discovery.dto.CreateRestaurantRequest;
import com.biteexpress.discovery.dto.PageResponse;
import com.biteexpress.discovery.dto.RestaurantMapper;
import com.biteexpress.discovery.dto.RestaurantResponse;
import com.biteexpress.discovery.dto.RestaurantSummary;
import com.biteexpress.discovery.persistence.RestaurantDocuments;
import com.biteexpress.discovery.persistence.RestaurantRepository;
import com.biteexpress.discovery.service.NaturalLanguageQueryParser.Parsed;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RestaurantService {
  private static final Set<String> SORTS = Set.of("distance", "rating", "name", "cost");
  private static final Set<String> ORDERS = Set.of("asc", "desc");
  private static final int MAX_OFFSET = 10_000; // guards against very deep skip() scans

  private final RestaurantRepository repository;
  private final CatalogVocabulary vocabulary;
  private final NaturalLanguageQueryParser parser = new NaturalLanguageQueryParser();
  private final int bestMinVotes;

  public RestaurantService(
      RestaurantRepository repository,
      CatalogVocabulary vocabulary,
      @Value("${app.search.best-min-votes:20}") int bestMinVotes) {
    this.repository = repository;
    this.vocabulary = vocabulary;
    this.bestMinVotes = bestMinVotes;
  }

  public RestaurantResponse create(String tenantId, CreateRestaurantRequest request) {
    Document doc = RestaurantDocuments.build(tenantId, request.toDraft());
    repository.insert(doc);
    vocabulary.invalidate(tenantId);
    return RestaurantMapper.toResponse(doc);
  }

  public RestaurantResponse get(String tenantId, String id) {
    if (!ObjectId.isValid(id)) {
      throw new BadRequestException("'" + id + "' is not a valid restaurant id");
    }
    return repository
        .findById(tenantId, new ObjectId(id))
        .map(RestaurantMapper::toResponse)
        .orElseThrow(() -> new NotFoundException("Restaurant " + id + " not found"));
  }

  public PageResponse<RestaurantSummary> list(String tenantId, RestaurantQuery query) {
    validate(query);
    return execute(tenantId, query, null);
  }

  /** Same filters as list(), plus a free-text sentence that is turned into structured filters. */
  public PageResponse<RestaurantSummary> search(
      String tenantId, String sentence, RestaurantQuery explicit) {
    if (sentence == null || sentence.isBlank()) {
      return list(tenantId, explicit);
    }
    CatalogVocabulary.Snapshot vocab = vocabulary.forTenant(tenantId);
    Parsed parsed = parser.parse(sentence, vocab.cities(), vocab.cuisines());
    RestaurantQuery merged = merge(explicit, parsed);
    validate(merged);
    return execute(tenantId, merged, parsed);
  }

  /** Explicit request parameters always win over what we inferred from the sentence. */
  private RestaurantQuery merge(RestaurantQuery e, Parsed p) {
    String sort = e.sort() != null ? e.sort() : p.nearest() ? "distance" : p.best() ? "rating" : null;
    Integer minVotes = e.minVotes() != null ? e.minVotes() : (p.best() ? bestMinVotes : null);
    String text =
        e.text() != null && p.text() != null
            ? e.text() + " " + p.text()
            : e.text() != null ? e.text() : p.text();
    return new RestaurantQuery(
        e.name(),
        e.city() != null ? e.city() : p.city(),
        !e.cuisines().isEmpty() ? e.cuisines() : p.cuisines(),
        e.minRating() != null ? e.minRating() : p.minRating(),
        e.maxCost() != null ? e.maxCost() : p.maxCost(),
        minVotes,
        e.lat(),
        e.lng(),
        e.radiusKm(),
        sort,
        e.order(),
        text,
        e.page(),
        e.size());
  }

  private PageResponse<RestaurantSummary> execute(
      String tenantId, RestaurantQuery q, Parsed interpreted) {
    List<Document> docs = repository.search(tenantId, q);
    boolean hasMore = docs.size() > q.size();
    List<RestaurantSummary> items =
        docs.stream().limit(q.size()).map(RestaurantMapper::toSummary).toList();
    return new PageResponse<>(items, q.page(), q.size(), hasMore, interpreted);
  }

  private void validate(RestaurantQuery q) {
    if ((q.lat() == null) != (q.lng() == null)) {
      throw new BadRequestException("lat and lng must be provided together");
    }
    if (q.hasLocation()) {
      if (q.lat() < -90 || q.lat() > 90 || q.lng() < -180 || q.lng() > 180) {
        throw new BadRequestException("lat must be within [-90, 90] and lng within [-180, 180]");
      }
    }
    if (q.radiusKm() != null && (!q.hasLocation() || q.radiusKm() <= 0)) {
      throw new BadRequestException("radiusKm must be positive and needs lat and lng");
    }
    if (q.sort() != null && !SORTS.contains(q.sort())) {
      throw new BadRequestException("sort must be one of " + SORTS);
    }
    if ("distance".equals(q.sort()) && !q.hasLocation()) {
      throw new BadRequestException("Distance-based results need lat and lng");
    }
    if (q.order() != null && !ORDERS.contains(q.order())) {
      throw new BadRequestException("order must be asc or desc");
    }
    if (q.minRating() != null && (q.minRating() < 0 || q.minRating() > 5)) {
      throw new BadRequestException("minRating must be between 0 and 5");
    }
    if (q.size() < 1 || q.size() > RestaurantQuery.MAX_SIZE) {
      throw new BadRequestException("size must be between 1 and " + RestaurantQuery.MAX_SIZE);
    }
    if (q.page() < 0 || (long) q.page() * q.size() > MAX_OFFSET) {
      throw new BadRequestException("page is out of range (max offset " + MAX_OFFSET + ")");
    }
  }
}
