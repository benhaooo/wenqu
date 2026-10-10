package com.wenqu.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.wenqu.workspace.api.WorkspaceRole;
import com.wenqu.workspace.domain.Invitation;
import com.wenqu.workspace.domain.InvitationStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvitationTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    @Test
    void activeUntilExpiryInstant() {
        Invitation invitation = invitation(NOW.plusSeconds(1), null);

        assertThat(invitation.statusAt(NOW)).isEqualTo(InvitationStatus.ACTIVE);
        assertThat(invitation.statusAt(NOW.plusSeconds(1))).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void revocationTakesPrecedenceOverExpiry() {
        assertThat(invitation(NOW.minusSeconds(1), NOW.minusSeconds(5)).statusAt(NOW))
                .isEqualTo(InvitationStatus.REVOKED);
    }

    private static Invitation invitation(Instant expiresAt, Instant revokedAt) {
        return new Invitation(1, 1, WorkspaceRole.EDITOR, 1, NOW.minusSeconds(60), expiresAt, revokedAt);
    }
}
