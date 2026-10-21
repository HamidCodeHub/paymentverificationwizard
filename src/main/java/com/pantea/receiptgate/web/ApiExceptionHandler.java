package com.pantea.receiptgate.web;

import com.pantea.receiptgate.image.ImageLoader;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Hidden from the OpenAPI document: the responses are declared per-endpoint on the controller,
 * which produces a far more useful spec than a generic advice-level entry.
 */
@Hidden
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("That file is too large. Please upload a screenshot under 15 MB."));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> missing(Exception e) {
        return ResponseEntity.badRequest().body(ApiError.of("No image was uploaded (form field \"image\")."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiError.of(e.getReason() == null ? "Request failed" : e.getReason()));
    }

    /** Unreadable, oversized or malformed images. The message is already customer-safe. */
    @ExceptionHandler(ImageLoader.ImageRejectedException.class)
    public ResponseEntity<ApiError> rejected(ImageLoader.ImageRejectedException e) {
        log.info("Image rejected: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiError.of(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unexpected failure", e);
        return ResponseEntity.internalServerError()
                .body(ApiError.of("Something went wrong checking that image. Please try again."));
    }
}
