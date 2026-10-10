package com.wenqu.document.controller;

import com.wenqu.auth.api.CurrentUser;
import com.wenqu.common.web.ApiResult;
import com.wenqu.document.controller.NodeRequests.CreateNodeRequest;
import com.wenqu.document.controller.NodeRequests.MoveNodeRequest;
import com.wenqu.document.controller.NodeRequests.RenameNodeRequest;
import com.wenqu.document.controller.NodeResponses.NodeResponse;
import com.wenqu.document.controller.NodeResponses.SearchHitResponse;
import com.wenqu.document.controller.NodeResponses.TrashItemResponse;
import com.wenqu.document.service.NodeService;
import com.wenqu.workspace.api.RequireWorkspaceRole;
import com.wenqu.workspace.api.WorkspaceRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "document", description = "目录树、回收站与搜索")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Operation(operationId = "listNodes", summary = "空间内全部未删除节点（扁平列表）")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/nodes")
    public ApiResult<List<NodeResponse>> list(@PathVariable long workspaceId) {
        return ApiResult.ok(nodeService.listTree(workspaceId).stream()
                .map(NodeResponse::from)
                .toList());
    }

    @Operation(operationId = "createNode", summary = "新建文档或文件夹，排在目标文件夹最后")
    @RequireWorkspaceRole(WorkspaceRole.EDITOR)
    @PostMapping("/nodes")
    public ApiResult<NodeResponse> create(
            @PathVariable long workspaceId, @Valid @RequestBody CreateNodeRequest request, CurrentUser currentUser) {
        return ApiResult.ok(NodeResponse.from(nodeService.create(
                workspaceId, request.parentId(), request.type(), request.title(), currentUser.id())));
    }

    @Operation(operationId = "renameNode", summary = "重命名")
    @RequireWorkspaceRole(WorkspaceRole.EDITOR)
    @PatchMapping("/nodes/{nodeId}")
    public ApiResult<NodeResponse> rename(
            @PathVariable long workspaceId,
            @PathVariable long nodeId,
            @Valid @RequestBody RenameNodeRequest request,
            CurrentUser currentUser) {
        return ApiResult.ok(
                NodeResponse.from(nodeService.rename(workspaceId, nodeId, request.title(), currentUser.id())));
    }

    @Operation(operationId = "moveNode", summary = "移动到指定文件夹的指定位置")
    @RequireWorkspaceRole(WorkspaceRole.EDITOR)
    @PostMapping("/nodes/{nodeId}/move")
    public ApiResult<NodeResponse> move(
            @PathVariable long workspaceId,
            @PathVariable long nodeId,
            @RequestBody MoveNodeRequest request,
            CurrentUser currentUser) {
        return ApiResult.ok(NodeResponse.from(
                nodeService.move(workspaceId, nodeId, request.parentId(), request.afterNodeId(), currentUser.id())));
    }

    @Operation(operationId = "deleteNode", summary = "移入回收站，下级节点一并移入")
    @RequireWorkspaceRole(WorkspaceRole.EDITOR)
    @DeleteMapping("/nodes/{nodeId}")
    public ApiResult<Void> delete(@PathVariable long workspaceId, @PathVariable long nodeId, CurrentUser currentUser) {
        nodeService.delete(workspaceId, nodeId, currentUser.id());
        return ApiResult.ok(null);
    }

    @Operation(operationId = "listTrash", summary = "回收站：每次删除的顶层节点")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/trash")
    public ApiResult<List<TrashItemResponse>> listTrash(@PathVariable long workspaceId) {
        return ApiResult.ok(nodeService.listTrash(workspaceId).stream()
                .map(TrashItemResponse::from)
                .toList());
    }

    @Operation(operationId = "restoreNode", summary = "恢复回收站中的节点（同批删除的节点一并恢复）")
    @RequireWorkspaceRole(WorkspaceRole.EDITOR)
    @PostMapping("/trash/{nodeId}/restore")
    public ApiResult<Void> restore(@PathVariable long workspaceId, @PathVariable long nodeId, CurrentUser currentUser) {
        nodeService.restore(workspaceId, nodeId, currentUser.id());
        return ApiResult.ok(null);
    }

    @Operation(operationId = "searchNodes", summary = "按标题和正文搜索")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/search")
    public ApiResult<List<SearchHitResponse>> search(@PathVariable long workspaceId, @RequestParam String q) {
        return ApiResult.ok(nodeService.search(workspaceId, q).stream()
                .map(SearchHitResponse::from)
                .toList());
    }
}
