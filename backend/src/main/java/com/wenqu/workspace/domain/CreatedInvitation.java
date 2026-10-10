package com.wenqu.workspace.domain;

/** 新创建的邀请。token 为原文，只在创建时可见，数据库只保存其哈希。 */
public record CreatedInvitation(Invitation invitation, String token) {}
