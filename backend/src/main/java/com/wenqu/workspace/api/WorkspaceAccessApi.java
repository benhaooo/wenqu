package com.wenqu.workspace.api;

public interface WorkspaceAccessApi {

    /**
     * 校验用户在空间中的角色，返回用户的实际角色。
     *
     * @throws com.wenqu.common.error.BusinessException 不是成员或空间不存在时为 WORKSPACE_NOT_FOUND，角色不足时为
     *     WORKSPACE_PERMISSION_DENIED
     */
    WorkspaceRole require(long workspaceId, long userId, WorkspaceRole minimum);
}
