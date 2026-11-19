package com.project.back_end_proyecto.dto;

import java.util.List;

public record ApiError(String message, String code, List<FieldIssue> fields) {
    public ApiError {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public ApiError(String message) {
        this(message, "REQUEST_ERROR", List.of());
    }

    public record FieldIssue(String field, String message) { }
}
