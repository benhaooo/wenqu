package com.wenqu.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.wenqu.workspace.api.WorkspaceRole;
import org.junit.jupiter.api.Test;

class WorkspaceRoleTest {

    @Test
    void higherRolesIncludeLowerOnes() {
        assertThat(WorkspaceRole.ADMIN.includes(WorkspaceRole.EDITOR)).isTrue();
        assertThat(WorkspaceRole.EDITOR.includes(WorkspaceRole.EDITOR)).isTrue();
        assertThat(WorkspaceRole.EDITOR.includes(WorkspaceRole.ADMIN)).isFalse();
        assertThat(WorkspaceRole.VIEWER.includes(WorkspaceRole.EDITOR)).isFalse();
    }
}
