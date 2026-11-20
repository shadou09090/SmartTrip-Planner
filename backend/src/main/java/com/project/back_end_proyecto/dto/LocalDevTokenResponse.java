package com.project.back_end_proyecto.dto;

public record LocalDevTokenResponse(String accessToken, String tokenType, long expiresIn) { }
