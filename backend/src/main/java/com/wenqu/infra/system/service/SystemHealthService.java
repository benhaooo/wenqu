package com.wenqu.infra.system.service;

import com.wenqu.infra.system.domain.HealthStatus;
import com.wenqu.infra.system.domain.SystemHealth;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Service;

/** 汇总 Actuator 健康指标中各依赖组件的连接状态。 */
@Service
public class SystemHealthService {

    private final HealthEndpoint healthEndpoint;

    public SystemHealthService(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    public SystemHealth check() {
        HealthDescriptor health = healthEndpoint.health();
        var components = health instanceof CompositeHealthDescriptor composite
                ? composite.getComponents().entrySet().stream()
                        .map(entry -> new SystemHealth.Component(
                                entry.getKey(), toHealthStatus(entry.getValue().getStatus())))
                        .sorted(Comparator.comparing(SystemHealth.Component::name))
                        .toList()
                : List.<SystemHealth.Component>of();
        return new SystemHealth(toHealthStatus(health.getStatus()), components);
    }

    private static HealthStatus toHealthStatus(Status status) {
        return switch (status.getCode()) {
            case "UP" -> HealthStatus.UP;
            case "DOWN" -> HealthStatus.DOWN;
            case "OUT_OF_SERVICE" -> HealthStatus.OUT_OF_SERVICE;
            default -> HealthStatus.UNKNOWN;
        };
    }
}
