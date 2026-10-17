package com.biteexpress.discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Offset page without a total count: counting a large filtered result on every request is costly,
 * and mobile apps only need to know whether another page exists.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(
    List<T> items, int page, int size, boolean hasMore, Object interpretedAs) {}
