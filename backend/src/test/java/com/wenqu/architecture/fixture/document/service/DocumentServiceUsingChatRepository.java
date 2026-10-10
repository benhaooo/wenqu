package com.wenqu.architecture.fixture.document.service;

import com.wenqu.architecture.fixture.chat.repository.ChatMessageRepository;

public class DocumentServiceUsingChatRepository {
    void run(ChatMessageRepository repository) {
        repository.save();
    }
}
