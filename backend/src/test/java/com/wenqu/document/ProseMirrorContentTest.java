package com.wenqu.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wenqu.document.domain.ProseMirrorContent;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProseMirrorContentTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID IMAGE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void keepsSafeLinksAndDropsScriptLinks() {
        JsonNode doc = parse("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"ok","marks":[{"type":"link","attrs":{"href":"https://example.com","onclick":"x"}}]},
                  {"type":"text","text":"bad","marks":[{"type":"link","attrs":{"href":" JavaScript:alert(1)"}}]}
                ]}]}""");

        JsonNode content = ProseMirrorContent.sanitize(doc, Set.of())
                .path("content")
                .path(0)
                .path("content");

        assertThat(content.path(0).path("marks").toString())
                .isEqualTo("[{\"type\":\"link\",\"attrs\":{\"href\":\"https://example.com\"}}]");
        assertThat(content.path(1).has("marks")).isFalse();
    }

    @Test
    void dropsUnknownAttributesAndImagesNotUploadedToTheWorkspace() {
        JsonNode doc = parse("""
                {"type":"doc","content":[
                  {"type":"image","attrs":{"imageId":"%s","src":"https://evil.example/x.png","onerror":"alert(1)"}},
                  {"type":"image","attrs":{"imageId":"00000000-0000-0000-0000-000000000099"}},
                  {"type":"heading","attrs":{"level":9,"style":"x"},"content":[{"type":"text","text":"t"}]}
                ]}""".formatted(IMAGE));

        JsonNode content = ProseMirrorContent.sanitize(doc, Set.of(IMAGE)).path("content");

        assertThat(content.size()).isEqualTo(2);
        assertThat(content.path(0).path("attrs").toString()).isEqualTo("{\"imageId\":\"%s\"}".formatted(IMAGE));
        assertThat(content.path(1).path("attrs").toString()).isEqualTo("{\"level\":3}");
    }

    @Test
    void rejectsUnknownNodeTypes() {
        JsonNode doc = parse("""
                {"type":"doc","content":[{"type":"script","content":[{"type":"text","text":"alert(1)"}]}]}""");

        assertThatThrownBy(() -> ProseMirrorContent.sanitize(doc, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("script");
        assertThatThrownBy(() -> ProseMirrorContent.sanitize(parse("{\"type\":\"paragraph\"}"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractsPlainTextWithBlocksOnSeparateLines() {
        JsonNode doc = parse("""
                {"type":"doc","content":[
                  {"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"部署"}]},
                  {"type":"paragraph","content":[{"type":"text","text":"第一行"},{"type":"hardBreak"},{"type":"text","text":"第二行"}]},
                  {"type":"bulletList","content":[
                    {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"a"}]}]},
                    {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"b"}]}]}
                  ]},
                  {"type":"codeBlock","attrs":{"language":"bash"},"content":[{"type":"text","text":"make deps"}]}
                ]}""");

        assertThat(ProseMirrorContent.plainText(doc)).isEqualTo("部署\n第一行\n第二行\na\n\nb\n\nmake deps");
    }

    private static JsonNode parse(String json) {
        return JSON.readTree(json);
    }
}
