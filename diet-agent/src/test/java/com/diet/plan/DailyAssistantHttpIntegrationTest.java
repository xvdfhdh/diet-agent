package com.diet.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:diet_daily_assistant;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.sql.init.mode=always", "spring.sql.init.encoding=UTF-8",
        "spring.sql.init.schema-locations=classpath:auth-test-schema.sql",
        "diet.auth.admin-username=plan_admin", "diet.auth.admin-password=plan-admin-password"
})
@AutoConfigureMockMvc
class DailyAssistantHttpIntegrationTest {
    private static final String API = "/api/v1/diet";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("diet.auth.jwt-secret-base64", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    void planSnapshotShoppingCheckinAndUserIsolationFormAClosedLoop() throws Exception {
        String user1 = register("planner_one");
        String user2 = register("planner_two");
        String admin = login("plan_admin", "plan-admin-password");
        String mealBody = """
                {"name":"番茄鸡蛋饭","mealTime":["午餐"],"taste":["清淡"],
                "acquisitionMode":"COOK","defaultServings":1,"priceMin":12,
                "ingredients":[{"name":"番茄","category":"蔬菜","quantity":2,"unit":"个"},
                {"name":"鸡蛋","category":"肉蛋奶","quantity":2,"unit":"个"}],
                "steps":["切番茄","炒鸡蛋"]}
                """;
        long mealId = json.readTree(mvc.perform(post(API + "/meals/public").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(mealBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ingredients[0].name").value("番茄"))
                .andReturn().getResponse().getContentAsString()).path("id").asLong();

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String planBody = "{\"planDate\":\"" + monday + "\",\"mealPeriod\":\"LUNCH\",\"mealId\":" + mealId
                + ",\"acquisitionMode\":\"COOK\",\"servings\":2}";
        JsonNode plan = json.readTree(mvc.perform(post(API + "/plans/items").header("Authorization", bearer(user1))
                        .contentType(MediaType.APPLICATION_JSON).content(planBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meal.name").value("番茄鸡蛋饭"))
                .andReturn().getResponse().getContentAsString());
        long planId = plan.path("id").asLong();

        mvc.perform(put(API + "/meals/public/" + mealId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(mealBody.replace("番茄鸡蛋饭", "公共库新名称")))
                .andExpect(status().isOk());
        mvc.perform(get(API + "/plans").param("weekStart", monday.toString()).header("Authorization", bearer(user1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].meal.name").value("番茄鸡蛋饭"));

        mvc.perform(get(API + "/plans").param("weekStart", monday.toString()).header("Authorization", bearer(user2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete(API + "/plans/items/" + planId).header("Authorization", bearer(user2)))
                .andExpect(status().isBadRequest());

        mvc.perform(post(API + "/shopping-lists/items").param("weekStart", monday.toString())
                        .header("Authorization", bearer(user1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"厨房纸\",\"category\":\"其他\",\"quantity\":1,\"unit\":\"卷\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.manual").value(true));
        mvc.perform(post(API + "/shopping-lists/sync").param("weekStart", monday.toString())
                        .header("Authorization", bearer(user1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.needsSync").value(false))
                .andExpect(jsonPath("$.items[?(@.name == '番茄')].quantity").value(4.0))
                .andExpect(jsonPath("$.items[?(@.name == '厨房纸')].manual").value(true));

        String alternativeBody = """
                {"name":"鸡肉蔬菜便当","mealTime":["午餐"],"taste":["清淡"],
                 "acquisitionMode":"COOK","defaultServings":1,"ingredients":[],"steps":["煎鸡肉"]}
                """;
        long alternativeId = json.readTree(mvc.perform(post(API + "/meals/public")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(alternativeBody)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(put(API + "/plans/items/" + planId).header("Authorization", bearer(user1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mealId\":" + alternativeId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meal.name").value("鸡肉蔬菜便当"))
                .andExpect(jsonPath("$.status").value("REPLACED"));

        mvc.perform(delete(API + "/meals/public/" + mealId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(post(API + "/plans/items/" + planId + "/check-in").header("Authorization", bearer(user1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"satiety\":4,\"actualSpent\":18.5,\"reasonCode\":\"GOOD_VALUE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get(API + "/plans/weekly-summary").param("weekStart", monday.toString())
                        .header("Authorization", bearer(user1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.actualCost").value(18.5));
    }

    private String register(String username) throws Exception {
        String body = mvc.perform(post(API + "/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"safe-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("token").asText();
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("token").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
