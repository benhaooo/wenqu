package com.wenqu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.wenqu.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * backend/openapi.json 是前后端的接口契约。接口代码变化而契约文件未更新时测试失败。
 * 更新契约：make api（以 -Dopenapi.update=true 运行本测试）。
 */
@IntegrationTest
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("openapi.json");

    @Autowired
    MockMvc mockMvc;

    @Test
    void contractMatchesCode() throws Exception {
        String actual = mockMvc.perform(get("/v3/api-docs"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8)
                + "\n";

        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(CONTRACT, actual);
            return;
        }

        assertThat(CONTRACT).as("缺少 openapi.json，执行 make api 生成").exists();
        assertThat(Files.readString(CONTRACT))
                .as("openapi.json 与接口代码不一致，执行 make api 更新并提交")
                .isEqualTo(actual);
    }
}
