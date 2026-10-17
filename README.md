# BiteExpress Restaurant Discovery API (MongoDB take-home)

Spring Boot 3 / Java 21 REST API for restaurant discovery, backed by MongoDB.
Built as a PoC/PoV for BiteExpress (via Apex Tech Partners): reliable, fast reads at peak traffic now,
and a data model and tenancy scheme that can be reused for other food-delivery businesses later.

## Run it (3 commands)

```bash
# 1. (optional) put the full Kaggle file at data/zomato.csv. Without it, a small synthetic
#    sample (data/sample_restaurants.csv) is loaded automatically.
# 2.
docker compose up --build
# 3. open http://localhost:8080/swagger-ui.html
```

Local dev without Docker for the API: `docker compose up mongo -d && mvn spring-boot:run`.
Tests: `mvn test` (the API tests start MongoDB via Testcontainers, so Docker must be running).

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/restaurants` | Create a restaurant, returns 201 + `Location` + the created entity |
| GET | `/v1/restaurants/{id}` | Full details of one restaurant |
| GET | `/v1/restaurants` | Listing with filters + sorting + pagination |
| GET | `/v1/restaurants/search?q=...` | Everything above, plus a natural-language sentence |

Listing/search parameters: `name`, `city`, `cuisines` (comma-separated), `minRating`, `maxCost`,
`minVotes`, `lat`+`lng` (+ `radiusKm`), `sort` = `distance|rating|name|cost`, `order` = `asc|desc`,
`page`, `size` (max 100). Optional header `X-Tenant-Id` (default `biteexpress`).

```bash
curl "localhost:8080/v1/restaurants?city=Gurgaon&cuisines=South%20Indian&sort=rating"
curl "localhost:8080/v1/restaurants?lat=28.495&lng=77.089&radiusKm=3"           # nearest first, distanceKm returned
curl "localhost:8080/v1/restaurants/search?q=Best%20restaurants%20in%20Gurgaon%20with%20South%20Indian%20Food"
curl "localhost:8080/v1/restaurants/search?q=Restaurants%20in%20Gurgaon%20under%20Rs.%201000%20for%20two"
curl "localhost:8080/v1/restaurants/search?q=Nearest%20place%20that%20serves%20Vada%20Pav&lat=28.495&lng=77.089"
```

The search response includes `interpretedAs` (what the parser understood), so a client can show it.

## Data model (one collection: `restaurants`)

```json
{
  "_id": "ObjectId", "tenantId": "biteexpress", "externalId": 6317637,
  "name": "...", "address": "...", "locality": "...", "localityVerbose": "...",
  "city": "Gurgaon", "cityKey": "gurgaon", "countryCode": 1, "country": "India",
  "location": { "type": "Point", "coordinates": [77.089, 28.495] },
  "cuisines": ["South Indian"], "cuisineKeys": ["south indian"],
  "pricing":  { "averageCostForTwo": 400, "currency": "Indian Rupees(Rs.)", "priceRange": 1 },
  "services": { "tableBooking": false, "onlineDelivery": true, "deliveringNow": false, "switchToOrderMenu": false },
  "rating":   { "average": 4.3, "color": "Green", "text": "Very Good", "votes": 520 },
  "schemaVersion": 1, "createdAt": "...", "updatedAt": "..."
}
```

Decisions:
- **One document per restaurant, embedded sub-documents** (`pricing`, `services`, `rating`): the app reads them
  together, so a single read serves the detail page and there are no joins.
- **GeoJSON `location`** (`[lng, lat]`) with a `2dsphere` index: required for `$geoNear` and real distances.
- **Lower-cased `cityKey` / `cuisineKeys`**: exact, index-friendly, case-insensitive filtering (alternative: a
  case-insensitive collation index). `cityKey` also maps aliases (Gurugram -> gurgaon).
- **`tenantId` on every document and in every index**: row-level multi-tenancy so the same platform can serve
  several businesses. Every repository query is tenant-scoped.
- **`externalId`** keeps the source system's ID, so migration/sync can upsert idempotently (unique partial index).
- **`schemaVersion`**: lets future releases evolve documents with lazy or background migration.

## Indexes (created at startup, see `RestaurantRepository.ensureIndexes`)

| Index | Serves |
|---|---|
| `{tenantId, location: 2dsphere}` | nearby search / distance |
| `{tenantId, cityKey, rating.average -1, rating.votes -1, _id}` | "top restaurants in a city", sort satisfied by the index |
| `{tenantId, cuisineKeys, rating.average -1, rating.votes -1, _id}` | cuisine filter + rating sort |
| `{tenantId, externalId}` unique, partial | idempotent loads, natural key |

Check them yourself:
```js
// mongosh
use biteexpress
db.restaurants.find({tenantId:"biteexpress", cityKey:"gurgaon"})
  .sort({"rating.average":-1,"rating.votes":-1,_id:1}).limit(20).explain("executionStats")
