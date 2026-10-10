package com.wenqu.auth.domain;

public record User(long id, String email, String passwordHash, String displayName) {}
