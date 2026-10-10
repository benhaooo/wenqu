package com.wenqu.infra.system.domain;

import java.util.List;

/** 系统整体状态及每个依赖组件（数据库等）的连接状态。 */
public record SystemHealth(HealthStatus status, List<Component> components) {

    public record Component(String name, HealthStatus status) {}
}
