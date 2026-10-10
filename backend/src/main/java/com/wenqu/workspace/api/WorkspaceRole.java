package com.wenqu.workspace.api;

/** 空间级角色，按权限从低到高排列。 */
public enum WorkspaceRole {
    VIEWER,
    EDITOR,
    ADMIN;

    /** 当前角色是否具备 required 角色的全部权限。 */
    public boolean includes(WorkspaceRole required) {
        return compareTo(required) >= 0;
    }
}
