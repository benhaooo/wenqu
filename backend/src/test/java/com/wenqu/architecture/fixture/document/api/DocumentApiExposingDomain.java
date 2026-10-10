package com.wenqu.architecture.fixture.document.api;

import com.wenqu.architecture.fixture.chat.api.ChatApi;
import com.wenqu.architecture.fixture.document.domain.DocumentNode;

public interface DocumentApiExposingDomain {
    DocumentNode find(long id);

    ChatApi chat();
}
