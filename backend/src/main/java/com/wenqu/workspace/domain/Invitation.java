package com.wenqu.workspace.domain;

import com.wenqu.workspace.api.WorkspaceRole;
import java.time.Instant;

public record Invitation(
        long id,
        long workspaceId,
        WorkspaceRole role,
        long createdBy,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt) {

    public InvitationStatus statusAt(Instant now) {
        if (revokedAt != null) {
            return InvitationStatus.REVOKED;
        }
        if (!now.isBefore(expiresAt)) {
            return InvitationStatus.EXPIRED;
        }
        return InvitationStatus.ACTIVE;
    }
}
