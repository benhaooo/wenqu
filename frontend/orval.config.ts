import { defineConfig } from 'orval'

// 接口代码只从契约文件生成，见 docs/architecture.md「接口契约」
export default defineConfig({
  wenqu: {
    input: '../backend/openapi.json',
    output: {
      mode: 'tags-split',
      target: 'src/shared/api/generated',
      schemas: 'src/shared/api/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
      override: {
        mutator: { path: 'src/shared/api/http.ts', name: 'http' },
        // 生成函数直接返回响应体 { code, message, data }，错误由 http 抛出
        fetch: { includeHttpResponseReturnType: false },
      },
    },
  },
})
