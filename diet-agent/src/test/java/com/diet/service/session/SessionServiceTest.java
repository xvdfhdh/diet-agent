package com.diet.service.session;

import com.diet.enums.SourceMode;
import com.diet.exception.DietException;
import com.diet.mapper.SessionMapper;
import com.diet.model.SessionMessageRow;
import com.diet.model.SessionRow;
import com.diet.util.JsonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SessionServiceTest {
    @Test
    void listsOnlyOwnedSessionsAndReturnsMessagesInChronologicalOrder() {
        SessionMapper mapper = mock(SessionMapper.class);
        SessionService service = new SessionService(mapper, new JsonService(new ObjectMapper()), 10);
        SessionRow session = new SessionRow();
        session.setId("session-1"); session.setUserId(7L); session.setPhase("RECOMMEND");
        session.setSlots("{\"_meta\":{\"sourceMode\":\"PERSONAL\"}}");
        session.setCreatedAt(LocalDateTime.now().minusMinutes(2)); session.setUpdatedAt(LocalDateTime.now());
        SessionMessageRow assistant = message(2L, "assistant", "推荐番茄鸡蛋面");
        SessionMessageRow user = message(1L, "user", "想吃清淡午餐");
        when(mapper.listRecentSessions(7L, 20)).thenReturn(List.of(session));
        when(mapper.listRecentMessages("session-1", 7L, 1)).thenReturn(new ArrayList<>(List.of(assistant)));
        when(mapper.countMessages("session-1", 7L)).thenReturn(2);
        when(mapper.findById("session-1", 7L)).thenReturn(session);
        when(mapper.listRecentMessages("session-1", 7L, 200)).thenReturn(new ArrayList<>(List.of(assistant, user)));

        var summaries = service.recentSessions(7L, null);
        var messages = service.messages(7L, "session-1", null);

        assertThat(summaries).singleElement().satisfies(value -> {
            assertThat(value.sourceMode()).isEqualTo(SourceMode.PERSONAL);
            assertThat(value.preview()).isEqualTo("推荐番茄鸡蛋面");
            assertThat(value.messageCount()).isEqualTo(2);
        });
        assertThat(messages).extracting(value -> value.role()).containsExactly("user", "assistant");
    }

    @Test
    void rejectsReadingAnotherUsersSession() {
        SessionMapper mapper = mock(SessionMapper.class);
        SessionService service = new SessionService(mapper, new JsonService(new ObjectMapper()), 10);
        assertThatThrownBy(() -> service.messages(8L, "session-1", 100))
                .isInstanceOf(DietException.class).hasMessageContaining("无权访问");
    }

    private SessionMessageRow message(Long id, String role, String content) {
        SessionMessageRow row = new SessionMessageRow();
        row.setId(id); row.setSessionId("session-1"); row.setRole(role); row.setContent(content);
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }
}
