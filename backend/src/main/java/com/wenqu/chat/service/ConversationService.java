package com.wenqu.chat.service;

import com.wenqu.chat.domain.Conversation;
import com.wenqu.chat.domain.Message;
import com.wenqu.chat.domain.Rating;
import com.wenqu.chat.repository.ConversationRepository;
import com.wenqu.chat.repository.MessageRepository;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会话与反馈。会话只属于创建者，其他成员访问返回不存在。 */
@Service
public class ConversationService {

    static final int LIST_LIMIT = 50;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final Clock clock;

    public ConversationService(
            ConversationRepository conversationRepository, MessageRepository messageRepository, Clock clock) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.clock = clock;
    }

    public Conversation create(long workspaceId, long userId) {
        return conversationRepository.insert(workspaceId, userId, Conversation.DEFAULT_TITLE);
    }

    public List<Conversation> list(long workspaceId, long userId) {
        return conversationRepository.findByUser(workspaceId, userId, LIST_LIMIT);
    }

    public Conversation get(long workspaceId, long userId, long conversationId) {
        return conversationRepository
                .find(workspaceId, userId, conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    public List<Message> messages(long workspaceId, long userId, long conversationId) {
        get(workspaceId, userId, conversationId);
        return messageRepository.findByConversation(conversationId);
    }

    @Transactional
    public void delete(long workspaceId, long userId, long conversationId) {
        get(workspaceId, userId, conversationId);
        conversationRepository.delete(conversationId);
    }

    /** rating 为 null 时取消反馈。只能评价自己会话中的回答。 */
    @Transactional
    public void rate(long workspaceId, long userId, long messageId, Rating rating) {
        if (!messageRepository.isOwnAnswer(workspaceId, userId, messageId)) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        if (rating == null) {
            messageRepository.deleteFeedback(messageId);
        } else {
            messageRepository.saveFeedback(messageId, rating, clock.instant());
        }
    }
}
