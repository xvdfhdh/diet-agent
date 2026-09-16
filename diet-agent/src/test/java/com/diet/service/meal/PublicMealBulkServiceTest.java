package com.diet.service.meal;

import com.diet.exception.DietException;
import com.diet.mapper.MealMapper;
import com.diet.model.MealBulkRequest;
import com.diet.model.MealBulkUpdate;
import com.diet.model.MealItemRow;
import com.diet.model.MealRequest;
import com.diet.service.slot.SlotOptionService;
import com.diet.util.JsonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicMealBulkServiceTest {
    private MealMapper mapper;
    private MealService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MealMapper.class);
        service = new MealService(mapper, mock(SlotOptionService.class), new JsonService(new ObjectMapper()));
    }

    @Test
    void rejectsDuplicateAndConflictingIdsBeforeWriting() {
        MealRequest meal = draft("午餐");
        assertThatThrownBy(() -> service.bulkPublicMeals(new MealBulkRequest(
                List.of(), List.of(new MealBulkUpdate(3L, meal)), List.of(3L))))
                .isInstanceOf(DietException.class);
        org.mockito.Mockito.verifyNoInteractions(mapper);
    }

    @Test
    void acceptsBatchCreateUpdateDeleteAndLimitsSize() {
        when(mapper.insert(any(MealItemRow.class))).thenReturn(1);
        when(mapper.updatePublic(any(MealItemRow.class))).thenReturn(1);
        when(mapper.deletePublic(5L)).thenReturn(1);
        var result = service.bulkPublicMeals(new MealBulkRequest(
                List.of(draft("午餐")), List.of(new MealBulkUpdate(4L, draft("晚餐"))), List.of(5L)));
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(1);
        verify(mapper).insert(any(MealItemRow.class));
        verify(mapper).updatePublic(any(MealItemRow.class));
        verify(mapper).deletePublic(5L);

        assertThatThrownBy(() -> service.bulkPublicMeals(new MealBulkRequest(
                java.util.Collections.nCopies(101, draft("午餐")), List.of(), List.of())))
                .isInstanceOf(DietException.class);
    }

    @Test
    void failureAfterEarlierWriteSignalsTransactionalRollback() {
        when(mapper.insert(any(MealItemRow.class))).thenReturn(1);
        when(mapper.updatePublic(any(MealItemRow.class))).thenReturn(0);
        RecordingTransactionManager manager = new RecordingTransactionManager();
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        MealService transactionalService = (MealService) proxy.getProxy();

        assertThatThrownBy(() -> transactionalService.bulkPublicMeals(new MealBulkRequest(
                List.of(draft("午餐")), List.of(new MealBulkUpdate(999L, draft("晚餐"))), List.of())))
                .isInstanceOf(DietException.class);
        assertThat(manager.rolledBack).isTrue();
        assertThat(manager.committed).isFalse();
    }

    private MealRequest draft(String mealTime) {
        return new MealRequest("测试餐食", null, List.of(mealTime), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private boolean rolledBack;
        private boolean committed;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }
}
