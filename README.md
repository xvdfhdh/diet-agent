# diet-agent — AI 饮食推荐多 Agent 系统

## 项目简介

`diet-agent` 是一个基于 **Spring Boot 3.3** + **AgentScope** 框架构建的 AI 饮食推荐助手。系统采用 **多 Agent 协作架构**，通过 LLM（通义千问 qwen-max / qwen-turbo）驱动意图识别、槽位抽取、澄清追问、推荐理由生成等能力，配合 Java 规则层做安全守卫和流程控制。

用户只需用自然语言描述"想吃什么"，系统即可智能推荐合适的餐食，并支持多轮对话调整、换一批、多餐规划等能力。

---

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.3.13 |
| ORM | MyBatis + Spring Boot Starter | 3.0.4 |
| AI Agent | AgentScope (`io.agentscope`) | 1.0.11 |
| LLM 模型 | 通义千问（DashScope） | qwen-max / qwen-turbo |
| 数据库 | MySQL | 8.4 |
| 工具库 | Hutool | 5.8.30 |
| 简化代码 | Lombok | — |
| 构建 | Maven | — |

---

## 核心架构

### 系统总览

```
┌─────────────────────────────────────────────────────────────────┐
│                        HTTP Controller                          │
│                    POST /api/v1/diet/chat                       │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                DietOrchestratorService（状态机）                  │
│                                                                  │
│  加载会话 → Trace → 加锁 → 记录消息 → 意图识别 → 路由分发          │
│     │                                                           │
│     ├─ MEAL_RECOMMENDATION / CLARIFY_NEEDED                     │
│     │       ↓                                                   │
│     │   槽位合并 → 澄清判断 → 餐食检索 → 重排 → LLM 推荐应答      │
│     │       ↓                                                   │
│     │   安全守卫 → 持久化 → 返回 ChatResponse                    │
│     │                                                           │
│     ├─ MEAL_ADJUST（换一批/调整）                                 │
│     ├─ MEAL_PLAN（多餐规划）                                      │
│     ├─ HEALTH_RISK（健康风险拦截）                                │
│     └─ OTHER（闲聊引导）                                          │
└─────────────────────────────────────────────────────────────────┘
```

### 推荐主链路

```
用户输入 "晚餐想吃清淡点的"
    │
    ▼
┌──────────────┐    ┌───────────────┐    ┌────────────────┐
│ IntentAgent  │───▶│ IntentRevise  │───▶│ SlotMergeService│
│ (LLM 意图识别)│    │ (规则矫正)     │    │ (槽位合并)       │
└──────────────┘    └───────────────┘    └───────┬────────┘
                                                  │
    ┌─────────────────────────────────────────────┘
    ▼
┌────────────────┐    ┌───────────────┐
│ ClarifyAgent   │───▶│ if ASK → 追问  │
│ (澄清判断)      │    │ if READY ↓     │
└────────────────┘    └───────┬───────┘
                              │
    ┌─────────────────────────┘
    ▼
┌────────────────┐    ┌───────────────┐    ┌───────────────────────┐
│ MealSearch     │───▶│ MealRank      │───▶│ RecommendResponseAgent│
│ (数据库检索)    │    │ (打分重排)     │    │ (LLM 推荐理由+口语回复) │
└────────────────┘    └───────────────┘    └───────────┬───────────┘
                                                        │
                                                        ▼
                                              ┌──────────────────┐
                                              │ RiskGuardService │
                                              │ (安全合规检查)     │
                                              └──────────────────┘
```

---

## 意图分类（6 种）

| 意图枚举 | 含义 | 示例用户输入 |
|----------|------|-------------|
| `MEAL_RECOMMENDATION` | 请求餐食推荐 | "晚饭推荐清淡一点的"、"中午吃什么" |
| `CLARIFY_NEEDED` | 信息不足需追问 | "帮我推荐一下"（无任何维度信息） |
| `MEAL_ADJUST` | 调整/替换已有推荐 | "换一批"、"清淡点"、"不太想吃" |
| `MEAL_PLAN` | 多餐规划 | "帮我规划今天三餐"、"一周怎么吃" |
| `HEALTH_RISK` | 涉及医疗/极端节食等高风险 | "胃疼吃什么能治好"、"绝食减肥" |
| `OTHER` | 与饮食无关内容 | "你是谁"、"帮我查快递" |

