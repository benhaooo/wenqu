package com.wenqu.workspace.controller;

import com.wenqu.auth.api.CurrentUser;
import com.wenqu.common.web.ApiResult;
import com.wenqu.workspace.controller.WorkspaceResponses.InvitationPreviewResponse;
import com.wenqu.workspace.controller.WorkspaceResponses.WorkspaceResponse;
import com.wenqu.workspace.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 被邀请人使用的接口，需要登录。 */
@Tag(name = "workspace")
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @Operation(operationId = "previewInvitation", summary = "查看邀请信息")
    @GetMapping("/{token}")
    public ApiResult<InvitationPreviewResponse> preview(@PathVariable String token, CurrentUser currentUser) {
        return ApiResult.ok(InvitationPreviewResponse.from(invitationService.preview(token, currentUser.id())));
    }

    @Operation(operationId = "acceptInvitation", summary = "接受邀请，加入空间")
    @PostMapping("/{token}/accept")
    public ApiResult<WorkspaceResponse> accept(@PathVariable String token, CurrentUser currentUser) {
        return ApiResult.ok(WorkspaceResponse.from(invitationService.accept(token, currentUser.id())));
    }
}
