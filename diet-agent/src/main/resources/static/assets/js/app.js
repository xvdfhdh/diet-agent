(function () {
    "use strict";
    const app = document.getElementById("app");
    const toast = document.getElementById("toast");
    const userIdInput = document.getElementById("userIdInput");
    const SLOT_LABELS = {
        mealTime: "用餐时间",
        mood: "心情状态",
        scene: "用餐场景",
        healthGoal: "健康目标",
        cuisine: "菜系偏好",
        taste: "口味偏好",
        convenience: "便利程度"
    };
    const INTENTS = [
        "MEAL_RECOMMENDATION",
        "CLARIFY_NEEDED",
        "MEAL_ADJUST",
        "MEAL_PLAN",
        "HEALTH_RISK",
        "OTHER"
    ];
    const state = {
        home: { loaded: false, personalCount: 0, publicCount: 0 },
        slotOptions: null,
        personalMeals: [],
        publicMeals: [],
        editingMeal: null,
        chat: {
            sourceMode: "PERSONAL",
            sessionId: null,
            sending: false,
            messages: [
                {
                    role: "assistant",
                    text: "你好，我可以根据你的个人餐食库或公共餐食库推荐今天吃什么。可以试试问我：今晚想吃清淡一点，有什么推荐？"
                }
            ]
        },
        traces: {
            rows: [],
            selected: null,
            loading: false,
            filters: defaultTraceFilters()
        },
        evaluation: {
            report: null,
            loading: false,
            form: defaultRangeForm()
        }
    };
    function defaultRangeForm() {
        const end = new Date();
        const start = new Date(end.getTime() - 24 * 60 * 60 * 1000);
        return {
            startAt: toLocalInputValue(start),
            endAt: toLocalInputValue(end),
            limit: 50,
            includeLlmJudge: false
        };
    }
    function defaultTraceFilters() {
        const range = defaultRangeForm();
        return {
            startAt: range.startAt,
            endAt: range.endAt,
            onlyUnlabeled: false,
            limit: 50,
            sessionId: ""
        };
    }
    function toLocalInputValue(date) {
        const pad = (value) => String(value).padStart(2, "0");
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }
    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }
    function safeJson(value) {
        if (value === null || value === undefined || value === "") {
            return "";
        }
        try {
            const parsed = typeof value === "string" ? JSON.parse(value) : value;
            return JSON.stringify(parsed, null, 2);
        } catch (error) {
            return String(value);
        }
    }
    function showToast(message, type) {
        toast.textContent = message;
        toast.className = `toast show ${type === "error" ? "error" : ""}`;
        window.clearTimeout(showToast.timer);
        showToast.timer = window.setTimeout(() => {
            toast.className = "toast";
        }, 3200);
    }
    function setLoading(button, loadingText) {
        if (!button) {
            return () => {};
        }
        const oldText = button.textContent;
        button.disabled = true;
        button.textContent = loadingText || "处理中...";
        return () => {
            button.disabled = false;
            button.textContent = oldText;
        };
    }
    async function guard(action, successMessage) {
        try {
            const result = await action();
            if (successMessage) {
                showToast(successMessage);
            }
            return result;
        } catch (error) {
            showToast(error.message || "操作失败", "error");
            throw error;
        }
    }
    function currentRoute() {
        return (location.hash || "#/diet").slice(1).split("?")[0] || "/diet";
    }
    function navigate(route) {
        location.hash = route;
    }
    function setActiveNav(route) {
        document.querySelectorAll("[data-nav]").forEach((item) => {
            item.classList.toggle("active", item.dataset.nav === route);
        });
    }
    function render() {
        const route = currentRoute();
        setActiveNav(route);
        if (route === "/diet") {
            renderHome();
        } else if (route === "/diet/chat") {
            renderChat();
        } else if (route === "/diet/meals/personal") {
            renderPersonalMeals();
        } else if (route === "/diet/meals/public") {
            renderPublicMeals();
        } else if (route === "/admin/traces") {
            renderTraces();
        } else if (route === "/admin/evaluations") {
            renderEvaluations();
        } else {
            navigate("/diet");
        }
        app.focus({ preventScroll: true });
    }
    function renderHome() {
        app.innerHTML = `
            <section class="hero">
                <div class="hero-panel">
                    <span class="badge">多 Agent 饮食推荐</span>
                    <h1>用更轻松的方式决定今天吃什么</h1>
                    <p>维护你的个人餐食库，也可以从公共餐食库开始。助手会根据时间、心情、场景、健康目标、口味和便利程度给出推荐，并在信息不足时主动追问。</p>
                    <div class="hero-actions">
                        <a class="btn primary" href="#/diet/chat">开始聊天推荐</a>
                        <a class="btn soft" href="#/diet/meals/personal">管理个人餐食</a>
                        <a class="btn ghost" href="#/admin/traces">查看 Trace</a>
                    </div>
                </div>
                <aside class="grid stats">
                    ${statCard("个人餐食", state.home.loaded ? state.home.personalCount : "加载中", "你的私有餐食库，用于个性化推荐")}
                    ${statCard("公共餐食", state.home.loaded ? state.home.publicCount : "加载中", "系统预置餐食，适合快速体验")}
                    ${statCard("当前用户", DietApi.getUserId(), "所有请求会带上 X-User-Id")}
                </aside>
            </section>
            <section class="grid three" style="margin-top: 18px;">
                ${featureCard("聊天推荐", "按自然语言表达需求，页面会展示澄清问题、推荐卡片和反馈入口。", "#/diet/chat")}
                ${featureCard("餐食维护", "用标签多选维护自己的常吃餐食，后续推荐会优先从个人库检索。", "#/diet/meals/personal")}
                ${featureCard("评测后台", "查看请求 Trace，标注预期结果，并生成批量评估报告。", "#/admin/evaluations")}
            </section>
        `;
        loadHomeStats();
    }
    function statCard(label, value, desc) {
        return `
            <div class="stat-card">
                <span class="muted">${escapeHtml(label)}</span>
                <strong>${escapeHtml(value)}</strong>
                <p class="muted">${escapeHtml(desc)}</p>
            </div>
        `;
    }
    function featureCard(title, desc, href) {
        return `
            <article class="card">
                <div class="card-title">
                    <div>
                        <h3>${escapeHtml(title)}</h3>
                        <p>${escapeHtml(desc)}</p>
                    </div>
                </div>
                <a class="btn soft" href="${href}">进入</a>
            </article>
        `;
    }
    async function loadHomeStats() {
        if (state.home.loaded) {
            return;
        }
        try {
            const [personal, publicMeals] = await Promise.all([
                DietApi.listPersonalMeals(),
                DietApi.listPublicMeals()
            ]);
            state.home = {
                loaded: true,
                personalCount: personal.length,
                publicCount: publicMeals.length
            };
            if (currentRoute() === "/diet") {
                renderHome();
            }
        } catch (error) {
            showToast(error.message || "首页数据加载失败", "error");
        }
    }
    function renderChat() {
        app.innerHTML = `
            <section class="chat-layout">
                <div class="section chat-window">
                    <div class="card-title">
                        <div>
                            <h2>聊天推荐</h2>
                            <p>当前会话：${state.chat.sessionId ? escapeHtml(state.chat.sessionId) : "尚未创建，发送消息时自动创建"}</p>
                        </div>
                        <div class="inline-actions">
                            <button class="btn ${state.chat.sourceMode === "PERSONAL" ? "soft" : "ghost"}" data-action="set-source" data-source="PERSONAL">个人库</button>
                            <button class="btn ${state.chat.sourceMode === "PUBLIC" ? "soft" : "ghost"}" data-action="set-source" data-source="PUBLIC">公共库</button>
                            <button class="btn ghost" data-action="new-session">新会话</button>
                        </div>
                    </div>
                    <div id="messages" class="messages">${state.chat.messages.map(renderMessage).join("")}</div>
                    <form id="chatForm" class="composer">
                        <textarea name="message" placeholder="例如：今晚想吃清淡一点，最好快手一点" required></textarea>
                        <button class="btn primary" type="submit">${state.chat.sending ? "发送中..." : "发送"}</button>
                    </form>
                </div>
                <aside class="grid">
                    <div class="card">
                        <div class="card-title">
                            <div>
                                <h3>快捷问题</h3>
                                <p>点击后可直接填入输入框。</p>
                            </div>
                        </div>
                        <div class="chips">
                            ${["早餐想吃方便一点", "晚饭推荐清淡低脂的", "今天心情一般，想吃点热乎的", "换一批，不想吃刚才那些", "我胃不舒服，应该吃什么"].map((text) => `<button class="chip" data-action="quick-message" data-message="${escapeHtml(text)}">${escapeHtml(text)}</button>`).join("")}
                        </div>
                    </div>
                    <div class="card">
                        <h3>使用提示</h3>
                        <p class="muted">PERSONAL 模式依赖你的个人餐食库；如果还没有数据，可以先去维护餐食，或切换到 PUBLIC 模式体验。</p>
                        <div class="button-row">
                            <a class="btn soft" href="#/diet/meals/personal">维护餐食</a>
                            <a class="btn ghost" href="#/diet/meals/public">看公共库</a>
                        </div>
                    </div>
                </aside>
            </section>
        `;
        scrollMessagesToBottom();
    }
    function renderMessage(message) {
        const mealCards = (message.meals || []).map((meal) => renderMealCard(meal, { feedback: true, sessionId: message.sessionId })).join("");
        const missingSlots = message.missingSlots && message.missingSlots.length
            ? `<div class="chips">${message.missingSlots.map((slot) => `<span class="chip selected">${escapeHtml(SLOT_LABELS[slot] || slot)}</span>`).join("")}</div>`
            : "";
        const trace = message.traceId
            ? `<span>traceId：<a href="#/admin/traces" data-action="open-trace" data-trace-id="${escapeHtml(message.traceId)}">${escapeHtml(message.traceId)}</a></span>`
            : "";
        return `
            <article class="message ${message.role}">
                <div class="bubble">${escapeHtml(message.text)}</div>
                ${missingSlots}
                ${mealCards ? `<div class="grid">${mealCards}</div>` : ""}
                ${trace ? `<div class="message-meta">${trace}</div>` : ""}
            </article>
        `;
    }
    function scrollMessagesToBottom() {
        const messages = document.getElementById("messages");
        if (messages) {
            messages.scrollTop = messages.scrollHeight;
        }
    }
    async function submitChat(form) {
        const messageInput = form.elements.message;
        const message = messageInput.value.trim();
        if (!message || state.chat.sending) {
            return;
        }
        state.chat.messages.push({ role: "user", text: message });
        messageInput.value = "";
        state.chat.sending = true;
        renderChat();
        try {
            if (!state.chat.sessionId) {
                const session = await DietApi.createSession();
                state.chat.sessionId = session.sessionId;
            }
            const response = await DietApi.chat({
                sessionId: state.chat.sessionId,
                message,
                sourceMode: state.chat.sourceMode,
                context: {}
            });
            state.chat.sessionId = response.sessionId || state.chat.sessionId;
            state.chat.messages.push({
                role: "assistant",
                text: response.clarifyQuestion || response.speechText || "我已经处理完这轮请求。",
                responseType: response.responseType,
                meals: response.displayBlocks || [],
                missingSlots: response.missingSlots || [],
                traceId: response.traceId,
                sessionId: response.sessionId || state.chat.sessionId
            });
        } catch (error) {
            showToast(error.message || "聊天请求失败", "error");
            state.chat.messages.push({ role: "assistant", text: "这轮请求失败了，请稍后重试。" });
        } finally {
            state.chat.sending = false;
            renderChat();
        }
    }
    function resetChat() {
        state.chat.sessionId = null;
        state.chat.messages = [
            {
                role: "assistant",
                text: "已开启新会话。告诉我你的用餐时间、口味、场景或健康目标，我来推荐。"
            }
        ];
        renderChat();
    }
    async function renderPersonalMeals() {
        if (!state.slotOptions) {
            app.innerHTML = `<section class="section"><div class="empty">标签字典加载中...</div></section>`;
            await ensureSlotOptions();
            if (currentRoute() !== "/diet/meals/personal") {
                return;
            }
        }
        await ensurePersonalMeals();
        if (currentRoute() !== "/diet/meals/personal") {
            return;
        }
        app.innerHTML = `
            <section class="split">
                <div class="section">
                    <div class="card-title">
                        <div>
                            <h2>个人餐食</h2>
                            <p>维护常吃餐食，聊天推荐时可切换到个人库。</p>
                        </div>
                        <button class="btn primary" data-action="new-meal">新增餐食</button>
                    </div>
                    <div id="personalMealList">${renderMealList(state.personalMeals, { editable: true })}</div>
                </div>
                <aside class="section">
                    ${renderMealForm()}
                </aside>
            </section>
        `;
    }
    function renderMealForm() {
        const meal = state.editingMeal || emptyMeal();
        const title = meal.id ? "编辑餐食" : "新增餐食";
        return `
            <div class="card-title">
                <div>
                    <h3>${title}</h3>
                    <p>从下拉框选择标签，用餐时间为必选项，其余可留空。</p>
                </div>
            </div>
            <form id="mealForm" class="form-grid">
                <input type="hidden" name="mealId" value="${escapeHtml(meal.id || "")}">
                <div class="field full">
                    <label for="mealName">餐食名称</label>
                    <input id="mealName" name="name" value="${escapeHtml(meal.name || "")}" placeholder="例如：番茄鸡蛋面" required>
                </div>
                <p class="field-hint full">标签下拉框支持多选：Windows 按住 Ctrl，Mac 按住 Command 点击可多项选择。</p>
                ${Object.entries(SLOT_LABELS).map(([key, label]) => renderSlotPicker(key, label, meal[key] || [])).join("")}
                <div class="field full">
                    <div class="button-row">
                        <button class="btn primary" type="submit">${meal.id ? "保存修改" : "创建餐食"}</button>
                        <button class="btn ghost" type="button" data-action="cancel-edit">清空</button>
                    </div>
                </div>
            </form>
        `;
    }
    function renderSlotPicker(key, label, selected) {
        const options = state.slotOptions && state.slotOptions[key] ? state.slotOptions[key] : [];
        const selectedSet = new Set(selected || []);
        const required = key === "mealTime";
        return `
            <div class="field">
                <label for="slot-${escapeHtml(key)}">${escapeHtml(label)}${required ? "（必选）" : ""}</label>
                <select
                    id="slot-${escapeHtml(key)}"
                    class="slot-select"
                    name="${escapeHtml(key)}"
                    multiple
                    size="5"
                    ${required ? "required" : ""}
                >
                    ${options.map((option) => {
                        const isSelected = selectedSet.has(option);
                        return `<option value="${escapeHtml(option)}" ${isSelected ? "selected" : ""}>${escapeHtml(option)}</option>`;
                    }).join("")}
                </select>
            </div>
        `;
    }
    function emptyMeal() {
        return {
            name: "",
            mealTime: [],
            mood: [],
            scene: [],
            healthGoal: [],
            cuisine: [],
            taste: [],
            convenience: []
        };
    }
    function renderMealList(meals, options) {
        if (!meals.length) {
            return `<div class="empty">暂无餐食。可以先新增几道常吃的菜。</div>`;
        }
        return `<div class="grid two">${meals.map((meal) => renderMealCard(meal, options || {})).join("")}</div>`;
    }
    function renderMealCard(meal, options) {
        const editable = options && options.editable;
        const feedback = options && options.feedback;
        return `
            <article class="meal-card">
                <header>
                    <div>
                        <h3>${escapeHtml(meal.name)}</h3>
                        <p class="muted">${escapeHtml(meal.sourceType || "")}</p>
                    </div>
                    ${meal.matchScore ? `<span class="score">匹配 ${Math.round(meal.matchScore * 100)}%</span>` : ""}
                </header>
                <div class="chips">${mealTags(meal).map((tag) => `<span class="chip selected">${escapeHtml(tag)}</span>`).join("")}</div>
                ${editable ? `
                    <div class="button-row">
                        <button class="btn soft" data-action="edit-meal" data-id="${escapeHtml(meal.id)}">编辑</button>
                        <button class="btn ghost" data-action="delete-meal" data-id="${escapeHtml(meal.id)}">删除</button>
                    </div>
                ` : ""}
                ${feedback ? `
                    <div class="button-row">
                        <button class="btn soft" data-action="feedback" data-action-value="LIKE" data-item-id="${escapeHtml(meal.id)}" data-session-id="${escapeHtml(options.sessionId || "")}">喜欢</button>
                        <button class="btn ghost" data-action="feedback" data-action-value="ADOPT" data-item-id="${escapeHtml(meal.id)}" data-session-id="${escapeHtml(options.sessionId || "")}">采纳</button>
                        <button class="btn ghost" data-action="feedback" data-action-value="DISLIKE" data-item-id="${escapeHtml(meal.id)}" data-session-id="${escapeHtml(options.sessionId || "")}">不合适</button>
                    </div>
                ` : ""}
            </article>
        `;
    }
    function mealTags(meal) {
        return Object.keys(SLOT_LABELS).flatMap((key) => (meal[key] || []).map((value) => `${SLOT_LABELS[key]}：${value}`));
    }
    async function ensurePersonalMeals(force) {
        if (!force && state.personalMeals.length) {
            return;
        }
        try {
            state.personalMeals = await DietApi.listPersonalMeals();
            state.home.loaded = false;
            if (currentRoute() === "/diet/meals/personal") {
                document.getElementById("personalMealList").innerHTML = renderMealList(state.personalMeals, { editable: true });
            }
        } catch (error) {
            showToast(error.message || "个人餐食加载失败", "error");
        }
    }
    async function ensureSlotOptions() {
        if (state.slotOptions) {
            return;
        }
        try {
            state.slotOptions = await DietApi.slotOptions();
        } catch (error) {
            showToast(error.message || "槽位字典加载失败", "error");
            throw error;
        }
    }
    async function saveMeal(form) {
        const { id, payload } = mealPayloadFromForm(form);
        if (!payload.name) {
            showToast("请填写餐食名称", "error");
            return;
        }
        if (!payload.mealTime.length) {
            showToast("请至少选择一个用餐时间标签", "error");
            return;
        }
        const restore = setLoading(form.querySelector("button[type=submit]"), "保存中...");
        try {
            await guard(async () => {
                if (id) {
                    return DietApi.updatePersonalMeal(id, payload);
                }
                return DietApi.createPersonalMeal(payload);
            }, id ? "餐食已更新" : "餐食已创建");
            state.editingMeal = null;
            await ensurePersonalMeals(true);
            renderPersonalMeals();
        } finally {
            restore();
        }
    }
    function mealPayloadFromForm(form) {
        const formData = new FormData(form);
        const payload = {
            name: String(formData.get("name") || "").trim()
        };
        Object.keys(SLOT_LABELS).forEach((key) => {
            payload[key] = formData.getAll(key).filter(Boolean);
        });
        return {
            id: String(formData.get("mealId") || "").trim(),
            payload
        };
    }
    function editMeal(id) {
        const meal = state.personalMeals.find((item) => String(item.id) === String(id));
        if (!meal) {
            showToast("没有找到要编辑的餐食", "error");
            return;
        }
        state.editingMeal = JSON.parse(JSON.stringify(meal));
        renderPersonalMeals();
    }
    async function deleteMeal(id) {
        const meal = state.personalMeals.find((item) => String(item.id) === String(id));
        if (!meal || !window.confirm(`确定删除“${meal.name}”？`)) {
            return;
        }
        await guard(async () => {
            await DietApi.deletePersonalMeal(id);
            await ensurePersonalMeals(true);
            renderPersonalMeals();
        }, "餐食已删除");
    }
    function renderPublicMeals() {
        app.innerHTML = `
            <section class="section">
                <div class="card-title">
                    <div>
                        <h2>公共餐食</h2>
                        <p>系统预置餐食库，只读展示，可在聊天页切换到 PUBLIC 模式体验。</p>
                    </div>
                    <a class="btn primary" href="#/diet/chat">去聊天推荐</a>
                </div>
                <div id="publicMealList">${renderMealList(state.publicMeals, {})}</div>
            </section>
        `;
        ensurePublicMeals();
    }
    async function ensurePublicMeals(force) {
        if (!force && state.publicMeals.length) {
            return;
        }
        try {
            state.publicMeals = await DietApi.listPublicMeals();
            state.home.loaded = false;
            if (currentRoute() === "/diet/meals/public") {
                document.getElementById("publicMealList").innerHTML = renderMealList(state.publicMeals, {});
            }
        } catch (error) {
            showToast(error.message || "公共餐食加载失败", "error");
        }
    }
    function renderTraces() {
        const selected = state.traces.selected;
        app.innerHTML = `
            <section class="split">
                <div class="section">
                    <div class="card-title">
                        <div>
                            <h2>Trace 调试</h2>
                            <p>按时间范围或会话查询请求链路，查看意图修正、槽位和推荐事件。</p>
                        </div>
                    </div>
                    <form id="traceFilterForm" class="form-grid">
                        <div class="field">
                            <label>开始时间</label>
                            <input type="datetime-local" name="startAt" value="${escapeHtml(state.traces.filters.startAt)}" required>
                        </div>
                        <div class="field">
                            <label>结束时间</label>
                            <input type="datetime-local" name="endAt" value="${escapeHtml(state.traces.filters.endAt)}" required>
                        </div>
                        <div class="field">
                            <label>会话 ID（可选）</label>
                            <input name="sessionId" value="${escapeHtml(state.traces.filters.sessionId)}" placeholder="填写后按会话查询">
                        </div>
                        <div class="field">
                            <label>数量上限</label>
                            <input type="number" min="1" max="500" name="limit" value="${escapeHtml(state.traces.filters.limit)}">
                        </div>
                        <div class="field">
                            <label>标注状态</label>
                            <select name="onlyUnlabeled">
                                <option value="false" ${!state.traces.filters.onlyUnlabeled ? "selected" : ""}>全部</option>
                                <option value="true" ${state.traces.filters.onlyUnlabeled ? "selected" : ""}>仅未标注</option>
                            </select>
                        </div>
                        <div class="field">
                            <span>&nbsp;</span>
                            <button class="btn primary" type="submit">${state.traces.loading ? "查询中..." : "查询 Trace"}</button>
                        </div>
                    </form>
                    <div class="subtle-divider"></div>
                    ${renderTraceTable()}
                </div>
                <aside class="section">
                    ${selected ? renderTraceDetail(selected) : `<div class="empty">选择一条 Trace 查看详情和标注表单。</div>`}
                </aside>
            </section>
        `;
    }
    function renderTraceTable() {
        if (!state.traces.rows.length) {
            return `<div class="empty">暂无 Trace 数据。可以先在聊天页发起几轮对话。</div>`;
        }
        return `
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Trace ID</th>
                            <th>会话</th>
                            <th>状态</th>
                            <th>事件</th>
                            <th>耗时</th>
                            <th>创建时间</th>
                            <th>标注</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${state.traces.rows.map((row) => `
                            <tr>
                                <td>${escapeHtml(row.traceId)}</td>
                                <td>${escapeHtml(row.sessionId)}</td>
                                <td>${escapeHtml(row.status || "-")}</td>
                                <td>${escapeHtml(row.eventCount ?? "-")}</td>
                                <td>${row.durationMs ? `${escapeHtml(row.durationMs)} ms` : "-"}</td>
                                <td>${escapeHtml(row.createdAt || "-")}</td>
                                <td>${row.expectedIntent ? `<span class="badge">${escapeHtml(row.expectedIntent)}</span>` : "<span class=\"muted\">未标注</span>"}</td>
                                <td><button class="btn soft" data-action="select-trace" data-trace-id="${escapeHtml(row.traceId)}">查看</button></td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }
    function renderTraceDetail(trace) {
        return `
            <div class="card-title">
                <div>
                    <h3>Trace 详情</h3>
                    <p>${escapeHtml(trace.traceId)}</p>
                </div>
            </div>
            <div class="grid">
                <div>
                    <span class="badge">${escapeHtml(trace.status || "UNKNOWN")}</span>
                    <p class="muted">Session：${escapeHtml(trace.sessionId || "-")} · Events：${escapeHtml(trace.eventCount ?? "-")} · Duration：${escapeHtml(trace.durationMs ?? "-")} ms</p>
                </div>
                <details open>
                    <summary>Trace JSON</summary>
                    <pre class="json-box">${escapeHtml(safeJson(trace.traceJson))}</pre>
                </details>
                <form id="traceLabelForm" class="form-grid">
                    <input type="hidden" name="traceId" value="${escapeHtml(trace.traceId)}">
                    <div class="field">
                        <label>预期意图</label>
                        <select name="expectedIntent">
                            <option value="">不标注</option>
                            ${INTENTS.map((intent) => `<option value="${intent}" ${trace.expectedIntent === intent ? "selected" : ""}>${intent}</option>`).join("")}
                        </select>
                    </div>
                    <div class="field">
                        <label>澄清动作</label>
                        <select name="expectedClarifyAction">
                            <option value="">不标注</option>
                            <option value="ASK" ${trace.expectedClarifyAction === "ASK" ? "selected" : ""}>ASK</option>
                            <option value="READY" ${trace.expectedClarifyAction === "READY" ? "selected" : ""}>READY</option>
                        </select>
                    </div>
                    <div class="field full">
                        <label>预期槽位 JSON</label>
                        <textarea name="expectedSlots" placeholder='{"mealTime":["晚餐"],"taste":["清淡"]}'>${escapeHtml(safeJson(trace.expectedSlots))}</textarea>
                    </div>
                    <div class="field full">
                        <label>备注</label>
                        <textarea name="labelNote" placeholder="标注说明">${escapeHtml(trace.labelNote || "")}</textarea>
                    </div>
                    <div class="field full">
                        <button class="btn primary" type="submit">保存标注</button>
                    </div>
                </form>
            </div>
        `;
    }
    async function searchTraces(form) {
        const formData = new FormData(form);
        state.traces.filters = {
            startAt: formData.get("startAt"),
            endAt: formData.get("endAt"),
            sessionId: formData.get("sessionId").trim(),
            onlyUnlabeled: formData.get("onlyUnlabeled") === "true",
            limit: Number(formData.get("limit") || 50)
        };
        state.traces.loading = true;
        renderTraces();
        try {
            if (state.traces.filters.sessionId) {
                state.traces.rows = await DietApi.listSessionTraces(state.traces.filters.sessionId, state.traces.filters.limit);
            } else {
                state.traces.rows = await DietApi.listTraces({
                    startAt: state.traces.filters.startAt,
                    endAt: state.traces.filters.endAt,
                    onlyUnlabeled: state.traces.filters.onlyUnlabeled,
                    limit: state.traces.filters.limit
                });
            }
            state.traces.selected = state.traces.rows[0] || null;
        } catch (error) {
            showToast(error.message || "Trace 查询失败", "error");
        } finally {
            state.traces.loading = false;
            renderTraces();
        }
    }
    async function selectTrace(traceId) {
        await guard(async () => {
            state.traces.selected = await DietApi.getTrace(traceId);
            renderTraces();
        });
    }
    async function saveTraceLabel(form) {
        const formData = new FormData(form);
        const traceId = formData.get("traceId");
        const slotsText = formData.get("expectedSlots").trim();
        let expectedSlots = null;
        if (slotsText) {
            try {
                expectedSlots = JSON.parse(slotsText);
            } catch (error) {
                showToast("预期槽位必须是合法 JSON", "error");
                return;
            }
        }
        const payload = {
            expectedIntent: formData.get("expectedIntent") || null,
            expectedSlots,
            expectedClarifyAction: formData.get("expectedClarifyAction") || null,
            labelNote: formData.get("labelNote").trim()
        };
        await guard(async () => {
            await DietApi.labelTrace(traceId, payload);
            state.traces.selected = await DietApi.getTrace(traceId);
            const index = state.traces.rows.findIndex((row) => row.traceId === traceId);
            if (index >= 0) {
                state.traces.rows[index] = state.traces.selected;
            }
            renderTraces();
        }, "Trace 标注已保存");
    }
    function renderEvaluations() {
        app.innerHTML = `
            <section class="section">
                <div class="card-title">
                    <div>
                        <h2>评估报告</h2>
                        <p>基于已落库 Trace 生成规则评分、可选 LLM Judge 和反馈归因指标。</p>
                    </div>
                </div>
                <form id="evaluationForm" class="form-grid">
                    <div class="field">
                        <label>开始时间</label>
                        <input type="datetime-local" name="startAt" value="${escapeHtml(state.evaluation.form.startAt)}" required>
                    </div>
                    <div class="field">
                        <label>结束时间</label>
                        <input type="datetime-local" name="endAt" value="${escapeHtml(state.evaluation.form.endAt)}" required>
                    </div>
                    <div class="field">
                        <label>数量上限</label>
                        <input type="number" min="1" max="500" name="limit" value="${escapeHtml(state.evaluation.form.limit)}">
                    </div>
                    <div class="field">
                        <label>LLM Judge</label>
                        <select name="includeLlmJudge">
                            <option value="false" ${!state.evaluation.form.includeLlmJudge ? "selected" : ""}>关闭</option>
                            <option value="true" ${state.evaluation.form.includeLlmJudge ? "selected" : ""}>开启</option>
                        </select>
                    </div>
                    <div class="field full">
                        <button class="btn primary" type="submit">${state.evaluation.loading ? "评估中..." : "生成评估报告"}</button>
                    </div>
                </form>
            </section>
            <section class="section" style="margin-top: 18px;">
                ${renderEvaluationReport()}
            </section>
        `;
    }
    function renderEvaluationReport() {
        const report = state.evaluation.report;
        if (!report) {
            return `<div class="empty">暂无报告。选择时间范围后生成评估。</div>`;
        }
        return `
            <div class="grid three">
                ${statCard("Trace 总数", report.totalTraces, "本次纳入评估的请求数")}
                ${statCard("已标注", report.labeledTraces, "有人工标签的 Trace 数")}
                ${statCard("平均分", report.avgScore === null || report.avgScore === undefined ? "-" : Number(report.avgScore).toFixed(2), "综合评分")}
            </div>
            <div class="subtle-divider"></div>
            <div class="grid two">
                <div>
                    <h3>指标均值</h3>
                    ${renderMetrics(report.metricAverages)}
                </div>
                <div>
                    <h3>报告范围</h3>
                    <p class="muted">${escapeHtml(report.startAt)} 至 ${escapeHtml(report.endAt)}</p>
                </div>
            </div>
            <div class="subtle-divider"></div>
            ${renderEvaluationTable(report.traceResults || [])}
        `;
    }
    function renderMetrics(metrics) {
        const entries = Object.entries(metrics || {});
        if (!entries.length) {
            return `<div class="empty">暂无指标</div>`;
        }
        return `<div class="chips">${entries.map(([key, value]) => `<span class="chip selected">${escapeHtml(key)}：${Number(value).toFixed(2)}</span>`).join("")}</div>`;
    }
    function renderEvaluationTable(rows) {
        if (!rows.length) {
            return `<div class="empty">暂无 Trace 明细</div>`;
        }
        return `
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Trace ID</th>
                            <th>会话</th>
                            <th>综合分</th>
                            <th>规则分</th>
                            <th>LLM 分</th>
                            <th>反馈分</th>
                            <th>指标 / 明细</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rows.map((row) => `
                            <tr>
                                <td>${escapeHtml(row.traceId)}</td>
                                <td>${escapeHtml(row.sessionId)}</td>
                                <td>${formatScore(row.score)}</td>
                                <td>${formatScore(row.ruleScore)}</td>
                                <td>${formatScore(row.llmJudgeScore)}</td>
                                <td>${formatScore(row.userFeedbackScore)}</td>
                                <td>
                                    <details>
                                        <summary>查看 JSON</summary>
                                        <pre class="json-box">${escapeHtml(JSON.stringify({ metrics: row.metrics, detail: row.detail }, null, 2))}</pre>
                                    </details>
                                </td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }
    function formatScore(value) {
        return value === null || value === undefined ? "-" : Number(value).toFixed(2);
    }
    async function runEvaluation(form) {
        const formData = new FormData(form);
        state.evaluation.form = {
            startAt: formData.get("startAt"),
            endAt: formData.get("endAt"),
            limit: Number(formData.get("limit") || 50),
            includeLlmJudge: formData.get("includeLlmJudge") === "true"
        };
        state.evaluation.loading = true;
        renderEvaluations();
        try {
            state.evaluation.report = await DietApi.evaluate(state.evaluation.form);
        } catch (error) {
            showToast(error.message || "评估失败", "error");
        } finally {
            state.evaluation.loading = false;
            renderEvaluations();
        }
    }
    async function saveFeedback(button) {
        await guard(async () => {
            await DietApi.saveFeedback({
                sessionId: button.dataset.sessionId || state.chat.sessionId,
                itemId: Number(button.dataset.itemId),
                action: button.dataset.actionValue,
                rating: button.dataset.actionValue === "DISLIKE" ? 2 : 5,
                reason: ""
            });
        }, "反馈已记录");
    }
    function handleClick(event) {
        const target = event.target.closest("[data-action]");
        if (!target) {
            return;
        }
        const action = target.dataset.action;
        if (action === "set-source") {
            state.chat.sourceMode = target.dataset.source;
            resetChat();
        } else if (action === "new-session") {
            resetChat();
        } else if (action === "quick-message") {
            const input = document.querySelector("#chatForm textarea[name=message]");
            if (input) {
                input.value = target.dataset.message;
                input.focus();
            }
        } else if (action === "feedback") {
            saveFeedback(target);
        } else if (action === "new-meal") {
            state.editingMeal = emptyMeal();
            renderPersonalMeals();
        } else if (action === "edit-meal") {
            editMeal(target.dataset.id);
        } else if (action === "delete-meal") {
            deleteMeal(target.dataset.id);
        } else if (action === "cancel-edit") {
            state.editingMeal = null;
            renderPersonalMeals();
        } else if (action === "select-trace") {
            selectTrace(target.dataset.traceId);
        } else if (action === "open-trace") {
            state.traces.filters.sessionId = "";
            navigate("/admin/traces");
            selectTrace(target.dataset.traceId);
        }
    }
    function handleSubmit(event) {
        const form = event.target;
        if (form.id === "chatForm") {
            event.preventDefault();
            submitChat(form);
        } else if (form.id === "mealForm") {
            event.preventDefault();
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            saveMeal(form);
        } else if (form.id === "traceFilterForm") {
            event.preventDefault();
            searchTraces(form);
        } else if (form.id === "traceLabelForm") {
            event.preventDefault();
            saveTraceLabel(form);
        } else if (form.id === "evaluationForm") {
            event.preventDefault();
            runEvaluation(form);
        }
    }
    function initUserField() {
        userIdInput.value = DietApi.setUserId(DietApi.getUserId());
        userIdInput.addEventListener("change", () => {
            DietApi.setUserId(userIdInput.value);
            state.home.loaded = false;
            state.personalMeals = [];
            state.publicMeals = [];
            state.traces.rows = [];
            state.traces.selected = null;
            resetChat();
            showToast("用户 ID 已切换");
            render();
        });
    }
    window.addEventListener("hashchange", render);
    app.addEventListener("click", handleClick);
    app.addEventListener("submit", handleSubmit);
    initUserField();
    if (!location.hash) {
        navigate("/diet");
    } else {
        render();
    }
})();
