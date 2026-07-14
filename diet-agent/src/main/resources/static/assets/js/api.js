(function () {
    "use strict";

    const API_BASE = "/api/v1/diet";
    const USER_ID_KEY = "diet.userId";

    function getUserId() {
        return localStorage.getItem(USER_ID_KEY) || "1";
    }

    function setUserId(userId) {
        const normalized = String(userId || "1").trim() || "1";
        localStorage.setItem(USER_ID_KEY, normalized);
        return normalized;
    }

    async function request(path, options) {
        const config = options || {};
        const headers = new Headers(config.headers || {});
        headers.set("X-User-Id", getUserId());

        if (config.body !== undefined && !(config.body instanceof FormData)) {
            headers.set("Content-Type", "application/json");
        }

        const response = await fetch(`${API_BASE}${path}`, {
            ...config,
            headers,
            body: config.body === undefined || config.body instanceof FormData
                ? config.body
                : JSON.stringify(config.body)
        });

        if (!response.ok) {
            const detail = await readError(response);
            throw new Error(detail || `请求失败：${response.status}`);
        }

        if (response.status === 204) {
            return null;
        }

        const text = await response.text();
        if (!text) {
            return null;
        }

        try {
            return JSON.parse(text);
        } catch (error) {
            return text;
        }
    }

    async function readError(response) {
        const text = await response.text();
        if (!text) {
            return "";
        }

        try {
            const payload = JSON.parse(text);
            return payload.message || payload.error || text;
        } catch (error) {
            return text;
        }
    }

    function toQuery(params) {
        const search = new URLSearchParams();
        Object.entries(params || {}).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== "") {
                search.set(key, value);
            }
        });
        const query = search.toString();
        return query ? `?${query}` : "";
    }

    window.DietApi = {
        getUserId,
        setUserId,
        createSession: () => request("/sessions", { method: "POST" }),
        chat: (payload) => request("/chat", { method: "POST", body: payload }),
        listPersonalMeals: () => request("/meals/personal"),
        createPersonalMeal: (payload) => request("/meals/personal", { method: "POST", body: payload }),
        updatePersonalMeal: (mealId, payload) => request(`/meals/personal/${encodeURIComponent(mealId)}`, { method: "PUT", body: payload }),
        deletePersonalMeal: (mealId) => request(`/meals/personal/${encodeURIComponent(mealId)}`, { method: "DELETE" }),
        listPublicMeals: () => request("/meals/public"),
        slotOptions: () => request("/slot-options"),
        saveFeedback: (payload) => request("/feedback", { method: "POST", body: payload }),
        listTraces: (params) => request(`/debug/traces${toQuery(params)}`),
        getTrace: (traceId) => request(`/debug/traces/${encodeURIComponent(traceId)}`),
        listSessionTraces: (sessionId, limit) => request(`/debug/sessions/${encodeURIComponent(sessionId)}/traces${toQuery({ limit })}`),
        labelTrace: (traceId, payload) => request(`/debug/traces/${encodeURIComponent(traceId)}/label`, { method: "PUT", body: payload }),
        evaluate: (payload) => request("/evaluations", { method: "POST", body: payload })
    };
})();


