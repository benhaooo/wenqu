package com.wenqu.document.domain;

import java.util.UUID;

public record Image(UUID id, long workspaceId, long nodeId, String objectKey, String contentType) {}
