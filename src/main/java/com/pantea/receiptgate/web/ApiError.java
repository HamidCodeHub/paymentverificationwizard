package com.pantea.receiptgate.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Every non-2xx response uses this shape, so the WordPress proxy has exactly one thing to parse.
 * Messages are written to be safe to show a customer verbatim.
 */
@Schema(name = "ApiError", description = "Error payload returned by every non-2xx response.")
public record ApiError(

        @Schema(description = "Customer-safe message. No stack traces or internal detail.",
                example = "Please upload the receipt as a JPG or PNG screenshot")
        String error
) {
    public static ApiError of(String message) {
        return new ApiError(message);
    }
}
