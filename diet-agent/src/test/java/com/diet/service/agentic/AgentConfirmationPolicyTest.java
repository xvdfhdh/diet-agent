package com.diet.service.agentic;

import com.diet.enums.AcquisitionMode;
import com.diet.enums.MealPeriod;
import com.diet.service.plan.MealPlanService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentConfirmationPolicyTest {
    private final MealPlanService plans = mock(MealPlanService.class);
    private final AgentConfirmationPolicy policy = new AgentConfirmationPolicy(plans);

    @Test
    void directSingleEmptySlotButPreviewBatchAndOverwrite() {
        LocalDate today = LocalDate.now();
        AgentMutation.AddPlanItem breakfast = new AgentMutation.AddPlanItem(today, MealPeriod.BREAKFAST, 1L,
                AcquisitionMode.COOK, 1);
        when(plans.findWeek(7L, today)).thenReturn(List.of());
        assertThat(policy.requiresConfirmation(7L, List.of(breakfast))).isFalse();
        assertThat(policy.requiresConfirmation(7L, List.of(breakfast,
                new AgentMutation.AddPlanItem(today, MealPeriod.LUNCH, 2L, AcquisitionMode.COOK, 1)))).isTrue();
    }

    @Test
    void destructiveOperationsAlwaysRequireConfirmation() {
        assertThat(policy.requiresConfirmation(7L,
                List.of(new AgentMutation.SyncShoppingList(LocalDate.now())))).isTrue();
        assertThat(policy.requiresConfirmation(7L,
                List.of(new AgentMutation.DeleteShoppingItem(9L)))).isTrue();
    }
}
