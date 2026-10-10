package com.wenqu.document.controller;

import com.wenqu.document.domain.Node;
import com.wenqu.document.domain.NodeType;
import com.wenqu.document.domain.SearchHit;
import com.wenqu.document.domain.TrashEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

final class NodeResponses {

    private NodeResponses() {}

    record NodeResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "null 表示位于根目录")
            Long parentId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            NodeType type,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "同级排序键，按字符串升序排列")
            String position,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant updatedAt) {

        static NodeResponse from(Node node) {
            return new NodeResponse(
                    node.id(), node.parentId(), node.type(), node.title(), node.position(), node.updatedAt());
        }
    }

    record TrashItemResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            NodeType type,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "同批删除的下级节点数量")
            int descendantCount,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String deletedByName,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant deletedAt,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "彻底删除的时间")
            Instant purgeAt) {

        static TrashItemResponse from(TrashEntry entry) {
            var item = entry.item();
            return new TrashItemResponse(
                    item.node().id(),
                    item.node().type(),
                    item.node().title(),
                    item.descendantCount(),
                    entry.deletedByName(),
                    item.deletedAt(),
                    entry.purgeAt());
        }
    }

    record SearchHitResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            NodeType type,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "正文中命中位置附近的片段")
            String snippet) {

        static SearchHitResponse from(SearchHit hit) {
            return new SearchHitResponse(
                    hit.node().id(), hit.node().type(), hit.node().title(), hit.snippet());
        }
    }
}
