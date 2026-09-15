# 食刻（diet-agent）

一个以自然语言理解饮食需求、从个人或公共餐食库中给出推荐的多 Agent 示例。项目已按前后端分离组织：

- `diet-agent/`：Spring Boot 3 + AgentScope + MyBatis 后端
- `diet-web/`：React + TypeScript + Vite 前端
- `docs/design/`：前端视觉概念与生成素材说明

## 本地运行

环境要求：Java 21、Maven 3.9+、Node.js 20+、MySQL 8。

1. 在 MySQL 中执行 `diet-agent/src/main/resources/db/diet_db.sql`。已有数据库依次执行 `diet-agent/src/main/resources/db/migrations/20260914_model_config.sql`、`20260915_recommendation_history_and_memory.sql` 和 `20260916_stream_favorites_preferences_images.sql`（每个迁移只执行一次）。
2. 配置本地数据库。建议新建不提交的 `diet-agent/src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/diet_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your-password

agentscope:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY:}
```

3. 启动后端：

```bash
cd diet-agent
mvn spring-boot:run
```

4. 启动前端：

```bash
cd diet-web
npm install
npm run dev
```

打开 `http://localhost:5173`。开发服务器会把 `/api` 代理到 `http://localhost:8080`。部署到不同域名时，复制 `diet-web/.env.example` 为 `.env` 并修改 `VITE_API_BASE_URL`，同时通过 `DIET_CORS_ALLOWED_ORIGIN_PATTERNS` 配置后端允许的来源，例如 `https://diet.example.com`。本地开发默认允许 `localhost` 与 `127.0.0.1` 的任意端口。

## 模型配置

前端“模型设置”支持阿里云百炼、OpenAI、DeepSeek、智谱 AI、月之暗面、硅基流动和自定义 OpenAI 兼容服务。可以分别指定 Base URL、接口路径、主模型、轻量模型和 API Key。

保存配置会使 Agent 缓存失效，新对话立即使用新模型。配置查询接口只返回 Key 的脱敏状态，不返回明文。当前示例把凭证存放在服务端数据库，生产环境应加管理端鉴权，并使用 KMS 或应用层加密实现静态加密。

## 推荐历史与长期记忆

每次真正返回餐食卡片时，后端会把推荐结果写入 `diet_recommendation_history`；首页展示当天历史，“收藏与历史”展示全部记录。用户说“上次那个挺好吃，类似的再推荐一下”时，系统会取同一数据源最近一次推荐的餐食标签作为相似推荐条件，并排除原餐食。

`diet_user_memory` 保存跨会话偏好：对话中明确表达的健康目标、菜系、口味和便捷性会累积权重，收藏会加强相关偏好，负向反馈会让具体餐食在后续推荐中被排除。“我的偏好”页面允许手动覆盖长期画像。本轮明确需求始终优先于长期记忆。

## API

所有业务接口位于 `/api/v1/diet`：

- `POST /chat`：餐食推荐对话
- `POST /chat/stream`：SSE 流式对话（`status`、`delta`、`complete`、`error` 事件）
- `/meals/personal`、`/meals/public`：餐食库
- `POST /feedback`：推荐反馈
- `GET /recommendations/history?scope=today|all`：当前用户推荐历史
- `GET/POST/DELETE /favorites`：收藏夹
- `GET /memories`：当前用户的长期记忆摘要
- `GET/PUT /memories/preferences`：可编辑长期偏好
- `/debug/traces`：运行记录与人工标注
- `POST /evaluations`：离线评测
- `/model-config`：读取、保存与测试模型配置

开发请求默认使用 `X-User-Id: 1`。这只是本地示例身份，公开部署前应接入真实认证。

## 验证

```bash
cd diet-agent && mvn test
cd diet-web && npm run build
```
