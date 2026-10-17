package com.biteexpress.discovery.ingest;

import com.biteexpress.discovery.persistence.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Creates indexes explicitly at startup (idempotent) instead of relying on framework magic. */
@Component
@Order(1)
public class MongoIndexInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);
  private final RestaurantRepository repository;

  public MongoIndexInitializer(RestaurantRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(ApplicationArguments args) {
    repository.ensureIndexes();
    log.info("MongoDB indexes ensured");
  }
}
