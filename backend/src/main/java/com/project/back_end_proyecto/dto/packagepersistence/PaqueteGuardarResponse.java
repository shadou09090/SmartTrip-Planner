package com.project.back_end_proyecto.dto.packagepersistence;

import java.time.LocalDateTime;

/** Returned only by a future transaction-backed implementation after re-reading persisted rows. */
public record PaqueteGuardarResponse(
        Long viajeId,
        long revision,
        String idempotencyKey,
        LocalDateTime guardadoEn,
        boolean persistido,
        PaqueteGuardarRequest paquete) { }
