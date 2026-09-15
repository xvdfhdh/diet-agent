# 食刻（diet-agent）

一个以自然语言理解饮食需求、从个人或公共餐食库中给出推荐的多 Agent 示例。项目已按前后端分离组织：

- `diet-agent/`：Spring Boot 3 + AgentScope + MyBatis 后端
- `diet-web/`：React + TypeScript + Vite 前端
- `docs/design/`：前端视觉概念与生成素材说明

## 本地运行

环境要求：Java 21、Maven 3.9+、Node.js 20+、MySQL 8。

1. 在 MySQL 中执行 `diet-agent/src/main/resources/db/diet_db.sql`。已有数据库只需执行 `diet-agent/src/main/resources/db/migrations/20260914_model_config.sql`。
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

## API

所有业务接口位于 `/api/v1/diet`：

- `POST /chat`：餐食推荐对话
- `/meals/personal`、`/meals/public`：餐食库
- `POST /feedback`：推荐反馈
- `/debug/traces`：运行记录与人工标注
- `POST /evaluations`：离线评测
- `/model-config`：读取、保存与测试模型配置

开发请求默认使用 `X-User-Id: 1`。这只是本地示例身份，公开部署前应接入真实认证。

## 验证

```bash
cd diet-agent && mvn test
cd diet-web && npm run build
```