---

## 槽位体系（7 维度）

系统使用 7 个维度描述用户就餐偏好：

| 槽位字段 | 含义 | 示例候选值 |
|----------|------|-----------|
| `mealTime` | 餐次 | 早餐、午餐、晚餐、夜宵、加餐、三餐 |
| `mood` | 心情 | 疲惫、烦躁、开心、焦虑、低落、压力大 |
| `scene` | 场景 | 工作、校园、家里、周末、加班、运动后 |
| `healthGoal` | 健康诉求 | 减脂、清淡、养胃、高蛋白、均衡、低油、低糖 |
| `cuisine` | 菜系偏好 | 川菜、粤菜、湘菜、轻食、火锅、海鲜、素食、快餐 |
| `taste` | 口味 | 辣、微辣、麻辣、酸甜、咸鲜、蒜香、番茄味 |
| `convenience` | 便捷需求 | 快速、慢享、外带方便、一人食、少排队 |

多轮对话中，槽位采用 **合并策略**：本轮非空覆盖历史，本轮空则保留历史值。

---

## 四个 ReActAgent

| Agent | 职责 | 使用模型 | Prompt 文件 |
|-------|------|----------|------------|
| `IntentAgent` | 意图分类 + 槽位抽取 | `qwen-turbo` | [intent.txt](src/main/resources/diet/prompts/intent.txt) |
| `ClarifyAgent` | 生成自然语言追问 | `qwen-turbo` | [clarify.txt](src/main/resources/diet/prompts/clarify.txt) |
| `RecommendResponseAgent` | 推荐理由 + 口语回复 | `qwen-max` | [recommend-response.txt](src/main/resources/diet/prompts/recommend-response.txt) |
| `EvaluationJudgeAgent` | 离线评估打分 | `qwen-turbo` | [evaluation-judge.txt](src/main/resources/diet/prompts/evaluation-judge.txt) |

- Agent 实例由 `AgentFactory` 按 `sessionId` 缓存（上限 1000，LRU 淘汰）
- 轻量任务（意图识别、追问）用 `qwen-turbo` 降延迟
- 核心任务（推荐理由生成）用 `qwen-max` 保质量

---

## 安全与健壮性

### LLM 失败兜底
- **IntentAgent 失败** → 关键词规则 fallback（意图推断 + 空槽位 + 低置信度）
- **ClarifyAgent 失败** → 模板追问（按 missingSlots 选择预设文案）
- **RecommendResponseAgent 失败** → 模板推荐理由 + 模板口语回复

### 规则层优先
- **ClarifyRuleService**：用 Java 规则判定是否需要追问，LLM 只负责生成追问文案
- **IntentReviseService**：结合历史 SessionState 对 LLM 意图做二次矫正（如无历史推荐时不能走 MEAL_ADJUST）

### 健康安全守卫（RiskGuardService）
在 LLM 生成回复后做关键词扫描：
- 医疗诊断/治疗承诺（"治好"、"治疗"、"处方"）
- 极端节食建议（"绝食"、"只喝水"）
- 绝对化健康承诺（"保证"、"一定能瘦"）
- 特殊人群（"孕妇"、"糖尿病"、"未成年人"）

命中后自动用保守文案替换 LLM 回复。

### 健康免责声明
当用户槽位命中 `减脂`、`低糖`、`控碳水`、`养胃` 时，自动附加免责声明：
> "这些建议只做日常饮食参考，如果有明确疾病或特殊身体情况，建议咨询医生或营养师。"

---

## 数据源模式

| 模式 | 枚举 | 说明 |
|------|------|------|
| PERSONAL | 个人餐食库 | 用户自己录入的餐食，按 `owner_user_id` 隔离 |
| PUBLIC | 公共餐食库 | 预置的通用餐食数据 |

- 个人库为空时提前返回引导文案，提示用户录入餐食
- 两种模式不混查

---

## 多轮对话能力

- **槽位累积**：多轮对话中槽位自动合并，不需要用户重复表达
- **换一批**：系统记录 `lastRecommendations`，换一批时自动排除已推荐餐食
- **多餐规划**：支持"帮我规划今天三餐"等多餐规划场景
- **会话锁**：`ConcurrentHashMap` 按 sessionId 加锁，保证同会话串行执行

