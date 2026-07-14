# diet-agent — AI 饮食推荐多 Agent 系统

## 目录

1. [项目简介](#1-项目简介)
2. [技术栈](#2-技术栈)
3. [核心架构](#3-核心架构)
   - [3.1 系统分层](#31-系统分层)
   - [3.2 推荐主链路时序](#32-推荐主链路时序)
   - [3.3 会话状态转移](#33-会话状态转移)
4. [意图分类](#4-意图分类)
5. [槽位体系](#5-槽位体系)
6. [Agent 设计](#6-agent-设计)
7. [服务层详解](#7-服务层详解)
   - [7.1 Orchestrator（编排核心）](#71-orchestrator编排核心)
   - [7.2 意图识别与矫正](#72-意图识别与矫正)
   - [7.3 澄清判断](#73-澄清判断)
   - [7.4 餐食检索与重排](#74-餐食检索与重排)
   - [7.5 推荐应答生成](#75-推荐应答生成)
   - [7.6 安全守卫](#76-安全守卫)
   - [7.7 链路追踪](#77-链路追踪)
   - [7.8 会话管理](#78-会话管理)
   - [7.9 离线评估](#79-离线评估)
8. [数据模型](#8-数据模型)
9. [数据库设计](#9-数据库设计)
10. [API 接口](#10-api-接口)
11. [项目结构](#11-项目结构)
12. [快速开始](#12-快速开始)
13. [设计理念](#13-设计理念)

---

## 1. 项目简介

`diet-agent` 是一个基于 **Spring Boot 3.3** + **AgentScope** 框架构建的 AI 饮食推荐助手。系统采用 **多 Agent 协作 + Java 状态机** 架构，通过 LLM（通义千问 qwen-max / qwen-turbo）驱动意图识别、槽位抽取、澄清追问、推荐理由生成等语义任务，配合 Java 规则层做流程控制和安全守卫。

**核心能力**：用户用自然语言描述"想吃什么"，系统即可推荐合适餐食，支持多轮调整、换一批、多餐规划。

**代码规模**：约 50 个 Java 源文件，分为 controller / service / agent / model / mapper / enums 六层。

---

## 2. 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.3.13 |
| ORM | MyBatis (Spring Boot Starter) | 3.0.4 |
| AI Agent 框架 | AgentScope (`io.agentscope`) | 1.0.11 |
| LLM 模型 | 阿里云 DashScope (通义千问) | qwen-max / qwen-turbo |
| 数据库 | MySQL | 8.4 |
| 工具库 | Hutool | 5.8.30 |
| JSON 处理 | Jackson | (内置) |
| 代码简化 | Lombok | (内置) |
| 构建 | Maven | — |

### LLM 模型分工

| 模型 | 定位 | 使用场景 |
|------|------|---------|
| `qwen-max` | 主模型，高能力 | RecommendResponseAgent：推荐理由 + 口语回复生成 |
| `qwen-turbo` | 轻量模型，低延迟 | IntentAgent：意图分类；ClarifyAgent：追问生成；EvaluationJudgeAgent：离线评分 |

### AgentScope 集成方式

[AgentFactory](src/main/java/com/diet/agent/factory/AgentFactory.java) 按 `sessionId` 管理 `ReActAgent` 实例（[`AgentSet`](src/main/java/com/diet/agent/factory/AgentFactory.java#L85-L89) 包含 intent / clarify / recommendResponse 三个 Agent），上限 1000 个会话，LRU 淘汰。每次调用前 `agent.getMemory().clear()` 清空上下文，避免跨轮污染。

---

## 3. 核心架构

### 3.1 系统分层

```
┌───────────────────────────────────────────────────────────────────┐
│  Controller 层（7 个 REST Controller）                              │
│  DietChatController / MealController / SessionController / ...     │
└───────────────────────────────┬───────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────────┐
│  Orchestrator 层（状态机核心）                                       │
│  DietOrchestratorService：会话加载 → Trace → 加锁 → 意图 → 路由    │
└───────┬───────────┬───────────┬───────────┬───────────┬───────────┘
        │           │           │           │           │
┌───────▼──┐ ┌──────▼───┐ ┌─────▼────┐ ┌────▼────┐ ┌────▼──────────┐
│ Intent   │ │ Clarify  │ │ Meal     │ │Recommend│ │ RiskGuard     │
│ Agent    │ │ Agent    │ │ Search   │ │Response │ │ Service       │
│ Service  │ │ Service  │ │ + Rank   │ │Agent Svc│ │ (安全守卫)     │
└──────────┘ └──────────┘ └──────────┘ └─────────┘ └───────────────┘
        │           │           │           │           │
┌───────▼──────────▼───────────▼───────────▼───────────▼───────────┐
│  Service 层                                                       │
│  SessionService / SessionStateService / SlotMergeService / ...    │
│  AgentTraceService（全链路事件采集）                                 │
└───────────────────────────────┬───────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────────┐
│  Mapper 层（MyBatis）                                              │
│  SessionMapper / MealMapper / AgentTraceMapper / FeedbackMapper   │
└───────────────────────────────┬───────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────────┐
│  MySQL 8.4（6 张表）                                               │
│  diet_sessions / diet_messages / meal_item / diet_slot_option     │
│  diet_request_trace / recommend_feedback                          │
└───────────────────────────────────────────────────────────────────┘
```

### 3.2 推荐主链路时序

一次完整推荐请求经过 15 个 Trace 事件节点：

```
用户输入
  │
  ▼
[REQUEST_RECEIVED]     ← 加载或创建 SessionState
  │
  ▼  会话级锁（ConcurrentHashMap，key=sessionId）
  │
[USER_MESSAGE_RECORDED]← INSERT diet_messages (role=user)
  │
  ▼  PERSONAL 空库检查 → 空则返回引导文案（短路）
  │
[INTENT_RECOGNIZED]    ← IntentAgent（qwen-turbo）: intent + slots + confidence
  │
[INTENT_REVISED]       ← IntentReviseService：规则二次矫正
  │
[ROUTE_SELECTED]        ← 按 Intent 枚举分发到 5 条分支之一
  │
  ▼  ▼  ▼  ▼  ▼
  │  │  │  │  └─ OTHER → 固定引导文案（短路）
  │  │  │  └─── HEALTH_RISK → conservativeMessage（短路）
  │  │  └───── MEAL_PLAN → 标记 PLAN 阶段，然后 ↓
  │  └──────── MEAL_ADJUST → 取 excludeMealIds，然后 ↓
  └─────────── MEAL_RECOMMENDATION / CLARIFY_NEEDED → 主推荐链路:
                │
                ▼
            [SLOTS_MERGED]        ← SlotMergeService：7 维历史+本轮合并
                │
                ▼
            [CLARIFY_DECISION]    ← ClarifyRuleService(Java) + ClarifyAgent(LLM)
                │                  ASK → 追问返回（短路）
                │                  READY ↓
                ▼
            [MEAL_SEARCHED]       ← MealMapper.search (MySQL JSON_OVERLAPS)
                │
                ▼
            [MEAL_RANKED]         ← MealRankService：7 维 overlap 打分重排，top10
                │
                ▼  空候选 → 提示文案（短路）
                │
            [RECOMMEND_RESULT_BUILT]  ← RecommendResponseAgent(qwen-max)
                │
                ▼
            [RESPONSE_AGENT_RESULT]   ← JSON → RecommendResult + ResponseResult
                │
                ▼
            [NUTRITION_GUARD_CHECKED] ← RiskGuardService：关键词扫描
                │                     BLOCK → conservativeMessage 替换
                │                     PASS ↓
                ▼
            [COMPLIANCE_GUARD_REWRITTEN] / [NUTRITION_GUARD_REWRITTEN]
                │
                ▼  持久化: UPDATE diet_sessions + INSERT diet_messages (role=assistant)
                │
            [RESPONSE_READY]           ← ChatResponse 返回 Controller
                │
                ▼
            [REQUEST_FINISHED]         ← durationMs 记录
```

**完整 Trace 事件类型（共 19 种）：**

| 事件类型 | 阶段 | 说明 |
|----------|------|------|
| `REQUEST_RECEIVED` | HTTP | 请求进入，加载 SessionState |
| `REQUEST_FINISHED` | HTTP | 请求完成，记录总耗时 |
| `REQUEST_FAILED` | HTTP | 请求异常，Trace 标记 FAILED |
| `USER_MESSAGE_RECORDED` | SESSION | 用户消息入库 |
| `PERSONAL_LIBRARY_EMPTY` | ROUTE | 个人库为空，返回引导 |
| `INTENT_RECOGNIZED` | INTENT | IntentAgent LLM 识别结果 |
| `INTENT_REVISED` | INTENT | IntentReviseService 矫正后结果 |
| `ROUTE_SELECTED` | ROUTE | 确定分发目标意图 |
| `SLOTS_MERGED` | SLOT | 历史+本轮槽位合并 |
| `CLARIFY_DECISION` | CLARIFY | 澄清判断 ASK/READY |
| `ADJUST_CONTEXT_RESOLVED` | ADJUST | 换一批链路，解析排除 ID |
| `PLAN_CONTEXT_RESOLVED` | PLAN | 多餐规划链路 |
| `MEAL_SEARCHED` | SEARCH | 数据库检索结果 |
| `MEAL_RANKED` | RANK | 重排打分结果 |
| `RECOMMEND_RESULT_BUILT` | RECOMMEND | LLM 推荐结果 |
| `RESPONSE_AGENT_RESULT` | RESPONSE | 解析后的 ResponseResult |
| `NUTRITION_GUARD_CHECKED` | GUARD | 安全审查结果 |
| `NUTRITION_GUARD_REWRITTEN` | GUARD | 安全不通过，替换回复 |
| `COMPLIANCE_GUARD_REWRITTEN` | GUARD | 安全通过确认 |
| `RESPONSE_READY` | RESPONSE | 最终返回前 |
| `AGENT_CALL` | AGENT | 每次 LLM 调用（含 token/latency） |

### 3.3 会话状态转移

```
                    ┌─────────────────────────────────┐
                    │            START                  │
                    │  (新会话，无槽位，无推荐历史)        │
                    └────────┬──────────────┬──────────┘
                             │              │
              槽位足够       │              │  槽位不足
                             ▼              ▼
                    ┌────────────┐  ┌──────────────┐
                    │  RECOMMEND │  │   CLARIFY    │
                    │  (推荐中)   │  │  (等待补充)   │
                    └─────┬──────┘  └──────┬───────┘
                          │                │
             用户说       │                │  用户补充槽位
             "换一批"     │                │
                          ▼                ▼
                    ┌────────────┐  ┌──────────────┐
                    │  RECOMMEND │  │  RECOMMEND   │
                    │ (exclude已 │  │  (走主推荐链)  │
                    │  推荐ID)    │  │              │
                    └────────────┘  └──────────────┘

                    ┌────────────┐
                    │   PLAN     │  ← 用户说"规划三餐"
                    │ (多餐规划)  │     仍走推荐流水线
                    └────────────┘
```

**SessionPhase 枚举定义：**

| 枚举值 | 含义 | 触发条件 |
|--------|------|---------|
| `START` | 初始状态 | 会话刚创建 |
| `CLARIFY` | 等待澄清 | Clarify 判断 ASK 后持久化到此状态 |
| `RECOMMEND` | 推荐阶段 | 槽位足够，进入检索推荐 |
| `PLAN` | 多餐规划 | Intent 为 MEAL_PLAN |

> SessionState 是不可变对象，每次状态变更通过 `withPhase()` / `withIntent()` / `withSlots()` 等方法返回新实例。

---

## 4. 意图分类

系统将用户输入分为 6 种意图，由 IntentAgent（LLM）识别 + IntentReviseService（Java 规则）矫正：

| 意图枚举 | 路由去向 | 典型用户输入 | 优先级 |
|----------|---------|-------------|--------|
| `MEAL_RECOMMENDATION` | 主推荐链路 | "晚饭推荐清淡的"、"中午吃啥" | — |
| `CLARIFY_NEEDED` | 主推荐链路 → 澄清 | "帮我推荐一下"（无维度信息） | — |
| `MEAL_ADJUST` | 换一批链路 | "换一批"、"去掉油腻的" | 需有上轮推荐 |
| `MEAL_PLAN` | 多餐规划链路 | "规划今天三餐"、"一周吃啥" | — |
| `HEALTH_RISK` | 风险拦截（短路） | "胃疼吃什么治好"、"绝食减肥" | 最高：前置拦截 |
| `OTHER` | 闲聊引导（短路） | "你是谁"、"帮我查快递" | — |

### IntentReviseService 矫正规则

LLM 意图可能误判，Java 层做二次矫正：

1. **健康风险前置**：用户原文含关键词（`胃疼`、`糖尿病`、`孕妇`、`绝食` 等），无论 LLM 给出什么意图，强制改为 `HEALTH_RISK`
2. **调整需有历史**：LLM 识别为 `MEAL_ADJUST` 但 `lastRecommendations` 为空时，降级为 `MEAL_RECOMMENDATION`
3. **低置信降级**：`MEAL_RECOMMENDATION` 但 `confidence < 0.4` 时，降级为 `CLARIFY_NEEDED`

### IntentAgent LLM 失败兜底

当 LLM 超时或 JSON 解析失败时，走关键词规则 fallback（按优先级匹配）：

```
"胃疼/糖尿病/孕妇/..." → HEALTH_RISK
"换一批/清淡点/..."    → MEAL_ADJUST
"三餐/一周"            → MEAL_PLAN
"你是谁/你好"          → OTHER
"吃什么/推荐/晚饭/..." → MEAL_RECOMMENDATION
其他                   → CLARIFY_NEEDED
```

---

## 5. 槽位体系

### 7 维槽位定义

| 字段 | 含义 | 候选值数量 | 示例 |
|------|------|-----------|------|
| `mealTime` | 餐次 | 8 | 早餐、午餐、晚餐、夜宵、加餐、早午餐、下午茶、三餐 |
| `mood` | 心情 | 10 | 疲惫、烦躁、开心、焦虑、低落、平静、压力大、没胃口、想放松、想奖励自己 |
| `scene` | 场景 | 10 | 工作、校园、家里、周末、加班、运动后、通勤、聚餐、独处、旅行 |
| `healthGoal` | 健康目标 | 14 | 减脂、清淡、养胃、高蛋白、均衡、降火、低油、低盐、低糖、补能、增肌、控碳水、易消化、暖胃 |
| `cuisine` | 菜系 | 24 | 川菜、粤菜、湘菜、江浙菜、东北菜、鲁菜、闽南菜、云南菜、新疆菜、轻食、西餐、日料、韩餐、东南亚菜、火锅、烧烤、海鲜、素食、家常、小吃、粉面、粥汤、快餐、甜品 |
| `taste` | 口味 | 16 | 辣、微辣、中辣、麻辣、甜、酸甜、咸鲜、鲜香、酱香、蒜香、番茄味、咖喱味、奶香、油香、烟火气、清淡 |
| `convenience` | 便捷性 | 10 | 快速、慢享、外带方便、堂食舒服、少排队、少餐具、一人食、多人共享、适合备餐、适合边走边吃 |

> 候选值由 `diet_slot_option` 表管理，支持动态增删。IntentAgent prompt 中会注入完整字典，LLM 做语义归一（如"午饭"→"午餐"、"减重"→"减脂"）。

### 槽位合并策略（SlotMergeService）

```
merge(历史槽位, 本轮槽位) → 逐字段：
  if 本轮[字段] 非空 → 用本轮
  else             → 保留历史
```

特点：多轮对话中用户不需要重复表达已知信息，槽位自动累积。例如：

```
第1轮："午餐吃什么"          → mealTime=["午餐"]
第2轮："要辣的"              → mealTime=["午餐"], taste=["辣"]
第3轮："换清淡点的"          → mealTime=["午餐"], taste=["清淡"]  ← 覆盖
```

### SlotBundle（不可变数据类）

每个维度存 `List<String>`，构造时自动去重、去空、trim。`isEmpty()` 判断 7 个维度是否全空。

---

## 6. Agent 设计

### Agent 工厂（AgentFactory）

```java
// 按 sessionId 获取或创建 Agent 集合
AgentSet agents = agentFactory.get(sessionId);
// AgentSet 包含三个 ReActAgent
agents.intent();           // IntentAgent
agents.clarify();          // ClarifyAgent
agents.recommendResponse();// RecommendResponseAgent
```

缓存策略：`LinkedHashMap(accessOrder=true, maxSize=1000)`，超过上限按 LRU 淘汰。prompt 版本变更（`diet.prompt.version` 配置项）后自动创建新 Agent。

### 三个 Agent 对比

| Agent | 模型 | 输入 | 输出 | 输入 Token |
|-------|------|------|------|-----------|
| `IntentAgent` | qwen-turbo | userId + sessionId + 历史摘要 + 槽位字典 + 用户原文 | JSON: `{intent, slots, confidence}` | ~2K-5K |
| `ClarifyAgent` | qwen-turbo | 用户原文 + 已知槽位 + 缺失字段列表 | 纯文本追问 | ~1K |
| `RecommendResponseAgent` | qwen-max | 用户原文 + sourceMode + 槽位 + Top3 候选 | JSON: `{recommendations[], speechText}` | ~2K-4K |
| `EvaluationJudgeAgent` | qwen-turbo | trace 摘要（意图/槽位/回复文本） | JSON: `{explanationQuality, naturalness, reason}` | ~1K |

### Prompt 管理

每个 Agent 的 System Prompt 存储在 `src/main/resources/diet/prompts/` 下的 `.txt` 文件中，由 [PromptLoader](src/main/java/com/diet/agent/loader/PromptLoader.java) 通过 `ClassPathResource` 加载，保证 JAR 部署也能正常读取。

---

## 7. 服务层详解

### 7.1 Orchestrator（编排核心）

**[DietOrchestratorService](src/main/java/com/diet/service/orchestrator/DietOrchestratorService.java)** 是系统的调度中心，持有 12 个依赖：

| 依赖 | 职责 |
|------|------|
| `SessionService` | 消息落库（diet_messages） |
| `SessionStateService` | 会话状态读写（diet_sessions） |
| `IntentAgentService` | 调用 IntentAgent 识别意图 |
| `IntentReviseService` | Java 规则矫正意图 |
| `SlotMergeService` | 多轮槽位合并 |
| `ClarifyAgentService` | 澄清判断 + 追问生成 |
| `MealSearchService` | 数据库检索候选 |
| `MealRankService` | 候选重排打分 |
| `RecommendResponseAgentService` | LLM 生成推荐理由和回复 |
| `MealService` | PERSONAL 空库检查 |
| `RiskGuardService` | 安全合规检查 |
| `AgentTraceService` | 全链路事件采集 |

**5 条分支处理：**

| 分支方法 | 触发意图 | 说明 |
|----------|---------|------|
| `handleRecommendation` | RECOMMENDATION / CLARIFY_NEEDED | 主链路：合并 → 澄清 → 检索 → 推荐 |
| `handleAdjust` | MEAL_ADJUST | 取 `lastRecommendations` 作为 `excludeMealIds`，重跑主链路 |
| `handlePlan` | MEAL_PLAN | 标记 PLAN 阶段，走主链路 |
| `handleHealthRisk` | HEALTH_RISK | 直接返回 `conservativeMessage` |
| `handleChitchat` | OTHER | 直接返回固定引导文案 |

### 7.2 意图识别与矫正

**IntentAgentService**：调用 IntentAgent LLM 识别意图 + 槽位。将槽位字典（`slotOptionService.findAllOptions()`）注入 prompt，要求 LLM 只从候选值中选择槽位标签。失败时走关键词 fallback。

**IntentReviseService**：三道矫正规则（健康风险前置 > 无历史降级调整 > 低置信降级澄清），确保状态机输入可靠。

### 7.3 澄清判断

流程：

```
ClarifyAgentService.decide(sessionId, userInput, slots)
  │
  ├─ ClarifyRuleService.missingSlots(slots) -- Java 规则计算缺失字段
  │    ├─ mealTime 空 → 必追问
  │    ├─ healthGoal 空 且 无强偏好(菜系/口味/场景/便捷) → 必追问
  │    └─ 否则 → 槽位足够
  │
  ├─ missingSlots 为空 → 直接 READY（不调 LLM）
  │
  └─ missingSlots 非空 → 调 ClarifyAgent LLM 生成追问文案
       ├─ 成功 → ASK(追问文案, missingSlots)
       └─ 失败 → ASK(模板追问, missingSlots)
```

**ClarifyRuleService 模板追问：**

| 缺失字段 | 模板文案 |
|----------|---------|
| `mealTime` | "这顿主要是早餐、午餐还是晚餐？" |
| `healthGoal` | "这顿更想清淡点、顶饱点，还是按口味来？" |
| 其他 | "我再确认一下，你这顿最看重口味、健康目标还是方便快捷？" |

### 7.4 餐食检索与重排

**MealSearchService → MealService.search：**

调用 [MealMapper.search](src/main/resources/mapper/MealMapper.xml)，使用 MySQL 8.0 `JSON_OVERLAPS` 函数做标签交集检索：

```sql
-- 伪 SQL 示意
SELECT * FROM meal_item
WHERE source_type = ?
  AND (? IS NULL OR owner_user_id = ?)
  AND (JSON_OVERLAPS(meal_time, '[..]') OR '[..]' = '[]')
  AND (JSON_OVERLAPS(mood, '[..]') OR '[..]' = '[]')
  -- ... 7 维依次
LIMIT 50
```

每个槽位维度只要有交集即召回，最多 50 条。

**MealRankService.rank：**

对候选做 7 维 **overlap 打分**，按分数降序排列，过滤 excludeMealIds，返回最多 10 条：

```
score = Σ( overlap(餐食[维度i], 查询[维度i]) ) / 7

overlap(itemValues, queryValues) = count( itemValues ∩ queryValues ) / queryValues.size()
```

| 餐食 | 用户查询: healthGoal=[清淡,高蛋白] | overlap 分 |
|------|----------------------------------|-----------|
| 鸡胸肉轻食碗 tags=[清淡,高蛋白,低油,均衡] | 2/2 | 1.00 |
| 番茄鸡蛋面 tags=[清淡,养胃,易消化] | 1/2 | 0.50 |

**总结**：检索层负责"有关就召回"，重排层负责"谁更匹配谁排前面"。

### 7.5 推荐应答生成

**RecommendResponseAgentService** 一次 LLM 调用完成两件事：

1. 为 Top3 候选生成推荐理由（每条 30-100 字，贴合用户槽位，不编造特征）
2. 生成自然口语回复 speechText（开场 10-25 字 + 逐款介绍 + 收尾提问，80-180 字）

防御策略：

- mealId 必须来自输入候选，不在候选内的被丢弃
- 无推荐理由的用模板兜底：`"{餐食名}比较符合你提到的{healthGoal/taste}诉求。"`
- LLM 异常时整体走模板回复

### 7.6 安全守卫

**RiskGuardService** 在 LLM 生成回复后做关键词扫描，5 条规则：

| 规则 | 关键词 | 阻断后动作 |
|------|--------|-----------|
| 意图层命中 | `HEALTH_RISK` 意图 | 替换为 conservativeMessage |
| 医疗承诺 | `治好`、`治疗`、`诊断`、`药`、`处方` | 同上 |
| 极端节食 | `绝食`、`一天不吃`、`只喝水` | 同上 |
| 绝对化承诺 | `保证`、`一定能瘦`、`最健康`、`包瘦` | 同上 |
| 特殊人群 | `孕妇`、`糖尿病`、`高血压`、`未成年人`、`儿童` | 同上 |

**保守文案（conservativeMessage）：**

> 这个问题涉及健康或医疗风险，我不能替代医生做诊断或治疗建议。可以从日常饮食角度选择清淡、均衡、不过量的餐食；如果症状明显或有慢病、孕期等情况，建议咨询医生或营养师。

**健康免责声明**：当用户槽位命中 `减脂`、`低糖`、`控碳水`、`养胃` 时，推荐回复末尾自动附加免责声明。

### 7.7 链路追踪

**[AgentTraceService](src/main/java/com/diet/service/trace/AgentTraceService.java)** 通过 ThreadLocal + `TraceScope`（实现 `AutoCloseable`）实现全链路事件采集：

```
AgentTraceService
├─ ThreadLocal<TraceScope> currentScope  ← 绑定当前请求线程
│
└─ TraceScope（内部类，AutoCloseable）
    ├─ traceId / sessionId / userId
    ├─ events: List<TraceEvent>          ← 每个状态机事件
    ├─ stepOrder: AtomicInteger          ← 自增序号
    ├─ status: "SUCCESS" | "FAILED"
    │
    ├─ recordEvent(eventType, phase, input, output)    ← 记录状态机事件
    ├─ callAgent(sessionId, agentName, model, input)   ← 调 LLM + 自动记录
    ├─ recordError(eventType, phase, input, exception) ← 记录异常
    │
    └─ close()  ← try-with-resources 结束
        └─ flushTrace(scope)
            └─ INSERT INTO diet_request_trace (trace_json JSON)
```

每条 Trace 包含：

- `trace_json`：完整的 JSON 文档，含 `events` 数组（所有 TraceEvent）
- `event_count`：事件总数
- `duration_ms`：整轮耗时
- `status`：SUCCESS / FAILED
- `error_message`：异常摘要

### 7.8 会话管理

**SessionStateService**：

- `loadOrCreate(sessionId, userId, sourceMode)`：DB 读取或创建新会话
- `save(state)`：持久化到 `diet_sessions`（UPDATE）
- 序列化格式：`slots` 列为 JSON，含 7 维槽位 + `_meta`（sourceMode/currentIntent）

**SessionService**：

- `appendMessage(sessionId, role, content, intent, traceId)`：INSERT diet_messages
- `recentConversationTurns(sessionId, userId, n)`：读取最近对话，转成 `ConversationTurn` 摘要（每段 ≤ 120 字）

### 7.9 离线评估

**EvaluationService** 从 `diet_request_trace` 的 `trace_json` 中解析链路事件，自动计算多维度指标：

#### 9 个规则指标

| 指标 | 计算方式 | 需要人工标注 |
|------|---------|:-----------:|
| `intentAccuracy` | 预测意图 vs 标注意图，0 或 1 | 是 |
| `slotAccuracy` | 逐槽位比较标注值，命中数/比较数 | 是 |
| `clarifyNecessityAccuracy` | 预测 ASK/READY vs 标注动作，0 或 1 | 是 |
| `tokenCost` | 累加所有 AGENT_CALL 的 `totalTokens`（原始值） | 否 |
| `tokenCostScore` | ≤1000=1, ≥3000=0, 中间线性（归一化） | 否 |
| `latencyMs` | 从 `RequestTraceRow.durationMs` 获取（原始值） | 否 |
| `latencyScore` | ≤3s=1, ≥8s=0, 中间线性（归一化） | 否 |
| `fallbackRate` | 1/0 标记（单条），区间平均 = fallback 率 | 否 |
| `fallbackScore` | fallback 为 0、正常为 1（反向分） | 否 |
| `safetyCompliance` | 禁用短语检查 + Trace 状态，0 或 1 | 否 |
| `hallucinationControl` | 响应卡片 ID 是否都来自重排候选集，0 或 1 | 否 |
| `multiTurnConsistency` | 仅 MEAL_ADJUST：排除 ID 不在最终推荐中 = 1 | 否 |

#### LLM Judge 指标

由 [EvaluationJudgeAgent](src/main/java/com/diet/agent/builder/EvaluationJudgeAgentBuilder.java)（qwen-turbo）评分：

| 指标 | 范围 | 说明 |
|------|------|------|
| `explanationQuality` | 1-5 | 是否说明"为什么推荐"，理由是否贴合需求和槽位 |
| `naturalness` | 1-5 | 表达是否简洁自然，不像机械表单拼接 |

#### 总分公式

```
总分 = 规则分 × 60% + Judge分 × 10% + 用户反馈分 × 30%
```
（某项不存在时，剩余项按比例重新归一）

---

## 8. 数据模型

### 核心 Record / DTO

| 类 | 类型 | 字段 |
|----|------|------|
| `ChatRequest` | DTO | sessionId, message, sourceMode, context |
| `ChatResponse` | DTO | sessionId, traceId, responseType, speechText, displayBlocks, nextAction, clarifyQuestion, missingSlots |
| `SessionState` | 领域对象 | sessionId, userId, phase, sourceMode, currentIntent, slots, lastRecommendations |
| `SlotBundle` | 值对象 | 7 个 `List<String>`（mealTime / mood / scene / healthGoal / cuisine / taste / convenience） |
| `IntentResult` | Record | intent, slots, confidence |
| `ClarifyResult` | Record | action(ASK/READY), questionToAsk, missingSlots |
| `MealItem` | 领域对象 | id, sourceType, ownerUserId, name, slots(SlotBundle), matchScore |
| `MealSearchRequest` | Record | sourceMode, userId, slots, excludeMealIds |
| `MealRankRequest` | Record | candidates, slots, excludeMealIds |
| `RecommendResult` | Record | recommendations(List<RecommendedMealOption>), needDisclaimer |
| `RecommendedMealOption` | Record | itemId, sourceType, name, reason, matchScore, matchedSlots |
| `ResponseResult` | Record | speechText, displayBlocks, nextAction |
| `RiskGuardResult` | Record | passed, reasons, rewriteSuggestion |
| `ConversationTurn` | Record | role, intent, summary, timestamp |
| `CreateSessionResponse` | Record | sessionId, message |
| `EvaluationRequest` | DTO | startAt, endAt, includeLlmJudge, limit |
| `EvaluationReport` | Record | startAt, endAt, traceCount, labeledCount, averageScore, metricAverages, results |
| `TraceEvaluationResult` | Record | traceId, sessionId, score, ruleScore, judgeScore, feedbackScore, metrics, detail |
| `EvaluationJudgeResult` | Record | explanationQuality, naturalness, reason |
| `TraceLabelRequest` | DTO | expectedIntent, expectedSlots, expectedClarifyAction, labelNote |
| `FeedbackRequest` | DTO | sessionId, itemId, action, rating, reason |
| `MealRequest` | DTO | name, mealTime, mood, scene, healthGoal, cuisine, taste, convenience |

### 枚举一览

| 枚举 | 值 | 说明 |
|------|----|------|
| `Intent` | MEAL_RECOMMENDATION / CLARIFY_NEEDED / MEAL_ADJUST / MEAL_PLAN / HEALTH_RISK / OTHER | 6 种意图 |
| `SessionPhase` | START / CLARIFY / RECOMMEND / PLAN | 4 个阶段 |
| `ClarifyAction` | ASK / READY | 澄清结果 |
| `RiskLevel` | LOW / HIGH | Guard 风险分级 |
| `SourceMode` | PERSONAL / PUBLIC | 数据源模式 |

---

## 9. 数据库设计

### 表关系

```
diet_sessions (会话状态)
    │
    ├──< diet_messages (对话消息, FK: session_id)
    │
    └──< diet_request_trace (链路追踪, FK: session_id)

meal_item (餐食数据, source_type + owner_user_id 隔离)

diet_slot_option (槽位字典)

recommend_feedback (用户反馈, FK: session_id + item_id)
```

### 表结构详解

#### diet_sessions — 会话状态表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | VARCHAR(64) PK | sessionId，格式 `sess_` + 32位 hex |
| `user_id` | BIGINT | 用户 ID |
| `phase` | VARCHAR(64) | SessionPhase 枚举名 |
| `slots` | JSON | 7 维槽位 + `_meta`(sourceMode/currentIntent) |
| `last_recommendations` | JSON | 已推荐餐食 ID 数组，换一批时排除 |
| `created_at` / `updated_at` | DATETIME | 时间戳 |

#### diet_messages — 对话消息表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 自增 |
| `session_id` | VARCHAR(64) | 关联会话 |
| `role` | VARCHAR(32) | `user` 或 `assistant` |
| `content` | TEXT | 消息内容 |
| `intent` | VARCHAR(64) | 关联意图枚举名 |
| `agent_trace_id` | VARCHAR(128) | 关联 trace |
| `created_at` | DATETIME | 创建时间 |

#### meal_item — 餐食数据表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 自增 |
| `source_type` | VARCHAR(16) | `PERSONAL` 或 `PUBLIC` |
| `owner_user_id` | BIGINT | PERSONAL 时关联用户 |
| `name` | VARCHAR(128) | 餐食名称 |
| `meal_time` ~ `convenience` | JSON(7) | 7 个槽位维度，存 JSON 数组 |
| 索引 | `idx_public_meal_source(source_type)` | 公共库查询 |
| 索引 | `idx_private_meal_source(owner_user_id, source_type)` | 个人库查询 |

#### diet_slot_option — 槽位字典表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 自增 |
| `slot_name` | VARCHAR(64) | 槽位名（mealTime / mood 等） |
| `option_value` | VARCHAR(64) | 候选值 |
| `sort_order` | INT | 排序 |
| `enabled` | TINYINT | 软开关 |

唯一约束：`(slot_name, option_value)`

#### diet_request_trace — 链路追踪表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 自增 |
| `trace_id` | VARCHAR(128) UNIQUE | trace 唯一标识 |
| `session_id` | VARCHAR(64) | 关联会话 |
| `user_id` | BIGINT | 用户 ID |
| `status` | VARCHAR(32) | SUCCESS / FAILED |
| `event_count` | INT | 事件总数 |
| `duration_ms` | BIGINT | 总耗时 |
| `error_message` | TEXT | 异常信息 |
| `trace_json` | JSON | 完整事件数组 |
| `expected_intent` / `expected_slots` / `expected_clarify_action` | — | 人工标注字段 |
| `labeled_by` / `labeled_at` / `label_note` | — | 标注元信息 |

#### recommend_feedback — 用户反馈表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 自增 |
| `user_id` | BIGINT | 用户 ID |
| `session_id` | VARCHAR(64) | 关联会话 |
| `item_id` | BIGINT | 关联餐食 |
| `action` | VARCHAR(32) | LIKE / DISLIKE / SWITCH 等 |
| `rating` | INT | 1-5 星评分 |
| `reason` | VARCHAR(512) | 反馈原因 |

---

## 10. API 接口

### 接口总览

| 路径 | 方法 | 说明 |
|------|------|------|
| `POST /api/v1/diet/chat` | 同步对话 | **核心接口**，接收用户消息，返回澄清或推荐结果 |
| `POST /api/v1/diet/sessions` | 创建会话 | 新建会话，返回 `sessionId` |
| `GET /api/v1/diet/sessions` | 查询会话 | 按 userId 查询会话列表 |
| `POST /api/v1/diet/meals` | 录入餐食 | 创建个人餐食（需 X-User-Id） |
| `GET /api/v1/diet/meals` | 查询餐食 | 分页查询餐食列表 |
| `PUT /api/v1/diet/meals/{id}` | 更新餐食 | 修改个人餐食 |
| `DELETE /api/v1/diet/meals/{id}` | 删除餐食 | 删除个人餐食 |
| `GET /api/v1/diet/slots` | 槽位字典 | 查询所有可用的槽位候选值 |
| `POST /api/v1/diet/feedback` | 用户反馈 | 提交推荐结果的反馈 |
| `GET /api/v1/diet/traces` | 链路查询 | 按时间范围 / sessionId 查询 trace |
| `PUT /api/v1/diet/traces/{traceId}/label` | 标注 Trace | 人工标注期望意图/槽位/澄清动作 |
| `POST /api/v1/diet/evaluation` | 评估报告 | 生成离线评估报告 |

### 对话接口详解

**POST /api/v1/diet/chat**

请求头：`X-User-Id: {userId}`（必填，缺省值 1）

请求体：

```json
{
  "sessionId": "sess_abc123",     // 可选，null 时自动创建新会话
  "message": "晚餐想吃清淡的，胃不太舒服",
  "sourceMode": "PUBLIC"           // PERSONAL | PUBLIC
}
```

响应类型一：推荐结果（`responseType: "ANSWER"`）

```json
{
  "sessionId": "sess_abc123",
  "traceId": "trace_a1b2c3d4...",
  "responseType": "ANSWER",
  "speechText": "晚餐清淡养胃是个好选择。给你推荐这几款：\n清汤馄饨——暖胃易消化，适合疲惫时快速补充能量...\n想换清淡点的还是就这几款？",
  "displayBlocks": [
    {
      "id": 2,
      "name": "清汤馄饨",
      "sourceType": "PUBLIC",
      "mealTime": ["早餐", "午餐", "晚餐", "三餐"],
      "healthGoal": ["清淡", "养胃", "暖胃"],
      "cuisine": ["小吃", "粥汤"],
      "taste": ["清淡", "咸鲜"],
      "convenience": ["快速", "少餐具"],
      "matchScore": 0.85
    }
  ],
  "nextAction": "WAIT_USER"
}
```

响应类型二：澄清追问（`responseType: "CLARIFY"`）

```json
{
  "sessionId": "sess_abc123",
  "traceId": "trace_a1b2c3d4...",
  "responseType": "CLARIFY",
  "speechText": "这顿主要是早餐、午餐还是晚餐呢？",
  "clarifyQuestion": "这顿主要是早餐、午餐还是晚餐呢？",
  "missingSlots": ["mealTime"],
  "nextAction": "ASK_CLARIFY",
  "displayBlocks": []
}
```

### 评估接口

**POST /api/v1/diet/evaluation**

```json
{
  "userId": 1,
  "startAt": "2026-07-01T00:00:00",
  "endAt": "2026-07-14T23:59:59",
  "includeLlmJudge": true,
  "limit": 200
}
```

响应：`EvaluationReport`，含区间平均值和每条 trace 的详细指标。

---

## 11. 项目结构

```
diet-agent/
├── pom.xml
├── lombok.config
├── README.md
├── .gitignore
└── src/main/
    ├── java/com/diet/
    │   ├── DietApplication.java                    # Spring Boot 启动入口
    │   │
    │   ├── agent/
    │   │   ├── builder/
    │   │   │   ├── ClarifyAgentBuilder.java         # 澄清 Agent 构建器
    │   │   │   ├── EvaluationJudgeAgentBuilder.java # 评估 Judge Agent 构建器
    │   │   │   ├── IntentAgentBuilder.java          # 意图识别 Agent 构建器
    │   │   │   └── RecommendResponseAgentBuilder.java # 推荐应答 Agent 构建器
    │   │   ├── factory/
    │   │   │   └── AgentFactory.java                # 会话级 Agent 工厂 (LRU 缓存)
    │   │   └── loader/
    │   │       └── PromptLoader.java                # Prompt 文件加载器
    │   │
    │   ├── config/
    │   │   └── DietAgentScopeConfig.java            # DashScope 主/轻量模型 Bean
    │   │
    │   ├── constants/
    │   │   └── DietConstants.java                   # X-User-Id 常量
    │   │
    │   ├── controller/
    │   │   ├── chat/DietChatController.java         # POST /api/v1/diet/chat
    │   │   ├── evaluation/EvaluationController.java # POST /api/v1/diet/evaluation
    │   │   ├── feedback/FeedbackController.java     # 反馈接口
    │   │   ├── meal/MealController.java             # 餐食 CRUD
    │   │   ├── session/SessionController.java       # 会话管理
    │   │   ├── slot/SlotOptionController.java       # 槽位字典
    │   │   └── trace/AgentTraceController.java      # Trace 查询和标注
    │   │
    │   ├── enums/
    │   │   ├── ClarifyAction.java                   # ASK / READY
    │   │   ├── Intent.java                          # 6 种意图
    │   │   ├── RiskLevel.java                       # LOW / HIGH
    │   │   ├── SessionPhase.java                    # 4 个阶段
    │   │   └── SourceMode.java                      # PERSONAL / PUBLIC
    │   │
    │   ├── exception/
    │   │   ├── DietException.java                   # 业务异常
    │   │   └── DietExceptionHandler.java            # 全局异常处理器
    │   │
    │   ├── mapper/
    │   │   ├── AgentTraceMapper.java                # diet_request_trace CRUD
    │   │   ├── FeedbackMapper.java                  # recommend_feedback CRUD
    │   │   ├── MealMapper.java                      # meal_item: JSON_OVERLAPS 检索
    │   │   ├── SessionMapper.java                   # diet_sessions + diet_messages
    │   │   └── SlotOptionMapper.java                # diet_slot_option 查询
    │   │
    │   ├── model/                                   # 20+ 个 DTO / Record
    │   │   ├── ChatRequest.java / ChatResponse.java
    │   │   ├── SessionState.java / SessionRow.java / SessionMessageRow.java
    │   │   ├── SlotBundle.java
    │   │   ├── IntentResult.java / ClarifyResult.java
    │   │   ├── MealItem.java / MealItemRow.java
    │   │   ├── RecommendResult.java / RecommendedMealOption.java
    │   │   ├── ResponseResult.java / MealResponse.java
    │   │   ├── RiskGuardResult.java
    │   │   ├── EvaluationReport.java / EvaluationRequest.java
    │   │   ├── EvaluationJudgeResult.java / TraceEvaluationResult.java
    │   │   ├── TraceLabelRequest.java / RequestTraceRow.java
    │   │   ├── FeedbackRequest.java / FeedbackRow.java
    │   │   ├── ConversationTurn.java
    │   │   └── CreateSessionResponse.java
    │   │
    │   ├── service/
    │   │   ├── orchestrator/DietOrchestratorService.java  # ★ 状态机核心
    │   │   ├── intent/
    │   │   │   ├── IntentAgentService.java                # IntentAgent 调用
    │   │   │   └── IntentReviseService.java               # 规则矫正
    │   │   ├── clarify/
    │   │   │   ├── ClarifyAgentService.java               # 澄清判断 + LLM 追问
    │   │   │   └── ClarifyRuleService.java                # Java 规则判断是否追问
    │   │   ├── meal/
    │   │   │   ├── MealSearchService.java                 # 检索封装
    │   │   │   ├── MealRankService.java                   # 7 维 overlap 重排
    │   │   │   └── MealService.java                       # 餐食 CRUD + JSON_OVERLAPS
    │   │   ├── recommend/RecommendResponseAgentService.java # LLM 推荐应答
    │   │   ├── risk/RiskGuardService.java                 # 健康安全守卫
    │   │   ├── session/
    │   │   │   ├── SessionService.java                    # 消息落库 + 历史摘要
    │   │   │   └── SessionStateService.java               # 会话状态读写
    │   │   ├── slot/
    │   │   │   ├── SlotMergeService.java                  # 多轮槽位合并
    │   │   │   └── SlotOptionService.java                 # 槽位字典查询
    │   │   ├── trace/AgentTraceService.java               # ThreadLocal 全链路追踪
    │   │   ├── evaluation/
    │   │   │   ├── EvaluationService.java                 # 离线评估核心
    │   │   │   └── EvaluationJudgeService.java            # LLM Judge 调用
    │   │   └── feedback/FeedbackService.java              # 反馈管理
    │   │
    │   └── util/
    │       ├── JsonService.java                           # JSON 序列化工具
    │       ├── LlmJsonService.java                        # LLM 输出 JSON 提取
    │       └── SlotJsonPicker.java                        # 槽位 JSON 字段过滤
    │
    └── resources/
        ├── application.yml                                # 应用配置
        ├── db/diet_db.sql                                 # 建表 + 初始数据
        ├── diet/prompts/
        │   ├── clarify.txt                                # ClarifyAgent System Prompt
        │   ├── evaluation-judge.txt                       # EvaluationJudgeAgent Prompt
        │   ├── intent.txt                                 # IntentAgent System Prompt
        │   └── recommend-response.txt                     # RecommendResponseAgent Prompt
        ├── mapper/                                        # MyBatis XML
        │   ├── AgentTraceMapper.xml
        │   ├── FeedbackMapper.xml
        │   ├── MealMapper.xml
        │   ├── SessionMapper.xml
        │   └── SlotOptionMapper.xml
        └── static/                                        # 前端页面
            ├── index.html
            └── assets/
                ├── css/app.css
                └── js/
                    ├── api.js                             # 前端 API 封装
                    └── app.js                             # 前端交互逻辑
```

---

## 12. 快速开始

### 环境要求

- JDK 21+
- MySQL 8.0+
- Maven 3.6+
- 阿里云 DashScope API Key

### 1. 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS diet_db DEFAULT CHARSET utf8mb4;
USE diet_db;
SOURCE src/main/resources/db/diet_db.sql;
```

建表脚本包含 5 条公共餐食和完整槽位字典的初始数据。

### 2. 配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/diet_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: your_password

agentscope:
  dashscope:
    api-key: sk-your-api-key-here      # 必填

diet:
  llm:
    main-model: qwen-max               # 推荐应答用
    light-model: qwen-turbo            # 意图识别/澄清用
  session:
    max-history-turns: 10              # IntentAgent 最多注入多少条历史
  prompt:
    version: v1                         # Prompt 版本，变更后重建 Agent

server:
  port: 8080
```

### 3. 启动

```bash
cd diet-agent
mvn clean package -DskipTests
java -jar target/diet-agent-1.0-SNAPSHOT.jar

# 或开发模式
mvn spring-boot:run
```

### 4. 验证

```bash
# 创建会话
curl -X POST http://localhost:8080/api/v1/diet/sessions \
  -H "X-User-Id: 1"

# 发送推荐请求
curl -X POST http://localhost:8080/api/v1/diet/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"sessionId":null,"message":"午餐想吃辣的，有什么推荐？","sourceMode":"PUBLIC"}'

# 换一批
curl -X POST http://localhost:8080/api/v1/diet/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"sessionId":"sess_xxx","message":"换一批","sourceMode":"PUBLIC"}'

# 查看 Trace
curl http://localhost:8080/api/v1/diet/traces?sessionId=sess_xxx \
  -H "X-User-Id: 1"

# 生成评估报告
curl -X POST http://localhost:8080/api/v1/diet/evaluation \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"startAt":"2026-07-01T00:00:00","endAt":"2026-07-14T23:59:59","includeLlmJudge":true}'
```

---

## 13. 设计理念

1. **Agent 只做不确定的事**：意图识别、槽位抽取、追问生成、推荐理由等语义任务交给 LLM；检索、排序、规则校正、安全审查等确定性任务由 Java 层完成
2. **LLM 为辅助，规则兜底**：每个 LLM 调用都有 fallback 策略（关键词、模板），确保系统在任何情况下都有可用输出
3. **状态机驱动**：Orchestrator 作为调度中心，按意图枚举和会话阶段做清晰的分支路由
4. **不可变状态**：SessionState 不可变，每次变更通过 `withXxx()` 返回新实例，避免并发修改问题
5. **会话级串行**：`ConcurrentHashMap<String, Object>` 按 sessionId 加锁，保证同会话内操作串行
6. **全链路可观测**：每个状态机事件写入 trace，支持离线评估、LLM-as-Judge 和多维度指标追踪
7. **安全优先**：医疗、极端节食等高风险场景前置拦截（意图层），推荐回复二次审查（Guard 层），健康建议附加免责声明
8. **数据源隔离**：PERSONAL / PUBLIC 严格分离，不混查，个人库为空时提前引导
