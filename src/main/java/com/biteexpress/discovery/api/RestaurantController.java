package com.biteexpress.discovery.api;

import com.biteexpress.discovery.domain.BadRequestException;
import com.biteexpress.discovery.domain.RestaurantQuery;
import com.biteexpress.discovery.dto.CreateRestaurantRequest;
import com.biteexpress.discovery.dto.PageResponse;
import com.biteexpress.discovery.dto.RestaurantResponse;
import com.biteexpress.discovery.dto.RestaurantSummary;
import com.biteexpress.discovery.service.RestaurantService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.regex.Pattern;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/restaurants")
public class RestaurantController {
  private static final Pattern TENANT = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
  private static final String TENANT_HEADER = "X-Tenant-Id";

  private final RestaurantService service;
  private final String defaultTenant;

  public RestaurantController(
      RestaurantService service, @Value("${app.default-tenant}") String defaultTenant) {
    this.service = service;
    this.defaultTenant = defaultTenant;
  }

  @PostMapping
  public ResponseEntity<RestaurantResponse> create(
      @RequestHeader(name = TENANT_HEADER, required = false) String tenant,
      @Valid @RequestBody CreateRestaurantRequest request) {
    RestaurantResponse created = service.create(tenantOf(tenant), request);
    return ResponseEntity.created(URI.create("/v1/restaurants/" + created.id())).body(created);
  }

  @GetMapping("/{id}")
  public RestaurantResponse get(
      @RequestHeader(name = TENANT_HEADER, required = false) String tenant,
      @PathVariable String id) {
    return service.get(tenantOf(tenant), id);
  }

  @GetMapping
  public PageResponse<RestaurantSummary> list(
      @RequestHeader(name = TENANT_HEADER, required = false) String tenant,
      @ParameterObject @ModelAttribute RestaurantQuery query) {
    return service.list(tenantOf(tenant), query);
  }

  @GetMapping("/search")
  public PageResponse<RestaurantSummary> search(
      @RequestHeader(name = TENANT_HEADER, required = false) String tenant,
      @RequestParam(name = "q", required = false) String q,
      @ParameterObject @ModelAttribute RestaurantQuery query) {
    return service.search(tenantOf(tenant), q, query);
  }

  private String tenantOf(String header) {
    if (header == null || header.isBlank()) {
      return defaultTenant;
    }
    if (!TENANT.matcher(header).matches()) {
      throw new BadRequestException(TENANT_HEADER + " must match " + TENANT.pattern());
    }
    return header;
  }
}
