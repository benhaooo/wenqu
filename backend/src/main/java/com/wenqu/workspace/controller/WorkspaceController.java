package com.wenqu.workspace.controller;

import com.wenqu.auth.api.CurrentUser;
import com.wenqu.common.web.ApiResult;
import com.wenqu.workspace.api.RequireWorkspaceRole;
import com.wenqu.workspace.api.WorkspaceRole;
import com.wenqu.workspace.controller.WorkspaceRequests.CreateInvitationRequest;
import com.wenqu.workspace.controller.WorkspaceRequests.WorkspaceNameRequest;
import com.wenqu.workspace.controller.WorkspaceResponses.CreatedInvitationResponse;
import com.wenqu.workspace.controller.WorkspaceResponses.InvitationResponse;
import com.wenqu.workspace.controller.WorkspaceResponses.MemberResponse;
import com.wenqu.workspace.controller.WorkspaceResponses.WorkspaceResponse;
import com.wenqu.workspace.service.InvitationService;
import com.wenqu.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "workspace", description = "团队空间、成员与邀请")
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final InvitationService invitationService;

    public WorkspaceController(WorkspaceService workspaceService, InvitationService invitationService) {
        this.workspaceService = workspaceService;
        this.invitationService = invitationService;
    }

    @Operation(operationId = "createWorkspace", summary = "创建空间，创建者成为管理员")
    @PostMapping
    public ApiResult<WorkspaceResponse> create(
            @Valid @RequestBody WorkspaceNameRequest request, CurrentUser currentUser) {
        return ApiResult.ok(WorkspaceResponse.from(workspaceService.create(request.name(), currentUser.id())));
    }

    @Operation(operationId = "listMyWorkspaces", summary = "我加入的空间")
    @GetMapping
    public ApiResult<List<WorkspaceResponse>> listMine(CurrentUser currentUser) {
        return ApiResult.ok(workspaceService.listForMember(currentUser.id()).stream()
                .map(WorkspaceResponse::from)
                .toList());
    }

    @Operation(operationId = "getWorkspace", summary = "空间详情")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/{workspaceId}")
    public ApiResult<WorkspaceResponse> get(@PathVariable long workspaceId, CurrentUser currentUser) {
        return ApiResult.ok(WorkspaceResponse.from(workspaceService.get(workspaceId, currentUser.id())));
    }

    @Operation(operationId = "renameWorkspace", summary = "重命名空间")
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    @PatchMapping("/{workspaceId}")
    public ApiResult<WorkspaceResponse> rename(
            @PathVariable long workspaceId, @Valid @RequestBody WorkspaceNameRequest request, CurrentUser currentUser) {
        return ApiResult.ok(
                WorkspaceResponse.from(workspaceService.rename(workspaceId, request.name(), currentUser.id())));
    }

    @Operation(operationId = "listWorkspaceMembers", summary = "空间成员")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/{workspaceId}/members")
    public ApiResult<List<MemberResponse>> listMembers(@PathVariable long workspaceId) {
        return ApiResult.ok(workspaceService.listMembers(workspaceId).stream()
                .map(MemberResponse::from)
                .toList());
    }

    @Operation(operationId = "createInvitation", summary = "创建邀请链接")
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    @PostMapping("/{workspaceId}/invitations")
    public ApiResult<CreatedInvitationResponse> createInvitation(
            @PathVariable long workspaceId,
            @Valid @RequestBody CreateInvitationRequest request,
            CurrentUser currentUser) {
        return ApiResult.ok(CreatedInvitationResponse.from(invitationService.create(
                workspaceId, request.role(), Duration.ofDays(request.validDays()), currentUser.id())));
    }

    @Operation(operationId = "listInvitations", summary = "有效的邀请链接")
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    @GetMapping("/{workspaceId}/invitations")
    public ApiResult<List<InvitationResponse>> listInvitations(@PathVariable long workspaceId) {
        return ApiResult.ok(invitationService.listActive(workspaceId).stream()
                .map(InvitationResponse::from)
                .toList());
    }

    @Operation(operationId = "revokeInvitation", summary = "撤销邀请链接")
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    @DeleteMapping("/{workspaceId}/invitations/{invitationId}")
    public ApiResult<Void> revokeInvitation(@PathVariable long workspaceId, @PathVariable long invitationId) {
        invitationService.revoke(workspaceId, invitationId);
        return ApiResult.ok(null);
    }
}
