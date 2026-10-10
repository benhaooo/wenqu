package com.wenqu.document.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 编辑器保存的 ProseMirror 文档 JSON 的服务端处理：校验结构、净化危险内容、提取纯文本。
 *
 * <p>前端编辑器的 schema 已经过滤了粘贴内容中的脚本与未知标签；服务端再次校验，防止绕过编辑器直接调用接口写入危险内容。
 */
public final class ProseMirrorContent {

    private static final Set<String> BLOCK_TYPES = Set.of(
            "paragraph",
            "heading",
            "bulletList",
            "orderedList",
            "listItem",
            "taskList",
            "taskItem",
            "codeBlock",
            "blockquote",
            "callout",
            "horizontalRule",
            "image",
            "table",
            "tableRow",
            "tableHeader",
            "tableCell");

    private static final Set<String> MARK_TYPES = Set.of("bold", "italic", "strike", "code", "underline", "link");

    /** 每种节点允许保留的属性，其余属性丢弃。 */
    private static final Map<String, Set<String>> ALLOWED_ATTRS = Map.of(
            "heading", Set.of("level"),
            "codeBlock", Set.of("language"),
            "taskItem", Set.of("checked"),
            "image", Set.of("imageId", "alt"),
            "callout", Set.of("variant"),
            "orderedList", Set.of("start"),
            "tableCell", Set.of("colspan", "rowspan", "colwidth"),
            "tableHeader", Set.of("colspan", "rowspan", "colwidth"));

    private static final Set<String> CALLOUT_VARIANTS = Set.of("info", "warning", "danger");
    private static final Pattern SAFE_HREF = Pattern.compile("^(https?:|mailto:|/|#)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_LANGUAGE = Pattern.compile("^[a-z0-9+#-]{1,30}$");

    private ProseMirrorContent() {}

    /** 文档中引用的图片 id（格式不合法的忽略）。 */
    public static Set<UUID> imageIds(JsonNode document) {
        Set<UUID> ids = new HashSet<>();
        collectImageIds(document, ids);
        return ids;
    }

    /**
     * 返回净化后的副本：未知节点或 mark 类型视为非法；不安全的链接去掉链接；未上传到本空间的图片删除。
     *
     * @throws IllegalArgumentException 文档结构不合法
     */
    public static ObjectNode sanitize(JsonNode document, Set<UUID> allowedImageIds) {
        if (!document.isObject() || !"doc".equals(document.path("type").asString(""))) {
            throw new IllegalArgumentException("根节点必须是 doc");
        }
        ObjectNode copy = (ObjectNode) document.deepCopy();
        copy.retain("type", "content");
        sanitizeChildren(copy, allowedImageIds);
        return copy;
    }

    /** 纯文本：块级节点之间换行，用于搜索与 AI 索引。 */
    public static String plainText(JsonNode document) {
        StringBuilder text = new StringBuilder();
        appendText(document, text);
        return text.toString().replaceAll("\n{3,}", "\n\n").strip();
    }

    private static void sanitizeChildren(ObjectNode parent, Set<UUID> allowedImageIds) {
        JsonNode content = parent.get("content");
        if (content == null) {
            return;
        }
        if (!content.isArray()) {
            throw new IllegalArgumentException("content 必须是数组");
        }
        ArrayNode kept = parent.putArray("content");
        for (JsonNode child : content.values()) {
            if (!child.isObject()) {
                throw new IllegalArgumentException("节点必须是对象");
            }
            ObjectNode node = (ObjectNode) child;
            if (sanitizeNode(node, allowedImageIds)) {
                kept.add(node);
            }
        }
    }

    /** 返回是否保留该节点。 */
    private static boolean sanitizeNode(ObjectNode node, Set<UUID> allowedImageIds) {
        String type = node.path("type").asString("");
        if (type.equals("text")) {
            if (!node.path("text").isString()) {
                throw new IllegalArgumentException("text 节点缺少文本");
            }
            node.retain("type", "text", "marks");
            sanitizeMarks(node);
            return true;
        }
        if (type.equals("hardBreak")) {
            node.retain("type");
            return true;
        }
        if (!BLOCK_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的节点类型：" + type);
        }
        node.retain("type", "attrs", "content");
        sanitizeAttrs(node, type, allowedImageIds);
        if (type.equals("image") && !node.path("attrs").has("imageId")) {
            return false;
        }
        sanitizeChildren(node, allowedImageIds);
        return true;
    }

    private static void sanitizeAttrs(ObjectNode node, String type, Set<UUID> allowedImageIds) {
        JsonNode attrs = node.get("attrs");
        Set<String> allowed = ALLOWED_ATTRS.getOrDefault(type, Set.of());
        if (attrs == null || !attrs.isObject() || allowed.isEmpty()) {
            node.remove("attrs");
            return;
        }
        ObjectNode clean = ((ObjectNode) attrs).retain(allowed);
        switch (type) {
            case "heading" -> {
                int level = clean.path("level").asInt(1);
                clean.put("level", Math.clamp(level, 1, 3));
            }
            case "codeBlock" -> {
                String language = clean.path("language").asString("");
                if (!CODE_LANGUAGE.matcher(language).matches()) {
                    clean.putNull("language");
                }
            }
            case "callout" -> {
                if (!CALLOUT_VARIANTS.contains(clean.path("variant").asString(""))) {
                    clean.put("variant", "info");
                }
            }
            case "image" -> {
                UUID imageId = parseUuid(clean.path("imageId").asString(""));
                if (imageId == null || !allowedImageIds.contains(imageId)) {
                    clean.remove("imageId");
                }
            }
            default -> {}
        }
    }

    private static void sanitizeMarks(ObjectNode textNode) {
        JsonNode marks = textNode.get("marks");
        if (marks == null) {
            return;
        }
        if (!marks.isArray()) {
            throw new IllegalArgumentException("marks 必须是数组");
        }
        ArrayNode kept = textNode.putArray("marks");
        for (JsonNode mark : marks.values()) {
            String type = mark.path("type").asString("");
            if (!MARK_TYPES.contains(type)) {
                throw new IllegalArgumentException("不支持的格式：" + type);
            }
            ObjectNode clean = ((ObjectNode) mark.deepCopy()).retain("type", "attrs");
            if (type.equals("link")) {
                String href = clean.path("attrs").path("href").asString("").strip();
                if (!SAFE_HREF.matcher(href).find()) {
                    continue;
                }
                clean.putObject("attrs").put("href", href);
            } else {
                clean.remove("attrs");
            }
            kept.add(clean);
        }
        if (kept.isEmpty()) {
            textNode.remove("marks");
        }
    }

    private static void collectImageIds(JsonNode node, Set<UUID> ids) {
        if ("image".equals(node.path("type").asString(""))) {
            UUID id = parseUuid(node.path("attrs").path("imageId").asString(""));
            if (id != null) {
                ids.add(id);
            }
        }
        for (JsonNode child : node.path("content").values()) {
            collectImageIds(child, ids);
        }
    }

    private static void appendText(JsonNode node, StringBuilder text) {
        String type = node.path("type").asString("");
        switch (type) {
            case "text" -> text.append(node.path("text").asString(""));
            case "hardBreak" -> text.append('\n');
            default -> {
                for (JsonNode child : node.path("content").values()) {
                    appendText(child, text);
                }
                if (BLOCK_TYPES.contains(type)
                        && !List.of("bulletList", "orderedList", "taskList", "table", "tableRow")
                                .contains(type)) {
                    text.append('\n');
                }
            }
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
