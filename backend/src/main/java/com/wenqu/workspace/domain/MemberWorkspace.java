package com.wenqu.workspace.domain;

import com.wenqu.workspace.api.WorkspaceRole;

/** 用户视角的空间：空间本身及该用户在其中的角色。 */
public record MemberWorkspace(Workspace workspace, WorkspaceRole role) {}
