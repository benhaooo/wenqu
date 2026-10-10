package com.wenqu.workspace.controller;

import com.wenqu.workspace.api.WorkspaceRole;
import com.wenqu.workspace.domain.CreatedInvitation;
import com.wenqu.workspace.domain.InvitationDetail;
import com.wenqu.workspace.domain.InvitationPreview;
import com.wenqu.workspace.domain.MemberDetail;
import com.wenqu.workspace.domain.MemberWorkspace;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

final class WorkspaceResponses {

    private WorkspaceResponses() {}

    record WorkspaceResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前用户在空间中的角色")
            WorkspaceRole myRole) {

        static WorkspaceResponse from(MemberWorkspace memberWorkspace) {
            return new WorkspaceResponse(
                    memberWorkspace.workspace().id(),
                    memberWorkspace.workspace().name(),
                    memberWorkspace.role());
        }
    }

    record MemberResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long userId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String email,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String displayName,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            WorkspaceRole role,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant joinedAt) {

        static MemberResponse from(MemberDetail member) {
            return new MemberResponse(
                    member.userId(), member.email(), member.displayName(), member.role(), member.joinedAt());
        }
    }

    record InvitationResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            WorkspaceRole role,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String createdByName,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant expiresAt) {

        static InvitationResponse from(InvitationDetail detail) {
            var invitation = detail.invitation();
            return new InvitationResponse(
                    invitation.id(),
                    invitation.role(),
                    detail.createdByName(),
                    invitation.createdAt(),
                    invitation.expiresAt());
        }
    }

    record CreatedInvitationResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "邀请令牌，只在创建时返回，链接为 /invite/{token}")
            String token,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            WorkspaceRole role,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant expiresAt) {

        static CreatedInvitationResponse from(CreatedInvitation created) {
            var invitation = created.invitation();
            return new CreatedInvitationResponse(
                    invitation.id(), created.token(), invitation.role(), invitation.expiresAt());
        }
    }

    record InvitationPreviewResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long workspaceId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String workspaceName,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            WorkspaceRole role,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String inviterName,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant expiresAt,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean alreadyMember) {

        static InvitationPreviewResponse from(InvitationPreview preview) {
            return new InvitationPreviewResponse(
                    preview.workspaceId(),
                    preview.workspaceName(),
                    preview.role(),
                    preview.inviterName(),
                    preview.expiresAt(),
                    preview.alreadyMember());
        }
    }
}
