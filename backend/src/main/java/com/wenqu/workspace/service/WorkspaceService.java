package com.wenqu.workspace.service;

import com.wenqu.auth.api.UserApi;
import com.wenqu.auth.api.UserSummary;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.workspace.api.WorkspaceRole;
import com.wenqu.workspace.domain.MemberDetail;
import com.wenqu.workspace.domain.MemberWorkspace;
import com.wenqu.workspace.domain.Membership;
import com.wenqu.workspace.domain.Workspace;
import com.wenqu.workspace.repository.MemberRepository;
import com.wenqu.workspace.repository.WorkspaceRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 空间与成员。调用方已通过 {@code @RequireWorkspaceRole} 完成角色校验。 */
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final MemberRepository memberRepository;
    private final UserApi userApi;
    private final Clock clock;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository, MemberRepository memberRepository, UserApi userApi, Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userApi = userApi;
        this.clock = clock;
    }

    /** 创建者成为管理员。 */
    @Transactional
    public MemberWorkspace create(String name, long userId) {
        Workspace workspace = workspaceRepository.insert(name.strip(), userId);
        memberRepository.insertIfAbsent(workspace.id(), userId, WorkspaceRole.ADMIN);
        return new MemberWorkspace(workspace, WorkspaceRole.ADMIN);
    }

    public List<MemberWorkspace> listForMember(long userId) {
        return workspaceRepository.findAllForMember(userId);
    }

    public MemberWorkspace get(long workspaceId, long userId) {
        return workspaceRepository
                .findForMember(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    @Transactional
    public MemberWorkspace rename(long workspaceId, String name, long userId) {
        workspaceRepository.rename(workspaceId, name.strip(), clock.instant());
        return get(workspaceId, userId);
    }

    public List<MemberDetail> listMembers(long workspaceId) {
        List<Membership> memberships = memberRepository.findByWorkspace(workspaceId);
        Map<Long, UserSummary> users =
                userApi.findByIds(memberships.stream().map(Membership::userId).toList());
        return memberships.stream()
                .filter(membership -> users.containsKey(membership.userId()))
                .map(membership -> {
                    UserSummary user = users.get(membership.userId());
                    return new MemberDetail(
                            user.id(), user.email(), user.displayName(), membership.role(), membership.joinedAt());
                })
                .toList();
    }
}
