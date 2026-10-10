package com.wenqu.document;

import static com.wenqu.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wenqu.support.IntegrationTest;
import com.wenqu.support.TestApi;
import com.wenqu.support.TestApi.LoggedInUser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class DocumentContentFlowTest {

    /** 1x1 像素透明 PNG。 */
    private static final byte[] PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    @Autowired
    MockMvc mockMvc;

    LoggedInUser editor;
    long workspaceId;
    long documentId;

    @BeforeEach
    void setUp() throws Exception {
        editor = TestApi.register(mockMvc);
        workspaceId = TestApi.createWorkspace(mockMvc, editor);
        documentId = TestApi.createNode(mockMvc, workspaceId, editor, null, "DOCUMENT", "部署手册");
    }

    @Test
    void newDocumentIsEmptyAtVersionZero() throws Exception {
        mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/documents/{d}", workspaceId, documentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("部署手册"))
                .andExpect(jsonPath("$.data.content.type").value("doc"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void savedContentPersistsAndBecomesSearchable() throws Exception {
        save(paragraph("回滚步骤：先切流量"), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/documents/{d}", workspaceId, documentId)))
                .andExpect(jsonPath("$.data.content.content[0].content[0].text").value("回滚步骤：先切流量"))
                .andExpect(jsonPath("$.data.version").value(1));
        mockMvc.perform(editor.authorize(
                        get("/api/workspaces/{w}/search", workspaceId).param("q", "切流量")))
                .andExpect(jsonPath("$.data[0].id").value(documentId));
    }

    @Test
    void saveBasedOnStaleVersionIsRejected() throws Exception {
        save(paragraph("第一次"), 0).andExpect(status().isOk());

        save(paragraph("另一个标签页的旧版本"), 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(13006));
    }

    @Test
    void invalidContentIsRejected() throws Exception {
        save("{'type':'doc','content':[{'type':'iframe'}]}", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(13007));
    }

    @Test
    void uploadedImageIsServedThroughSignedUrl() throws Exception {
        String body = upload(new MockMultipartFile("file", "a.png", "image/png", PNG))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String imageId = JsonPath.read(body, "$.data.imageId");

        save("{'type':'doc','content':[{'type':'image','attrs':{'imageId':'%s'}}]}".formatted(imageId), 0)
                .andExpect(status().isOk());
        String urlBody = mockMvc.perform(
                        editor.authorize(get("/api/workspaces/{w}/images/{i}/url", workspaceId, imageId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String url = JsonPath.read(urlBody, "$.data.url");

        assertThat(url).contains("X-Amz-Expires=3600");
        HttpResponse<byte[]> image = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body()).isEqualTo(PNG);
        String unsigned = url.substring(0, url.indexOf('?'));
        assertThat(HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(URI.create(unsigned)).build(),
                                HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(403);
    }

    @Test
    void imageTypeIsDetectedFromContentNotExtension() throws Exception {
        upload(new MockMultipartFile("file", "fake.png", "image/png", "<html><script>x</script></html>".getBytes()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(13009));
    }

    @Test
    void imagesFromAnotherWorkspaceAreStripped() throws Exception {
        long otherWorkspace = TestApi.createWorkspace(mockMvc, editor);
        long otherDocument = TestApi.createNode(mockMvc, otherWorkspace, editor, null, "DOCUMENT", "其他");
        String body = mockMvc.perform(editor.authorize(
                        multipart("/api/workspaces/{w}/documents/{d}/images", otherWorkspace, otherDocument)
                                .file(new MockMultipartFile("file", "a.png", "image/png", PNG))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String foreignImage = JsonPath.read(body, "$.data.imageId");

        save("{'type':'doc','content':[{'type':'image','attrs':{'imageId':'%s'}}]}".formatted(foreignImage), 0)
                .andExpect(status().isOk());

        mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/documents/{d}", workspaceId, documentId)))
                .andExpect(jsonPath("$.data.content.content.length()").value(0));
    }

    private ResultActions save(String content, int baseVersion) throws Exception {
        return mockMvc.perform(
                editor.authorize(put("/api/workspaces/{w}/documents/{d}/content", workspaceId, documentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'content':%s,'baseVersion':%d}", content, baseVersion)));
    }

    private ResultActions upload(MockMultipartFile file) throws Exception {
        return mockMvc.perform(
                editor.authorize(multipart("/api/workspaces/{w}/documents/{d}/images", workspaceId, documentId)
                        .file(file)));
    }

    private static String paragraph(String text) {
        return "{'type':'doc','content':[{'type':'paragraph','content':[{'type':'text','text':'%s'}]}]}"
                .formatted(text);
    }
}
