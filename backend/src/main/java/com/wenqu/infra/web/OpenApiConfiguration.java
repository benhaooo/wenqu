package com.wenqu.infra.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("问渠 API").version("v1"))
                // 前端通过同源 /api 访问，契约中不包含主机地址
                .servers(List.of(new Server().url("/")));
    }

    /**
     * swagger-core 把泛型字段（ApiResult.data）引用的类型整体标记为 nullable，生成的前端类型因此全部带上 | null。
     * 是否可空属于引用它的字段，组件类型本身不可空，这里统一去掉组件级别的标记。
     */
    @Bean
    OpenApiCustomizer componentSchemasAreNotNullable() {
        return openApi -> openApi.getComponents().getSchemas().values().forEach(schema -> schema.setNullable(null));
    }
}
