package com.wenqu.workspace.controller;

import com.wenqu.workspace.api.WorkspaceRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class WorkspaceRequests {

    private WorkspaceRequests() {}

    record WorkspaceNameRequest(
            @NotBlank(message = "空间名称不能为空") @Size(max = 50, message = "空间名称最多 50 个字符")
            String name) {}

    record CreateInvitationRequest(
            @NotNull(message = "角色不能为空") WorkspaceRole role,

            @NotNull(message = "有效天数不能为空")
            @Min(value = 1, message = "有效期为 1～30 天")
            @Max(value = 30, message = "有效期为 1～30 天")
            Integer validDays) {}
}
