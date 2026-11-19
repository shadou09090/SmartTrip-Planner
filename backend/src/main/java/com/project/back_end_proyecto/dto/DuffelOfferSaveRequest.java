package com.project.back_end_proyecto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DuffelOfferSaveRequest(@NotBlank @Size(max = 100) String offerId) { }
