package com.diet.service.agentic;

import com.diet.enums.AgentTaskType;
import com.diet.enums.PendingActionStatus;
import com.diet.mapper.PendingAgentActionMapper;
import com.diet.model.*;
import com.diet.service.plan.MealPlanService;
import com.diet.service.shopping.ShoppingListService;
import com.diet.service.favorite.FavoriteMealService;
import com.diet.service.memory.UserMemoryService;
import com.diet.util.JsonService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PendingAgentActionService {
    private static final int EXPIRY_MINUTES = 10;
    private final PendingAgentActionMapper mapper;
    private final AgentMutationCommitService commitService;
    private final MealPlanService plans;
    private final ShoppingListService shopping;
    private final FavoriteMealService favorites;
    private final UserMemoryService memories;
    private final JsonService json;

    public PendingAgentActionService(PendingAgentActionMapper mapper, AgentMutationCommitService commitService,
                                     MealPlanService plans, ShoppingListService shopping,
                                     FavoriteMealService favorites, UserMemoryService memories, JsonService json) {
        this.mapper = mapper;
        this.commitService = commitService;
        this.plans = plans;
        this.shopping = shopping;
        this.favorites = favorites;
        this.memories = memories;
        this.json = json;
    }

    @Transactional
    public AgentActionPreview create(Long userId, String sessionId, AgentTaskType taskType,
                                     List<AgentMutation> mutations, String summary,
                                     List<AgentActionChange> changes) {
        List<AgentMutation> safe = mutations == null ? List.of() : List.copyOf(mutations);
        if (safe.isEmpty()) throw new IllegalArgumentException("待确认操作不能为空");
        String id = "act_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRY_MINUTES);
        AgentActionPreview preview = new AgentActionPreview(id, summary, changes, expiresAt, true);
        PendingAgentActionRow row = new PendingAgentActionRow();
        row.setId(id); row.setUserId(userId); row.setSessionId(sessionId); row.setTaskType(taskType.name());
        row.setActionJson(json.toJson(safe)); row.setPreviewJson(json.toJson(preview));
        row.setPreconditionHash(precondition(userId, safe)); row.setStatus(PendingActionStatus.PENDING.name());
        row.setExpiresAt(expiresAt);
        mapper.insert(row);
        return preview;
    }

    @Transactional
    public AgentActionResponse confirm(Long userId, String id) {
        PendingAgentActionRow row = requireOwned(userId, id);
        PendingActionStatus status = PendingActionStatus.valueOf(row.getStatus());
        if (status == PendingActionStatus.CONFIRMED)
            return new AgentActionResponse(id, status, "该操作已经执行，无需重复确认", domains(actions(row)));
        if (status != PendingActionStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该操作已取消或失效，请重新发起");
        if (row.getExpiresAt().isBefore(LocalDateTime.now())) {
            mapper.expireOwned(id, userId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "操作预览已过期，请重新生成");
        }
        List<AgentMutation> actions = actions(row);
        if (!Objects.equals(row.getPreconditionHash(), precondition(userId, actions)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相关计划或清单已经变化，请重新生成预览");
        commitService.commit(userId, actions);
        if (mapper.updateStatus(id, userId, PendingActionStatus.PENDING.name(), PendingActionStatus.CONFIRMED.name()) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "操作状态已变化，请刷新后重试");
        return new AgentActionResponse(id, PendingActionStatus.CONFIRMED, "已确认并完成操作", domains(actions));
    }

    @Transactional
    public AgentActionResponse cancel(Long userId, String id) {
        PendingAgentActionRow row = requireOwned(userId, id);
        PendingActionStatus status = PendingActionStatus.valueOf(row.getStatus());
        if (status == PendingActionStatus.CANCELLED)
            return new AgentActionResponse(id, status, "该操作已经取消", domains(actions(row)));
        if (status != PendingActionStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该操作已执行或失效，不能取消");
        mapper.updateStatus(id, userId, PendingActionStatus.PENDING.name(), PendingActionStatus.CANCELLED.name());
        return new AgentActionResponse(id, PendingActionStatus.CANCELLED, "已取消，本次没有修改数据", domains(actions(row)));
    }

    public PendingAgentActionRow latest(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        return mapper.findLatestPending(sessionId, userId);
    }

    public AgentActionPreview preview(PendingAgentActionRow row) {
        return row == null ? null : json.fromJson(row.getPreviewJson(), AgentActionPreview.class);
    }

    private PendingAgentActionRow requireOwned(Long userId, String id) {
        PendingAgentActionRow row = mapper.findOwned(id, userId);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待确认操作不存在");
        return row;
    }

    private List<AgentMutation> actions(PendingAgentActionRow row) {
        return json.fromJsonList(row.getActionJson(), AgentMutation.class);
    }

    private String precondition(Long userId, List<AgentMutation> actions) {
        List<Object> state = new ArrayList<>();
        for (AgentMutation action : actions) {
            if (action instanceof AgentMutation.AddPlanItem value) state.add(plans.findWeek(userId, value.planDate()));
            else if (action instanceof AgentMutation.ReplacePlanItem value) state.add(plans.findOwned(userId, value.planId()));
            else if (action instanceof AgentMutation.SyncShoppingList value) state.add(shopping.get(userId, value.weekStart()));
            else if (action instanceof AgentMutation.CheckInPlanItem value) state.add(plans.findOwned(userId, value.planId()));
            else if (action instanceof AgentMutation.SetFavorite) state.add(favorites.list(userId, 100));
            else if (action instanceof AgentMutation.AddShoppingItem value) state.add(shopping.get(userId, value.weekStart()));
            else if (action instanceof AgentMutation.UpdateShoppingItem value) state.add(shopping.findOwnedItem(userId, value.itemId()));
            else if (action instanceof AgentMutation.DeleteShoppingItem value) state.add(shopping.findOwnedItem(userId, value.itemId()));
            else if (action instanceof AgentMutation.ReplacePreferences) state.add(memories.preferences(userId));
        }
        return sha256(json.toJson(state));
    }

    private List<String> domains(List<AgentMutation> actions) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (AgentMutation action : actions) {
            if (action instanceof AgentMutation.SyncShoppingList || action instanceof AgentMutation.AddShoppingItem
                    || action instanceof AgentMutation.UpdateShoppingItem || action instanceof AgentMutation.DeleteShoppingItem)
                values.add("SHOPPING");
            else if (action instanceof AgentMutation.CheckInPlanItem) values.add("CHECKIN");
            else if (action instanceof AgentMutation.SetFavorite) values.add("FAVORITE");
            else if (action instanceof AgentMutation.ReplacePreferences) values.add("PREFERENCE");
            else values.add("PLAN");
        }
        return List.copyOf(values);
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception error) { throw new IllegalStateException("无法计算操作版本", error); }
    }
}
