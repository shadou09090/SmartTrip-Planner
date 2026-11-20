package com.project.back_end_proyecto.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
    public LoginResponse(Long id, String name, String email) {
        this(null, "Bearer", 0L, new UserSummary(id, name, email));
    }

    public Long id() {
        return user != null ? user.id() : null;
    }

    public String name() {
        return user != null ? user.name() : null;
    }

    public String email() {
        return user != null ? user.email() : null;
    }
}