// expect IXSCAN on tenant_city_rating, totalKeysExamined ~ nReturned, no SORT stage
```

## Design choices worth discussing

- **Aggregation with `$geoNear`** rather than `find()`: it is the only way to get distance back per document.
  `$geoNear` must be the first stage, so with no location the pipeline starts with `$match` (one code path).
- **Natural-language search is rule-based and deterministic** (no LLM at query time): predictable latency and
  cost, no data leaving the platform, testable. It recognises price ceilings, ratings, city (incl. aliases),
  cuisines, "best/top" and "nearest" intent, and treats leftover words as free text.
  "Best" requires >= 20 votes so a single 5-star review does not outrank a restaurant with 500 reviews.
- **Free-text uses regex, not `$text`**, because `$text` and `$geoNear` cannot share a pipeline. At production
  scale this is where **Atlas Search** (text + geo + facets + autocomplete in one stage) replaces it, and Atlas
  Vector Search could power semantic "something spicy and cheap" queries.
- **Pagination**: offset pages with a deterministic `_id` tie-breaker and a `hasMore` flag (no expensive total
  count); deep offsets are capped. Keyset pagination is the next step for infinite scroll.
- **Idempotent CSV loading** via bulk upsert (unordered); invalid `0,0` coordinates load as "no location";
  missing/garbled rows are skipped and counted in the startup log.
- **Errors** are RFC 9457 `application/problem+json`; datastore outages return 503 instead of a stack trace.

## Roadmap: from PoC to BiteExpress production (FDA view)

1. **Coexistence**: keep the on-prem relational DBs as system of record. Stream changes with log-based CDC
   (Debezium + Kafka, or MongoDB Relational Migrator) into this collection via idempotent upserts keyed on
   `externalId`; reconcile with counts/checksums; shadow-read, then cut over per city/tenant with a reverse-sync
   rollback path.
2. **Peak-hour reliability**: replica set (3 nodes, `w:majority`), read from secondaries for discovery,
   caching for hot lists, connection-pool and timeout tuning, then shard on `{tenantId, cityKey}`-style key
   when one replica set is not enough.
3. **Cloud + international**: Atlas multi-region, zone sharding to keep each country's data in region,
   encryption at rest/in transit, field-level encryption for PII, audit logging.
4. **Operations**: metrics/alerts on slow queries (profiler), index audits, tested backup/restore.

## Testing

- `NaturalLanguageQueryParserTest`, `RestaurantPipelinesTest`: fast unit tests, no database.
- `RestaurantApiTest`: end-to-end API tests against a real MongoDB 7 (Testcontainers): CRUD, validation,
  tenant isolation, filters, geo distance/radius, pagination, natural-language queries.

## Known limitations (deliberately out of scope for a 3-5 hour MVP)

Name sort uses default binary collation (uppercase before lowercase); no authentication; vocabulary cache is
per instance with a 5-minute TTL; no update/delete endpoints; menu/inventory/orders are not modelled.

## Tools and AI-assistance disclosure

> **Edit this section to be accurate before you submit.**
> I used an LLM assistant (Claude) for [scaffolding / code review / brainstorming ... describe exactly what].
> I reviewed, ran and can explain every part of the code. Other resources: Spring Boot, MongoDB Java driver
> and MongoDB manual documentation, Testcontainers docs.