---

## 数据库设计

| 表名 | 用途 | 核心字段 |
|------|------|---------|
| `diet_sessions` | 会话状态 | id, user_id, phase, slots(JSON), last_recommendations(JSON) |
| `diet_messages` | 对话消息 | session_id, role, content, intent, agent_trace_id |
| `meal_item` | 餐食数据 | source_type, owner_user_id, name, meal_time~convenience(JSON) |
| `diet_slot_option` | 槽位字典 | slot_name, option_value, sort_order, enabled |
| `diet_request_trace` | 请求链路追踪 | trace_id, session_id, status, trace_json(JSON), 标注字段 |
| `recommend_feedback` | 用户反馈 | user_id, session_id, item_id, action, rating, reason |

---

## 离线评估系统

系统内建评估服务，支持对推荐链路做量化评价：

### 评估维度

| 维度 | 计算方式 | 权重 |
|------|---------|------|
| **规则分** | 意图准确率、槽位准确率、澄清准确率、成本分、延迟分、fallback 率、安全合规、幻觉控制、多轮一致性 | 60% |
| **LLM Judge 分** | 解释质量（1-5 分）+ 自然度（1-5 分），由 EvaluationJudgeAgent 评分 | 10% |
| **用户反馈分** | 来自 `recommend_feedback` 表（like/dislike/rating） | 30% |

### 评分标准

- **token 成本**：≤ 1000 token 满分，≥ 3000 token 零分，中间线性衰减
- **延迟**：≤ 3 秒满分，≥ 8 秒零分，中间线性衰减
- **幻觉控制**：最终推荐卡片是否都来自重排候选集
- **安全合规**：回复文本是否包含禁用短语

---

## API 接口

| 路径 | 方法 | 说明 |
|------|------|------|
| `POST /api/v1/diet/chat` | 同步对话 | 核心对话接口 |
| `GET /api/v1/diet/sessions` | 查询会话 | 获取会话列表 |
| `POST /api/v1/diet/sessions` | 创建会话 | 新建会话 |
| `POST /api/v1/diet/meals` | 录入餐食 | 添加个人/公共餐食 |
| `GET /api/v1/diet/meals` | 查询餐食 | 分页查询餐食 |
| `GET /api/v1/diet/slots` | 槽位字典 | 查询可用槽位候选值 |
| `POST /api/v1/diet/feedback` | 用户反馈 | 提交 like/dislike/rating |
| `GET /api/v1/diet/traces` | 链路查询 | 查看请求 trace |
| `POST /api/v1/diet/evaluation` | 评估报告 | 生成离线评估报告 |

### 对话接口示例

**请求：**
```json
POST /api/v1/diet/chat
Header: X-User-Id: 1

{
  "sessionId": "sess_abc123",
  "message": "晚餐想吃清淡一点的，最近胃不太舒服",
  "sourceMode": "PUBLIC"
}
```

**响应（推荐）：**
```json
{
  "sessionId": "sess_abc123",
  "traceId": "trace_xxx",
  "responseType": "ANSWER",
  "speechText": "晚餐清淡养胃是个好选择。给你推荐这几款：清汤馄饨暖胃易消化...",
  "displayBlocks": [
    {
      "id": 2,
      "name": "清汤馄饨",
      "mealTime": ["早餐", "午餐", "晚餐"],
      "healthGoal": ["清淡", "养胃", "暖胃"],
      "cuisine": ["小吃", "粥汤"],
      "taste": ["清淡", "咸鲜"]
    }
  ],
  "nextAction": "WAIT_USER"
}
```

**响应（澄清追问）：**
```json
{
  "responseType": "CLARIFY",
  "speechText": "你说的这顿是午餐还是晚餐呢？",
  "clarifyQuestion": "你说的这顿是午餐还是晚餐呢？",
  "missingSlots": ["mealTime"],
  "nextAction": "ASK_CLARIFY"
}
```

---

## 项目结构

