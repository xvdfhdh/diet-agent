package com.diet.service.auth;

import com.diet.mapper.DietUserMapper;
import com.diet.model.AuthCredentials;
import com.diet.model.DietUserRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private DietUserMapper mapper;
    private JwtService jwt;
    private AuthService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DietUserMapper.class);
        jwt = new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 24);
        service = new AuthService(mapper, jwt);
    }

    @Test
    void registrationAlwaysCreatesOrdinaryUserWithHashedPassword() {
        when(mapper.insert(any())).thenAnswer(call -> {
            DietUserRow row = call.getArgument(0);
            row.setId(1000L);
            return 1;
        });

        var result = service.register(new AuthCredentials(" Alice_1 ", "long-secret-password"));

        assertThat(result.user().username()).isEqualTo("alice_1");
        assertThat(result.user().role()).isEqualTo("USER");
        assertThat(jwt.verify(result.token()).userId()).isEqualTo(1000L);
        org.mockito.ArgumentCaptor<DietUserRow> capture = org.mockito.ArgumentCaptor.forClass(DietUserRow.class);
        verify(mapper).insert(capture.capture());
        assertThat(capture.getValue().getPasswordHash()).startsWith("$2a$").isNotEqualTo("long-secret-password");
        assertThat(capture.getValue().getRole()).isEqualTo("USER");
    }

    @Test
    void loginRejectsWrongPasswordAndDisabledAccount() {
        DietUserRow user = new DietUserRow();
        user.setId(1000L);
        user.setUsername("alice");
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode("correct-password"));
        user.setEnabled(true);
        when(mapper.findByUsername("alice")).thenReturn(user);

        assertThatThrownBy(() -> service.login(new AuthCredentials("alice", "wrong-password")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.UNAUTHORIZED);
        user.setEnabled(false);
        assertThatThrownBy(() -> service.login(new AuthCredentials("alice", "wrong-password")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validLoginIssuesTokenAndLogoutAdvancesTokenVersion() {
        DietUserRow user = new DietUserRow();
        user.setId(1002L);
        user.setUsername("alice");
        user.setRole("USER");
        user.setEnabled(true);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode("correct-password"));
        when(mapper.findByUsername("alice")).thenReturn(user);

        var response = service.login(new AuthCredentials("ALICE", "correct-password"));

        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(jwt.verify(response.token()).userId()).isEqualTo(1002L);
        service.logout(1002L);
        verify(mapper).incrementTokenVersion(1002L);
    }

    @Test
    void tokenRequiresCurrentDatabaseVersionAndAccountState() {
        DietUserRow user = new DietUserRow();
        user.setId(1001L);
        user.setUsername("admin");
        user.setRole("ADMIN");
        user.setEnabled(true);
        when(mapper.findById(1001L)).thenReturn(user);
        String token = jwt.issue(user);

        assertThat(service.authenticatedUser(token).getRole()).isEqualTo("ADMIN");
        user.setTokenVersion(1);
        assertThatThrownBy(() -> service.authenticatedUser(token))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.UNAUTHORIZED);
        user.setTokenVersion(0);
        user.setEnabled(false);
        assertThatThrownBy(() -> service.authenticatedUser(token))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsTamperedTokenAndWeakSecret() {
        DietUserRow user = new DietUserRow();
        user.setId(1001L);
        String token = jwt.issue(user);
        String altered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> service.authenticatedUser(altered)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> new JwtService(Base64.getEncoder().encodeToString(new byte[16]), 24))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupSchemaCheckExplainsMissingUserTable() {
        when(mapper.findByUsername("__diet_auth_schema_check__"))
                .thenThrow(new BadSqlGrammarException("query", "SELECT FROM diet_user", new SQLException("missing table")));
        assertThatThrownBy(service::verifySchema)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("20260917_user_auth.sql");
    }
}
