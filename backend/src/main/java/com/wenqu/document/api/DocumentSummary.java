package com.wenqu.document.api;

import java.util.List;

/**
 * @param folderPath 从根目录到所在文件夹的文件夹标题，位于根目录时为空
 */
public record DocumentSummary(long id, String title, List<String> folderPath) {}