```
diet-agent/
├── pom.xml                            # Maven 配置
├── lombok.config                      # Lombok 配置
├── README.md                          # 本文件
└── src/main/
    ├── java/com/diet/
    │   ├── DietApplication.java       # 启动入口
    │   ├── agent/
    │   │   ├── builder/               # Agent 构建器
    │   │   │   ├── ClarifyAgentBuilder.java
    │   │   │   ├── EvaluationJudgeAgentBuilder.java
    │   │   │   ├── IntentAgentBuilder.java
    │   │   │   └── RecommendResponseAgentBuilder.java
    │   │   ├── factory/
    │   │   │   └── AgentFactory.java  # 会话级 Agent 工厂
    │   │   └── loader/
    │   │       └── PromptLoader.java  # Prompt 文件加载器
    │   ├── config/
    │   │   └── DietAgentScopeConfig.java  # DashScope 模型配置
    │   ├── constants/
    │   │   └── DietConstants.java
    │   ├── controller/
    │   │   ├── chat/DietChatController.java
    │   │   ├── evaluation/EvaluationController.java
    │   │   ├── feedback/FeedbackController.java
    │   │   ├── meal/MealController.java
    │   │   ├── session/SessionController.java
    │   │   ├── slot/SlotOptionController.java
    │   │   └── trace/AgentTraceController.java
    │   ├── enums/
    │   │   ├── ClarifyAction.java
    │   │   ├── Intent.java
    │   │   ├── RiskLevel.java
    │   │   ├── SessionPhase.java
    │   │   └── SourceMode.java
    │   ├── exception/
    │   │   ├── DietException.java
    │   │   └── DietExceptionHandler.java
    │   ├── mapper/
    │   │   ├── AgentTraceMapper.java
    │   │   ├── FeedbackMapper.java
    │   │   ├── MealMapper.java
    │   │   ├── SessionMapper.java
    │   │   └── SlotOptionMapper.java
    │   ├── model/                     # 20+ 个 DTO/Record
    │   ├── service/
    │   │   ├── clarify/               # 澄清判断
    │   │   ├── evaluation/            # 离线评估
    │   │   ├── feedback/              # 用户反馈
    │   │   ├── intent/                # 意图识别 + 矫正
    │   │   ├── meal/                  # 餐食检索/重排/管理
    │   │   ├── orchestrator/          # ★ 核心编排（状态机）
    │   │   ├── recommend/             # 推荐应答生成
    │   │   ├── risk/                  # 健康风险守卫
    │   │   ├── session/               # 会话状态 + 消息
    │   │   ├── slot/                  # 槽位合并 + 字典
    │   │   └── trace/                 # 链路追踪
    │   └── util/                      # JSON 解析工具
    └── resources/
        ├── application.yml            # 应用配置
        ├── db/diet_db.sql             # 数据库建表脚本
        ├── diet/prompts/              # Agent Prompt 文件
        │   ├── clarify.txt
        │   ├── evaluation-judge.txt
        │   ├── intent.txt
        │   └── recommend-response.txt
        ├── mapper/                    # MyBatis XML
        └── static/                    # 前端静态资源
            ├── index.html
            └── assets/
```

---

## 快速开始

### 环境要求

- JDK 21+
- MySQL 8.0+
- Maven 3.6+

### 1. 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS diet_db DEFAULT CHARSET utf8mb4;
```

执行 `src/main/resources/db/diet_db.sql` 建表和初始数据。

### 2. 配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/diet_db?...
    username: root
    password: your_password

agentscope:
  dashscope:
    api-key: your_dashscope_api_key
```

### 3. 启动

```bash
cd diet-agent
mvn spring-boot:run
```

应用启动在 `http://localhost:8080`。

### 4. 测试

```bash
curl -X POST http://localhost:8080/api/v1/diet/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": null,
    "message": "午餐想吃辣的，有什么推荐？",
    "sourceMode": "PUBLIC"
  }'
```

---

## 设计理念

1. **Agent 只做不确定的事**：意图识别、槽位抽取、追问生成、推荐理由等语义任务交给 LLM；检索、排序、规则校正、安全审查等确定性任务由 Java 层完成
2. **LLM 为辅助，规则兜底**：每个 LLM 调用都有 fallback 策略，确保系统在任何情况下都有可用输出
3. **状态机驱动**：Orchestrator 作为调度中心，按意图枚举和会话阶段做清晰的状态流转
4. **全链路可观测**：每个状态机事件写入 trace，便于排查问题和离线评估
5. **安全优先**：医疗、极端节食等高风险场景前置拦截，推荐回复二次审查
