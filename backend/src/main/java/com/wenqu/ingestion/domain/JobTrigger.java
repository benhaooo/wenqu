package com.wenqu.ingestion.domain;

public enum JobTrigger {
    /** 导入文件：先解析写入正文，再建立索引。 */
    IMPORT,
    /** 编辑器保存正文后重新索引。 */
    EDIT,
    /** 管理员手动重新索引全部文档（如更换 Embedding 模型后）。 */
    REINDEX
}
