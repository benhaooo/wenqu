package com.wenqu.chat.domain;

import java.util.List;

/** 流式回答中的事件，依次为 Retrieval、若干 Token、Citations、（失败时）Failure、Done。 */
public sealed interface AnswerEvent {

    /** 检索命中的文档（去重，按相关度），以及提供给模型的全部资料（marker 即资料编号），前端据此在生成过程中显示角标。 */
    record Retrieval(List<SourceDocument> documents, List<Citation> sources) implements AnswerEvent {}

    record Token(String text) implements AnswerEvent {}

    /** 回答中实际引用的资料。 */
    record Citations(List<Citation> citations) implements AnswerEvent {}

    record Failure(String message) implements AnswerEvent {}

    /** 回答已保存。 */
    record Done(long messageId, MessageStatus status) implements AnswerEvent {}

    record SourceDocument(long documentId, String title) {}
}
