# 0004 AI 接入使用 Spring AI 与 OpenAI 兼容接口

- 日期：2026-10-10
- 状态：已采纳

## 背景

问答需要对话模型（流式输出、可取消）和 Embedding 模型。要求模型和 Embedding 的供应商通过配置切换，不写死在代码里。开发与演示需要成本可控的模型来源。

## 备选方案

框架：

1. **Spring AI**：Spring 官方项目，与 Spring Boot 自动配置融合；`ChatModel`、`EmbeddingModel` 抽象统一；流式返回 `Flux`，取消订阅即可中断对模型的 HTTP 请求。
2. **LangChain4j**：RAG 组件丰富（文档加载、切分、检索器）。本项目的切块、检索、提示词需要自己控制以便评测，用不上这些高层组件；与 Spring 的集成是第三方 starter。

供应商协议：

1. **OpenAI 兼容接口**：同一套客户端可以接入 OpenAI、DeepSeek、通义（兼容模式）、硅基流动、本地 vLLM / Ollama 等，切换只改 `base-url`、模型名和 API Key。
2. **各供应商原生 SDK**：能用到供应商特有能力，但每加一家都要写适配。

## 决定

- 使用 Spring AI，模型调用只依赖 `ChatModel`、`EmbeddingModel` 接口，模型客户端的配置集中在 `infra`。
- 默认通过 OpenAI 兼容接口接入，对话模型和 Embedding 模型分别配置 `base-url`、模型名、API Key，可以来自不同供应商。
- API Key 只通过环境变量注入。

## 代价

- 供应商特有能力（如某家的特殊参数）通过兼容接口不一定可用。
- Embedding 维度由所选模型决定，`vector` 列维度固定；更换 Embedding 模型需要迁移脚本修改维度并全部重新索引。
- Spring AI 2.0 依赖 Spring Boot 4，版本基线见 [0007](0007-采用SpringBoot4与React19基线.md)。
