package com.diet.service.auth;

import com.diet.mapper.DietUserMapper;
import com.diet.model.AuthCredentials;
import com.diet.model.AuthResponse;
import com.diet.model.AuthUserResponse;
import com.diet.model.DietUserRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {
    private final DietUserMapper mapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public AuthService(DietUserMapper mapper, JwtService jwtService) {
        this.mapper = mapper;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(AuthCredentials credentials) {
        String username = normalizedUsername(credentials);
        String password = validPassword(credentials);
        if (mapper.findByUsername(username) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        DietUserRow user = new DietUserRow();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole("USER");
        try {
            mapper.insert(user);
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在", error);
        }
        return new AuthResponse(jwtService.issue(user), AuthUserResponse.from(user));
    }

    public AuthResponse login(AuthCredentials credentials) {
        String username = normalizedUsername(credentials);
        DietUserRow user = mapper.findByUsername(username);
        String password = credentials == null ? null : credentials.password();
        if (user == null || !user.isEnabled() || password == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return new AuthResponse(jwtService.issue(user), AuthUserResponse.from(user));
    }

    public AuthUserResponse me(Long userId) {
        DietUserRow user = mapper.findById(userId);
        if (user == null || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账户不存在或已停用");
        }
        return AuthUserResponse.from(user);
    }

    @Transactional
    public void logout(Long userId) {
        mapper.incrementTokenVersion(userId);
    }

    public DietUserRow authenticatedUser(String token) {
        JwtService.TokenIdentity identity;
        try {
            identity = jwtService.verify(token);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        DietUserRow user = mapper.findById(identity.userId());
        if (user == null || !user.isEnabled() || user.getTokenVersion() != identity.tokenVersion()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        return user;
    }

    public void bootstrapAdmin(String usernameRaw, String passwordRaw) {
        if ((usernameRaw == null || usernameRaw.isBlank()) && (passwordRaw == null || passwordRaw.isBlank())) {
            return;
        }
        AuthCredentials credentials = new AuthCredentials(usernameRaw, passwordRaw);
        String username = normalizedUsername(credentials);
        String password = validPassword(credentials);
        if (mapper.countEnabledAdmins() != 0) {
            return;
        }
        if (mapper.findByUsername(username) != null) {
            throw new IllegalStateException("管理员初始化用户名已被普通用户占用，请选择其他名称");
        }
        DietUserRow admin = new DietUserRow();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole("ADMIN");
        mapper.insert(admin);
    }

    public void verifySchema() {
        try {
            mapper.findByUsername("__diet_auth_schema_check__");
        } catch (BadSqlGrammarException error) {
            throw new IllegalStateException("登录系统数据库结构不可用：请先执行 db/migrations/20260917_user_auth.sql", error);
        }
    }

    private String normalizedUsername(AuthCredentials credentials) {
        String raw = credentials == null ? null : credentials.username();
        String username = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9_]{3,32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名需为 3~32 位小写字母、数字或下划线");
        }
        return username;
    }

    private String validPassword(AuthCredentials credentials) {
        String password = credentials == null ? null : credentials.password();
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码需为 8 位以上且不超过 72 字节");
        }
        return password;
    }
}
