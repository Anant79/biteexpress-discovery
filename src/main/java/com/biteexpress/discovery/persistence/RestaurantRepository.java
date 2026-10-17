package com.biteexpress.discovery.persistence;

import com.biteexpress.discovery.domain.RestaurantQuery;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.bulk.BulkWriteResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/** All MongoDB access lives here, using the plain driver API so every operation is explicit. */
@Repository
public class RestaurantRepository {
  public static final String COLLECTION = "restaurants";

  private final MongoCollection<Document> collection;

  public RestaurantRepository(MongoTemplate mongoTemplate) {
    this.collection = mongoTemplate.getCollection(COLLECTION);
  }

  public void insert(Document doc) {
    collection.insertOne(doc); // the driver adds the generated _id to the Document
  }

  public Optional<Document> findById(String tenantId, ObjectId id) {
    // tenantId is part of every lookup, so one tenant can never read another's documents.
    return Optional.ofNullable(
        collection.find(Filters.and(Filters.eq("_id", id), Filters.eq("tenantId", tenantId))).first());
  }

  public List<Document> search(String tenantId, RestaurantQuery query) {
    return collection
        .aggregate(RestaurantPipelines.search(tenantId, query))
        .into(new ArrayList<>());
  }

  public Set<String> distinctKeys(String tenantId, String field) {
    return collection
        .distinct(field, Filters.eq("tenantId", tenantId), String.class)
        .into(new HashSet<>());
  }

  /** Idempotent load: re-running the same file updates documents instead of duplicating them. */
  public BulkWriteResult bulkUpsert(String tenantId, List<Document> docs) {
    List<WriteModel<Document>> ops = new ArrayList<>(docs.size());
    for (Document doc : docs) {
      Document set = new Document(doc);
      Object createdAt = set.remove("createdAt");
      ops.add(
          new UpdateOneModel<>(
              Filters.and(
                  Filters.eq("tenantId", tenantId),
                  Filters.eq("externalId", doc.get("externalId"))),
              new Document("$set", set).append("$setOnInsert", new Document("createdAt", createdAt)),
              new UpdateOptions().upsert(true)));
    }
    return collection.bulkWrite(ops, new BulkWriteOptions().ordered(false));
  }

  public DeleteResult deleteAll() {
    return collection.deleteMany(new Document());
  }

  /**
   * Index design follows the ESR rule (Equality, Sort, Range). Every index starts with tenantId
   * because every query is tenant-scoped.
   */
  public void ensureIndexes() {
    // Nearby search: equality on tenant, then the 2dsphere key used by $geoNear.
    collection.createIndex(
        Indexes.compoundIndex(Indexes.ascending("tenantId"), Indexes.geo2dsphere("location")),
        new IndexOptions().name("tenant_location_2dsphere"));

    // "Top restaurants in a city": equality on tenant+city, sorted by rating, votes, _id
    // exactly matching the pipeline's sort, so no in-memory sort is needed.
    collection.createIndex(
        Indexes.compoundIndex(
            Indexes.ascending("tenantId", "cityKey"),
            Indexes.descending("rating.average", "rating.votes"),
            Indexes.ascending("_id")),
        new IndexOptions().name("tenant_city_rating"));

    // Same idea for cuisine filters (multikey index on the cuisineKeys array).
    collection.createIndex(
        Indexes.compoundIndex(
            Indexes.ascending("tenantId", "cuisineKeys"),
            Indexes.descending("rating.average", "rating.votes"),
            Indexes.ascending("_id")),
        new IndexOptions().name("tenant_cuisine_rating"));

    // Natural key from the source system; partial so API-created restaurants (no externalId) are fine.
    collection.createIndex(
        Indexes.ascending("tenantId", "externalId"),
        new IndexOptions()
            .name("tenant_external_unique")
            .unique(true)
            .partialFilterExpression(Filters.exists("externalId")));
  }
}
