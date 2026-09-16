package com.diet.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:diet_auth_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.encoding=UTF-8",
        "spring.sql.init.schema-locations=classpath:auth-test-schema.sql",
        "diet.auth.admin-username=qa_admin",
        "diet.auth.admin-password=qa-admin-password"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthHttpFlowIntegrationTest {
    private static final String API = "/api/v1/diet";
    private static final String BATCH = "{\"creates\":[{\"name\":\"测试餐食\",\"mealTime\":[\"午餐\"]}],\"updates\":[],\"deleteIds\":[]}";

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("diet.auth.jwt-secret-base64", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;

    @Test
    void registrationLoginLogoutAndAdminOnlyBatchWorkThroughRealMvcAndUserMapper() throws Exception {
        String registration = mvc.perform(post(API + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"qa_user\",\"password\":\"qa-user-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        String userToken = token(registration);

        mvc.perform(get(API + "/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("qa_user"));
        mvc.perform(post(API + "/meals/public/batch").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(BATCH))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(API + "/meals/public/batch").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(BATCH))
                .andExpect(status().isForbidden());

        mvc.perform(post(API + "/auth/logout").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        mvc.perform(get(API + "/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
        String userLogin = mvc.perform(post(API + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"qa_user\",\"password\":\"qa-user-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(token(userLogin)).isNotBlank();

        String adminLogin = mvc.perform(post(API + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"qa_admin\",\"password\":\"qa-admin-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post(API + "/meals/public/batch").header("Authorization", "Bearer " + token(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON).content(BATCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
        assertThat(database.queryForObject("SELECT COUNT(*) FROM meal_item WHERE source_type = 'PUBLIC'", Integer.class))
                .isEqualTo(1);

        long createdId = database.queryForObject("SELECT id FROM meal_item WHERE name = '测试餐食'", Long.class);
        String update = "{\"creates\":[],\"updates\":[{\"id\":" + createdId
                + ",\"meal\":{\"name\":\"改好的餐食\",\"mealTime\":[\"晚餐\"],\"taste\":[\"清淡\"]}}],\"deleteIds\":[]}";
        mvc.perform(post(API + "/meals/public/batch").header("Authorization", "Bearer " + token(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get(API + "/meals/public").header("Authorization", "Bearer " + token(userLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("改好的餐食"));

        String failed = "{\"creates\":[{\"name\":\"应回滚餐食\",\"mealTime\":[\"午餐\"]}],"
                + "\"updates\":[{\"id\":99999,\"meal\":{\"name\":\"不存在\",\"mealTime\":[\"午餐\"]}}],\"deleteIds\":[]}";
        mvc.perform(post(API + "/meals/public/batch").header("Authorization", "Bearer " + token(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON).content(failed))
                .andExpect(status().isBadRequest());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM meal_item WHERE name = '应回滚餐食'", Integer.class))
                .isZero();

        String delete = "{\"creates\":[],\"updates\":[],\"deleteIds\":[" + createdId + "]}";
        mvc.perform(post(API + "/meals/public/batch").header("Authorization", "Bearer " + token(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON).content(delete))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1));
        assertThat(database.queryForObject("SELECT COUNT(*) FROM meal_item WHERE source_type = 'PUBLIC'", Integer.class))
                .isZero();

        String single = "{\"name\":\"单条餐食\",\"mealTime\":[\"午餐\"]}";
        mvc.perform(post(API + "/meals/public").header("Authorization", "Bearer " + token(userLogin))
                        .contentType(MediaType.APPLICATION_JSON).content(single))
                .andExpect(status().isForbidden());
        String created = mvc.perform(post(API + "/meals/public")
                        .header("Authorization", "Bearer " + token(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON).content(single))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("PUBLIC"))
                .andReturn().getResponse().getContentAsString();
        long singleId = json.readTree(created).path("id").asLong();
        assertThat(singleId).isPositive();

        mvc.perform(put(API + "/meals/public/" + singleId)
                        .header("Authorization", "Bearer " + token(userLogin))
                        .contentType(MediaType.APPLICATION_JSON).content(single))
                .andExpect(status().isForbidden());
        mvc.perform(put(API + "/meals/public/" + singleId)
                        .header("Authorization", "Bearer " + token(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"单条已修改\",\"mealTime\":[\"晚餐\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("单条已修改"));
        mvc.perform(delete(API + "/meals/public/" + singleId)
                        .header("Authorization", "Bearer " + token(userLogin)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(API + "/meals/public/" + singleId)
                        .header("Authorization", "Bearer " + token(adminLogin)))
                .andExpect(status().isOk());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM meal_item WHERE source_type = 'PUBLIC'", Integer.class))
                .isZero();
    }

    private String token(String body) throws Exception {
        JsonNode parsed = json.readTree(body);
        return parsed.path("token").asText();
    }
}
