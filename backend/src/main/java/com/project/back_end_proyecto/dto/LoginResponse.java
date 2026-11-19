package com.project.back_end_proyecto.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
}
