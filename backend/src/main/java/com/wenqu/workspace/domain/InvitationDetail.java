package com.wenqu.workspace.domain;

/** 管理员查看的邀请及创建人名称。 */
public record InvitationDetail(Invitation invitation, String createdByName) {}
