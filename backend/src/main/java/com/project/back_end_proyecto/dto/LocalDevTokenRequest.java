package com.project.back_end_proyecto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocalDevTokenRequest(
        @NotBlank @Size(min = 32, max = 512) String bootstrapToken
) { }
