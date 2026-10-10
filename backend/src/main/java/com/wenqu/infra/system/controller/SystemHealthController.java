package com.wenqu.infra.system.controller;

import com.wenqu.common.web.ApiResult;
import com.wenqu.infra.system.service.SystemHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "system", description = "系统")
@RestController
@RequestMapping("/api/system")
public class SystemHealthController {

    private final SystemHealthService systemHealthService;

    public SystemHealthController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @Operation(operationId = "getSystemHealth", summary = "系统及依赖组件的连接状态")
    @GetMapping("/health")
    public ApiResult<SystemHealthResponse> health() {
        return ApiResult.ok(SystemHealthResponse.from(systemHealthService.check()));
    }
}
