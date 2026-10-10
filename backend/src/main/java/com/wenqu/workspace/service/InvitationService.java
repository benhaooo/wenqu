package com.wenqu.workspace.service;

import com.wenqu.auth.api.UserApi;
import com.wenqu.auth.api.UserSummary;
import com.wenqu.common.crypto.SecureTokens;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.workspace.api.WorkspaceRole;
import com.wenqu.workspace.domain.CreatedInvitation;
import com.wenqu.workspace.domain.Invitation;
import com.wenqu.workspace.domain.InvitationDetail;
import com.wenqu.workspace.domain.InvitationPreview;
import com.wenqu.workspace.domain.MemberWorkspace;
import com.wenqu.workspace.domain.Workspace;
import com.wenqu.workspace.repository.InvitationRepository;
import com.wenqu.workspace.repository.MemberRepository;
import com.wenqu.workspace.repository.WorkspaceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 邀请链接：可被多人使用，直到过期或被撤销。 */
@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserApi userApi;
    private final Clock clock;

    public InvitationService(
            InvitationRepository invitationRepository,
            MemberRepository memberRepository,
            WorkspaceRepository workspaceRepository,
            UserApi userApi,
            Clock clock) {
        this.invitationRepository = invitationRepository;
        this.memberRepository = memberRepository;
        this.workspaceRepository = workspaceRepository;
        this.userApi = userApi;
        this.clock = clock;
    }

    @Transactional
    public CreatedInvitation create(long workspaceId, WorkspaceRole role, Duration validity, long createdBy) {
        String token = SecureTokens.generate();
        Invitation invitation = invitationRepository.insert(
                workspaceId,
                SecureTokens.hash(token),
                role,
                createdBy,
                clock.instant().plus(validity));
        return new CreatedInvitation(invitation, token);
    }

    public List<InvitationDetail> listActive(long workspaceId) {
        List<Invitation> invitations = invitationRepository.findActiveByWorkspace(workspaceId, clock.instant());
        Map<Long, UserSummary> creators = userApi.findByIds(
                invitations.stream().map(Invitation::createdBy).distinct().toList());
        return invitations.stream()
                .map(invitation -> new InvitationDetail(invitation, displayName(creators, invitation.createdBy())))
                .toList();
    }

    @Transactional
    public void revoke(long workspaceId, long invitationId) {
        if (!invitationRepository.revoke(workspaceId, invitationId, clock.instant())) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_FOUND);
        }
    }

    public InvitationPreview preview(String token, long userId) {
        Invitation invitation = findUsable(token);
        Workspace workspace = workspaceRepository
                .findById(invitation.workspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        Map<Long, UserSummary> inviter = userApi.findByIds(List.of(invitation.createdBy()));
        boolean alreadyMember =
                memberRepository.findRole(invitation.workspaceId(), userId).isPresent();
        return new InvitationPreview(
                workspace.id(),
                workspace.name(),
                invitation.role(),
                displayName(inviter, invitation.createdBy()),
                invitation.expiresAt(),
                alreadyMember);
    }

    /** 加入空间并返回加入后的空间。已是成员时保留原角色。 */
    @Transactional
    public MemberWorkspace accept(String token, long userId) {
        Invitation invitation = findUsable(token);
        memberRepository.insertIfAbsent(invitation.workspaceId(), userId, invitation.role());
        return workspaceRepository
                .findForMember(invitation.workspaceId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
    }

    private Invitation findUsable(String token) {
        Invitation invitation = invitationRepository
                .findByTokenHash(SecureTokens.hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        Instant now = clock.instant();
        return switch (invitation.statusAt(now)) {
            case ACTIVE -> invitation;
            case EXPIRED -> throw new BusinessException(ErrorCode.INVITATION_EXPIRED);
            case REVOKED -> throw new BusinessException(ErrorCode.INVITATION_REVOKED);
        };
    }

    private static String displayName(Map<Long, UserSummary> users, long userId) {
        UserSummary user = users.get(userId);
        return user != null ? user.displayName() : "";
    }
}
