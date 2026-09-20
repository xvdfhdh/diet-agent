# 食刻（diet-agent）

一个以自然语言理解饮食需求、从个人或公共餐食库中给出推荐的多 Agent 示例。项目已按前后端分离组织：

- `diet-agent/`：Spring Boot 3 + AgentScope + MyBatis 后端
- `diet-web/`：React + TypeScript + Vite 前端
- `docs/design/`：前端视觉概念与生成素材说明

完整的系统分层、推荐链路、业务闭环和代码导航见 [项目架构与核心链路](docs/PROJECT_ARCHITECTURE.md)。

## 本地运行

环境要求：Java 21、Maven 3.9+、Node.js 20+、MySQL 8。

1. 在 MySQL 中执行 `diet-agent/src/main/resources/db/diet_db.sql`。已有数据库依次执行 `diet-agent/src/main/resources/db/migrations/20260914_model_config.sql`、`20260915_recommendation_history_and_memory.sql`、`20260916_stream_favorites_preferences_images.sql`、`20260917_user_auth.sql`、`20260918_seeded_meal_taste_option.sql`、`20260920_daily_assistant.sql` 和 `20260921_agentic_recommendation.sql`（每个迁移只执行一次）。最后两个迁移分别增加日常饮食闭环表和智能推荐链路的 Trace 字段。
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

3. 设置 JWT 密钥。每个环境使用独立、随机的至少 32 字节密钥，转为 Base64 后设置 `DIET_JWT_SECRET_BASE64`；没有密钥后端会拒绝启动。PowerShell 示例：

```powershell
$jwtBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
$env:DIET_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
```

首次创建管理员时，再设置 `DIET_ADMIN_USERNAME`（3～32 位字母、数字或下划线）和 `DIET_ADMIN_PASSWORD`（至少 8 位，最多 72 UTF-8 字节）。启动时仅在数据库中没有启用的管理员账户时创建；不存在内置管理员或默认密码。成功创建后清除这两个环境变量，并妥善保管 JWT 密钥；更换密钥会使所有现有登录失效。
后端启动时会检查 `diet_user` 的表结构；缺少该迁移时会直接报错，而不是运行一个登录接口必然失败的实例。

4. 启动后端：

```bash
cd diet-agent
mvn spring-boot:run
```

5. 启动前端：

```bash
cd diet-web
npm install
npm run dev
```

打开 `http://localhost:5173`。开发服务器会把 `/api` 代理到 `http://localhost:8080`。部署到不同域名时，复制 `diet-web/.env.example` 为 `.env` 并修改 `VITE_API_BASE_URL`，同时通过 `DIET_CORS_ALLOWED_ORIGIN_PATTERNS` 配置后端允许的来源，例如 `https://diet.example.com`。本地开发默认允许 `localhost` 与 `127.0.0.1` 的任意端口。

## 模型配置

前端“模型设置”支持阿里云百炼、OpenAI、DeepSeek、智谱 AI、月之暗面、硅基流动和自定义 OpenAI 兼容服务。可以分别指定 Base URL、接口路径、主模型、轻量模型和 API Key。

保存配置会使 Agent 缓存失效，新对话立即使用新模型。配置查询接口只返回 Key 的脱敏状态，不返回明文。模型设置与运行记录、评测仅管理员可访问。当前示例把凭证存放在服务端数据库，生产环境还应使用 KMS 或应用层加密实现静态加密。

## 推荐历史与长期记忆

每次真正返回餐食卡片时，后端会把推荐结果写入 `diet_recommendation_history`；首页展示当天历史，“收藏与历史”展示全部记录。用户说“上次那个挺好吃，类似的再推荐一下”时，系统会取同一数据源最近一次推荐的餐食标签作为相似推荐条件，并排除原餐食。

`diet_user_memory` 保存跨会话偏好：对话中明确表达的健康目标、菜系、口味和便捷性会累积权重，收藏会加强相关偏好，负向反馈会让具体餐食在后续推荐中被排除。“我的偏好”页面允许手动覆盖长期画像。本轮明确需求始终优先于长期记忆。

## 日常饮食闭环

