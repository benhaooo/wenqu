package com.wenqu.chat.domain;

public enum MessageStatus {
    COMPLETED,
    /** 用户停止生成，保留已生成的部分。 */
    STOPPED,
    /** 模型调用失败，保留已生成的部分。 */
    FAILED
}
