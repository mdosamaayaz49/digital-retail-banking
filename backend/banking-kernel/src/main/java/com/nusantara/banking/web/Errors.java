package com.nusantara.banking.web;

import java.net.URI;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  private static final Logger log = LoggerFactory.getLogger(Errors.class);

  @ExceptionHandler(BankingException.class)
  ResponseEntity<ProblemDetail> banking(BankingException e) {
    return problem(e.status, e.code, e.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    jakarta.validation.ConstraintViolationException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e) {
    return problem(400, "INVALID_REQUEST", "Check the request fields and try again.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return problem(409, "CONFLICT", "This operation conflicts with an existing record.");
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied(Exception e) {
    return problem(403, "ACCESS_DENIED", "Access denied.");
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ProblemDetail> missing(Exception e) {
    return problem(404, "NOT_FOUND", "Resource not found.");
  }

  @ExceptionHandler({
    org.springframework.web.bind.ServletRequestBindingException.class,
    org.springframework.web.HttpRequestMethodNotSupportedException.class,
    org.springframework.web.HttpMediaTypeNotSupportedException.class,
    org.springframework.web.HttpMediaTypeNotAcceptableException.class,
    org.springframework.web.ErrorResponseException.class
  })
  ResponseEntity<ProblemDetail> framework(Exception e) {
    int status =
        e instanceof org.springframework.web.ErrorResponse response
            ? response.getStatusCode().value()
            : 400;
    return problem(
        status,
        status >= 500 ? "INTERNAL_ERROR" : "INVALID_REQUEST",
        status >= 500
            ? "The request could not be completed."
            : "Check the request method, media type and required parameters.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    log.error("Unhandled banking request failure", e);
    return problem(500, "INTERNAL_ERROR", "The request could not be completed.");
  }

  private ResponseEntity<ProblemDetail> problem(int status, String code, String detail) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    p.setType(URI.create("urn:nusantara:error:" + code));
    p.setProperty("code", code);
    return ResponseEntity.status(status).body(p);
  }
}
