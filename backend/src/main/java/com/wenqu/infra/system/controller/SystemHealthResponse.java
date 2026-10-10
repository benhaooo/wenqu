package com.wenqu.infra.system.controller;

import com.wenqu.infra.system.domain.HealthStatus;
import com.wenqu.infra.system.domain.SystemHealth;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SystemHealthResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) HealthStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ComponentResponse> components) {

    public record ComponentResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "db")
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            HealthStatus status) {}

    static SystemHealthResponse from(SystemHealth health) {
        return new SystemHealthResponse(
                health.status(),
                health.components().stream()
                        .map(component -> new ComponentResponse(component.name(), component.status()))
                        .toList());
    }
}
