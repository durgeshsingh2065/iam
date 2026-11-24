package com.iam.assistant.server.exception;

import com.iam.assistant.server.model.mcp.McpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<McpResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(McpResponse.error(null, McpResponse.McpError.INVALID_PARAMS, msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<McpResponse> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(McpResponse.error(null, McpResponse.McpError.INVALID_PARAMS, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<McpResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(McpResponse.error(null,
                        McpResponse.McpError.INTERNAL_ERROR,
                        "Internal server error: " + ex.getMessage()));
    }
}
