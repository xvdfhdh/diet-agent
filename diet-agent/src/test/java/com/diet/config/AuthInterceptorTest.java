package com.diet.config;

import com.diet.constants.DietConstants;
import com.diet.controller.meal.MealController;
import com.diet.model.DietUserRow;
import com.diet.model.MealBulkResponse;
import com.diet.model.MealRequest;
import com.diet.service.auth.AuthService;
import com.diet.service.meal.MealService;
import com.diet.service.meal.MealAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {
    private AuthService authService;
    private AuthInterceptor interceptor;
    private HandlerMethod publicCreate;

    @BeforeEach
    void setUp() throws Exception {
        authService = mock(AuthService.class);
        interceptor = new AuthInterceptor(authService, new ObjectMapper());
        publicCreate = new HandlerMethod(new MealController(null, null),
                MealController.class.getMethod("createPublic", MealRequest.class));
    }

    @Test
    void ignoresForgedLegacyUserHeaderWithoutBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/diet/meals/public");
        request.addHeader("X-User-Id", "1000");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, publicCreate)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute(DietConstants.AUTH_USER_ID)).isNull();
    }

    @Test
    void ordinaryUserCannotWritePublicLibrary() throws Exception {
        DietUserRow user = user("USER");
        when(authService.authenticatedUser("token")).thenReturn(user);
        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, publicCreate)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void adminMayWritePublicLibraryWithVerifiedIdentity() throws Exception {
        DietUserRow admin = user("ADMIN");
        when(authService.authenticatedUser("token")).thenReturn(admin);
        MockHttpServletRequest request = bearerRequest();
        request.addHeader("X-User-Id", "42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, publicCreate)).isTrue();
        assertThat(request.getAttribute(DietConstants.AUTH_USER_ID)).isEqualTo(1000L);
        assertThat(request.getAttribute(DietConstants.AUTH_ROLE)).isEqualTo("ADMIN");
    }

    @Test
    void batchEndpointEnforcesAdminRoleThroughMvcHandlerMapping() throws Exception {
        MealService mealService = mock(MealService.class);
        when(mealService.bulkPublicMeals(any())).thenReturn(new MealBulkResponse(1, 0, 0));
        var mvc = MockMvcBuilders.standaloneSetup(new MealController(mealService, mock(MealAiService.class)))
                .addInterceptors(interceptor).build();
        String batch = "{\"creates\":[{\"name\":\"测试餐食\",\"mealTime\":[\"午餐\"]}],\"updates\":[],\"deleteIds\":[]}";
        mvc.perform(post("/api/v1/diet/meals/public/batch").contentType("application/json").content(batch))
                .andExpect(status().isUnauthorized());
        when(authService.authenticatedUser("token")).thenReturn(user("USER"));
        mvc.perform(post("/api/v1/diet/meals/public/batch").header("Authorization", "Bearer token")
                        .contentType("application/json").content(batch))
                .andExpect(status().isForbidden());
        when(authService.authenticatedUser("token")).thenReturn(user("ADMIN"));
        mvc.perform(post("/api/v1/diet/meals/public/batch").header("Authorization", "Bearer token")
                        .contentType("application/json").content(batch))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/diet/meals/public");
        request.addHeader("Authorization", "Bearer token");
        return request;
    }

    private DietUserRow user(String role) {
        DietUserRow user = new DietUserRow();
        user.setId(1000L);
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }
}
