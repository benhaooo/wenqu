package com.wenqu.workspace.domain;

import com.wenqu.workspace.api.WorkspaceRole;
import java.time.Instant;

public record Membership(long workspaceId, long userId, WorkspaceRole role, Instant joinedAt) {}
