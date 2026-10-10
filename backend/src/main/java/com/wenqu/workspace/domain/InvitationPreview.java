package com.wenqu.workspace.domain;

import com.wenqu.workspace.api.WorkspaceRole;
import java.time.Instant;

/** 被邀请人打开链接时看到的信息。 */
public record InvitationPreview(
        long workspaceId,
        String workspaceName,
        WorkspaceRole role,
        String inviterName,
        Instant expiresAt,
        boolean alreadyMember) {}
