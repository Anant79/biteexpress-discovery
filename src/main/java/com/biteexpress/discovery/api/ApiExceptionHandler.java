package com.biteexpress.discovery.api;

import com.biteexpress.discovery.domain.BadRequestException;
import com.biteexpress.discovery.domain.NotFoundException;
import com.mongodb.MongoException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** One consistent error format (RFC 9457 problem+json) for every failure. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(BadRequestException.class)
  ProblemDetail badRequest(BadRequestException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(MongoException.class)
  ProblemDetail datastore(MongoException e) {
    log.error("MongoDB error", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "The datastore is temporarily unavailable");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    problem.setProperty("errors", errors);
    return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
  }
}
