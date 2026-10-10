package com.wenqu.workspace.service;

import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.workspace.api.WorkspaceAccessApi;
import com.wenqu.workspace.api.WorkspaceRole;
import com.wenqu.workspace.repository.MemberRepository;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceAccessService implements WorkspaceAccessApi {

    private final MemberRepository memberRepository;

    public WorkspaceAccessService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public WorkspaceRole require(long workspaceId, long userId, WorkspaceRole minimum) {
        // 不是成员与空间不存在返回同一个错误，不暴露空间是否存在
        WorkspaceRole role = memberRepository
                .findRole(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        if (!role.includes(minimum)) {
            throw new BusinessException(ErrorCode.WORKSPACE_PERMISSION_DENIED);
        }
        return role;
    }
}
