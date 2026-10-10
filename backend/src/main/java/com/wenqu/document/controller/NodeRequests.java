package com.wenqu.document.controller;

import com.wenqu.document.domain.NodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class NodeRequests {

    private NodeRequests() {}

    record CreateNodeRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "null 表示根目录")
            Long parentId,

            @NotNull(message = "类型不能为空") NodeType type,

            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题最多 200 个字符")
            String title) {}

    record RenameNodeRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题最多 200 个字符")
            String title) {}

    /**
     * @param parentId 目标文件夹，null 表示根目录
     * @param afterNodeId 放在该同级节点之后，null 表示放在最前
     */
    record MoveNodeRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Long parentId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Long afterNodeId) {}
}
