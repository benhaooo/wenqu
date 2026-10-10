package com.wenqu.workspace.domain;

import com.wenqu.workspace.api.WorkspaceRole;
import java.time.Instant;

public record MemberDetail(long userId, String email, String displayName, WorkspaceRole role, Instant joinedAt) {}