- 餐食详情支持做饭、外食或两者皆可，包含耗时、难度、价格、份数、食材步骤、点餐建议、替换菜品和可选营养估算。
- “本周计划”按早餐、午餐、晚餐、加餐排布，支持拖动、替换、复制、删除、AI 排周、执行打卡和轻量周报。计划保存餐食快照，后续编辑餐食不会改写历史计划。
- “购物清单”只聚合计划中选择“做饭”的餐食；相同食材且单位相同才合并。重新同步只重建自动项，手工项不会被删除。
- 首页显示今日计划，可直接标记已吃、跳过或换一道。评分、跳过和“太贵 / 太费时间 / 吃不饱”等原因会写入长期记忆，影响后续推荐。

营养和价格均为可选估算字段，不用于医疗诊断。

## 双链路智能推荐

推荐页默认使用原有稳定编排；开启“智能推荐”后改用 ReAct Agent。Agent 可在当前选择的个人库或公共库内查询真实餐食，并按需读取长期偏好、近期推荐、本周计划和购物清单。只有用户明确提出“加入计划”“替换计划”或“同步购物清单”时才允许暂存写操作；Agent 完整成功且输出校验通过后才会在同一事务中提交。

智能链路默认 20 秒超时、最多 6 轮推理和 8 次工具调用。模型、工具、超时、非法餐食 ID 或结构化输出异常时会自动回退稳定链路，不保留任何未提交写操作。连续 3 次基础设施失败会熔断 60 秒，模型配置更新或成功调用会恢复。模型设置中的连接测试会分别报告文本调用和工具调用能力。

## API

所有业务接口位于 `/api/v1/diet`：

- `POST /chat`：餐食推荐对话
- `POST /chat/stream`：SSE 流式对话（`status`、`activity`、`delta`、`complete`、`error` 事件）
- `/meals/personal`、`/meals/public`：餐食库
- `POST /meals/ai/complete`：根据餐食名称和已填资料补全空白详情
- `POST /meals/personal/ai-expand`：结合长期偏好和本次自由要求生成个人餐食
- `GET /meals/{id}`：当前用户可访问的餐食详情
- `GET /plans?weekStart=`、`POST /plans/items`、`PUT/DELETE /plans/items/{id}`：周计划查询与维护
- `POST /plans/generate`、`POST /plans/items/{id}/replace`：AI 排周与相似替换
- `POST /plans/items/{id}/check-in`、`GET /plans/weekly-summary`：执行打卡与周报
- `GET /shopping-lists?weekStart=`、`POST /shopping-lists/sync`：购物清单查询与同步
- `POST/PUT/DELETE /shopping-lists/items`：手工购物项与勾选维护
- `POST /feedback`：推荐反馈
- `GET /recommendations/history?scope=today|all`：当前用户推荐历史
- `GET/POST/DELETE /favorites`：收藏夹
- `GET /memories`：当前用户的长期记忆摘要
- `GET/PUT /memories/preferences`：可编辑长期偏好
- `/debug/traces`：运行记录与人工标注
- `POST /evaluations`：离线评测
- `/model-config`：读取、保存与测试模型配置
- `POST /auth/register`、`POST /auth/login`：注册普通用户、登录并获取 JWT
- `GET /auth/me`、`POST /auth/logout`：查询当前账户、注销并使现有令牌失效
- `POST /meals/public`、`PUT/DELETE /meals/public/{mealId}`：管理员单条维护公共餐食
- `POST /meals/public/batch`：管理员一次批量新增、修改、删除公共餐食（最多 100 条，失败整体回滚）

除注册和登录外，所有接口都需要 `Authorization: Bearer <token>`。普通账户可以查看公共库、维护自己的个人库；只有管理员可增删改公共库（包括批量操作）。后端通过 JWT 和数据库当前角色判定权限，前端也隐藏普通用户不应使用的操作。旧 `X-User-Id` 请求头不再作为身份来源；原匿名用户 ID 1 的个人数据不会自动归给新注册账户，如需保留应由数据库管理员确定归属后手动迁移。

## 验证

```bash
cd diet-agent && mvn test
cd diet-web && npm test && npm run build
```

后端 HTTP 集成测试使用隔离的 H2 内存数据库，不会写入本地 MySQL；前端组件测试使用 jsdom 验证登录、退出、角色权限、计划打卡和购物清单交互。
