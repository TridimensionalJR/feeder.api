package com.threedimensional.feeder.controller;

import com.threedimensional.feeder.config.JwtConfig;
import com.threedimensional.feeder.config.SecurityConfig;
import com.threedimensional.feeder.exception.DuplicateFeederConfigException;
import com.threedimensional.feeder.exception.FeederConfigNotFoundException;
import com.threedimensional.feeder.exception.FeederNotFoundException;
import com.threedimensional.feeder.exception.InvalidFeederDataException;
import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.User;
import com.threedimensional.feeder.model.enums.ConfigType;
import com.threedimensional.feeder.service.FeederConfigService;
import com.threedimensional.feeder.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of {@link FeederConfigController}: route authorization, status codes, Location
 * headers, error mapping, and ensuring owner scope is derived strictly from the authentication token.
 */
@WebMvcTest(FeederConfigController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class})
class FeederConfigControllerTests {

	private static final UUID USER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
	private static final UUID FEEDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID CONFIG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID DEVICE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-01-02T04:05:06Z");

	private static final String CREATE_INTERVAL_BODY =
			"{\"type\":\"INTERVAL\",\"intervalMinutes\":30,\"dose\":2}";
	private static final String CREATE_DAILY_BODY =
			"{\"type\":\"DAILY_TIME\",\"timeOfDay\":\"08:30:00\",\"weekdays\":[\"MONDAY\",\"FRIDAY\"],\"dose\":1}";
	private static final String UPDATE_BODY =
			"{\"type\":\"INTERVAL\",\"intervalMinutes\":45,\"dose\":3}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private FeederConfigService feederConfigService;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	// --- Creating -----------------------------------------------------------------------------

	@Test
	void create_shouldReturn201_withLocationAndBody_forIntervalConfig() throws Exception {
		FeederConfig config = intervalConfig();
		when(feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 2))
				.thenReturn(config);

		mockMvc.perform(post("/feeders/{feederId}/configs", FEEDER_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_INTERVAL_BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/feeders/" + FEEDER_ID + "/configs/" + CONFIG_ID))
				.andExpect(jsonPath("$.id").value(CONFIG_ID.toString()))
				.andExpect(jsonPath("$.type").value("INTERVAL"))
				.andExpect(jsonPath("$.intervalMinutes").value(30))
				.andExpect(jsonPath("$.timeOfDay").value(nullValue()))
				.andExpect(jsonPath("$.dose").value(1))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
				.andExpect(jsonPath("$.feeder").doesNotExist())
				.andExpect(jsonPath("$.feederId").doesNotExist());

		verify(feederConfigService).create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 2);
		verifyNoMoreInteractions(feederConfigService);
	}

	@Test
	void create_shouldReturn201_withLocationAndBody_forDailyTimeConfig() throws Exception {
		FeederConfig config = dailyTimeConfig();
		when(feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.DAILY_TIME, null, LocalTime.of(8, 30),
				EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 1)).thenReturn(config);

		mockMvc.perform(post("/feeders/{feederId}/configs", FEEDER_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_DAILY_BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/feeders/" + FEEDER_ID + "/configs/" + CONFIG_ID))
				.andExpect(jsonPath("$.type").value("DAILY_TIME"))
				.andExpect(jsonPath("$.timeOfDay").value("08:30:00"))
				.andExpect(jsonPath("$.intervalMinutes").value(nullValue()))
				.andExpect(jsonPath("$.dose").value(1));

		verify(feederConfigService).create(USER_ID, FEEDER_ID, ConfigType.DAILY_TIME, null, LocalTime.of(8, 30),
				EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 1);
		verifyNoMoreInteractions(feederConfigService);
	}

	@Test
	void create_shouldReturn404_whenFeederDoesNotExistOrIsNotOwned() throws Exception {
		when(feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 2))
				.thenThrow(new FeederNotFoundException(FEEDER_ID));

		mockMvc.perform(post("/feeders/{feederId}/configs", FEEDER_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_INTERVAL_BODY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder not found"));
	}

	@Test
	void create_shouldReturn409_whenScheduleCollidesWithExistingRule() throws Exception {
		when(feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 2))
				.thenThrow(DuplicateFeederConfigException.forInterval(FEEDER_ID, 30));

		mockMvc.perform(post("/feeders/{feederId}/configs", FEEDER_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_INTERVAL_BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("feeder config already exists"))
				.andExpect(content().string(not(containsString("uk_feeder_configs"))));
	}

	@Test
	void create_shouldReturn400_whenDomainRejectsSchedulingValues() throws Exception {
		IllegalArgumentException cause = new IllegalArgumentException("dose must be between 1 and 100");
		when(feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 2))
				.thenThrow(new InvalidFeederDataException(cause.getMessage(), cause));

		mockMvc.perform(post("/feeders/{feederId}/configs", FEEDER_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_INTERVAL_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("invalid request content"));
	}

	@ParameterizedTest(name = "body={0}")
	@MethodSource("invalidCreateBodies")
	void create_shouldReturn400_whenBodyIsInvalid(String body) throws Exception {
		mockMvc.perform(post("/feeders/{feederId}/configs", FEEDER_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(feederConfigService);
	}

	// --- Listing ------------------------------------------------------------------------------

	@Test
	void list_shouldReturnConfigs_whenFeederIsOwned() throws Exception {
		when(feederConfigService.list(USER_ID, FEEDER_ID)).thenReturn(List.of(intervalConfig(), dailyTimeConfig()));

		mockMvc.perform(get("/feeders/{feederId}/configs", FEEDER_ID).header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(CONFIG_ID.toString()))
				.andExpect(jsonPath("$[0].type").value("INTERVAL"))
				.andExpect(jsonPath("$[1].type").value("DAILY_TIME"));

		verify(feederConfigService).list(USER_ID, FEEDER_ID);
		verifyNoMoreInteractions(feederConfigService);
	}

	@Test
	void list_shouldReturnEmptyArray_whenFeederHasNoConfigs() throws Exception {
		when(feederConfigService.list(USER_ID, FEEDER_ID)).thenReturn(List.of());

		mockMvc.perform(get("/feeders/{feederId}/configs", FEEDER_ID).header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());

		verify(feederConfigService).list(USER_ID, FEEDER_ID);
	}

	@Test
	void list_shouldReturn404_whenFeederIsNotOwned() throws Exception {
		when(feederConfigService.list(USER_ID, FEEDER_ID)).thenThrow(new FeederNotFoundException(FEEDER_ID));

		mockMvc.perform(get("/feeders/{feederId}/configs", FEEDER_ID).header("Authorization", bearer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder not found"));
	}

	// --- Reading one --------------------------------------------------------------------------

	@Test
	void get_shouldReturnConfig_whenOwnedAndFound() throws Exception {
		when(feederConfigService.get(USER_ID, FEEDER_ID, CONFIG_ID)).thenReturn(intervalConfig());

		mockMvc.perform(get("/feeders/{feederId}/configs/{id}", FEEDER_ID, CONFIG_ID).header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(CONFIG_ID.toString()))
				.andExpect(jsonPath("$.type").value("INTERVAL"));

		verify(feederConfigService).get(USER_ID, FEEDER_ID, CONFIG_ID);
		verifyNoMoreInteractions(feederConfigService);
	}

	@Test
	void get_shouldReturn404_whenFeederIsNotOwned() throws Exception {
		when(feederConfigService.get(USER_ID, FEEDER_ID, CONFIG_ID)).thenThrow(new FeederNotFoundException(FEEDER_ID));

		mockMvc.perform(get("/feeders/{feederId}/configs/{id}", FEEDER_ID, CONFIG_ID).header("Authorization", bearer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder not found"));
	}

	@Test
	void get_shouldReturn404_whenConfigNotFound() throws Exception {
		when(feederConfigService.get(USER_ID, FEEDER_ID, CONFIG_ID)).thenThrow(new FeederConfigNotFoundException(CONFIG_ID));

		mockMvc.perform(get("/feeders/{feederId}/configs/{id}", FEEDER_ID, CONFIG_ID).header("Authorization", bearer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder config not found"));
	}

	// --- Updating -----------------------------------------------------------------------------

	@Test
	void update_shouldReturnUpdatedConfig() throws Exception {
		FeederConfig updated = intervalConfig();
		when(feederConfigService.update(USER_ID, FEEDER_ID, CONFIG_ID, ConfigType.INTERVAL, 45, null, null, 3))
				.thenReturn(updated);

		mockMvc.perform(put("/feeders/{feederId}/configs/{id}", FEEDER_ID, CONFIG_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(CONFIG_ID.toString()));

		verify(feederConfigService).update(USER_ID, FEEDER_ID, CONFIG_ID, ConfigType.INTERVAL, 45, null, null, 3);
		verifyNoMoreInteractions(feederConfigService);
	}

	@Test
	void update_shouldReturn404_whenConfigNotFound() throws Exception {
		when(feederConfigService.update(USER_ID, FEEDER_ID, CONFIG_ID, ConfigType.INTERVAL, 45, null, null, 3))
				.thenThrow(new FeederConfigNotFoundException(CONFIG_ID));

		mockMvc.perform(put("/feeders/{feederId}/configs/{id}", FEEDER_ID, CONFIG_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder config not found"));
	}

	@Test
	void update_shouldReturn409_whenCollidesWithExistingConfig() throws Exception {
		when(feederConfigService.update(USER_ID, FEEDER_ID, CONFIG_ID, ConfigType.INTERVAL, 45, null, null, 3))
				.thenThrow(DuplicateFeederConfigException.forInterval(FEEDER_ID, 45));

		mockMvc.perform(put("/feeders/{feederId}/configs/{id}", FEEDER_ID, CONFIG_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("feeder config already exists"));
	}

	@ParameterizedTest(name = "body={0}")
	@MethodSource("invalidUpdateBodies")
	void update_shouldReturn400_whenBodyIsInvalid(String body) throws Exception {
		mockMvc.perform(put("/feeders/{feederId}/configs/{id}", FEEDER_ID, CONFIG_ID).header("Authorization", bearer())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(feederConfigService);
	}

	// --- Switching on and off -----------------------------------------------------------------

	@Test
	void activate_shouldReturnActivatedConfig() throws Exception {
		FeederConfig config = intervalConfig();
		when(feederConfigService.activate(USER_ID, FEEDER_ID, CONFIG_ID)).thenReturn(config);

		mockMvc.perform(post("/feeders/{feederId}/configs/{id}/activate", FEEDER_ID, CONFIG_ID)
						.header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));

		verify(feederConfigService).activate(USER_ID, FEEDER_ID, CONFIG_ID);
		verifyNoMoreInteractions(feederConfigService);
	}

	@Test
	void activate_shouldReturn404_whenConfigNotFound() throws Exception {
		when(feederConfigService.activate(USER_ID, FEEDER_ID, CONFIG_ID))
				.thenThrow(new FeederConfigNotFoundException(CONFIG_ID));

		mockMvc.perform(post("/feeders/{feederId}/configs/{id}/activate", FEEDER_ID, CONFIG_ID)
						.header("Authorization", bearer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder config not found"));
	}

	@Test
	void deactivate_shouldReturnDeactivatedConfig() throws Exception {
		FeederConfig config = intervalConfig();
		config.deactivate();
		when(feederConfigService.deactivate(USER_ID, FEEDER_ID, CONFIG_ID)).thenReturn(config);

		mockMvc.perform(post("/feeders/{feederId}/configs/{id}/deactivate", FEEDER_ID, CONFIG_ID)
						.header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		verify(feederConfigService).deactivate(USER_ID, FEEDER_ID, CONFIG_ID);
		verifyNoMoreInteractions(feederConfigService);
	}

	@Test
	void deactivate_shouldReturn404_whenConfigNotFound() throws Exception {
		when(feederConfigService.deactivate(USER_ID, FEEDER_ID, CONFIG_ID))
				.thenThrow(new FeederConfigNotFoundException(CONFIG_ID));

		mockMvc.perform(post("/feeders/{feederId}/configs/{id}/deactivate", FEEDER_ID, CONFIG_ID)
						.header("Authorization", bearer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder config not found"));
	}

	// --- Removing -----------------------------------------------------------------------------

	@Test
	void delete_shouldReturn204_withNoBody() throws Exception {
		mockMvc.perform(delete("/feeders/{feederId}/configs/{id}", FEEDER_ID, CONFIG_ID)
						.header("Authorization", bearer()))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		verify(feederConfigService).delete(USER_ID, FEEDER_ID, CONFIG_ID);
		verifyNoMoreInteractions(feederConfigService);
	}

	@Test
	void delete_shouldReturn404_whenConfigNotFound() throws Exception {
		doThrow(new FeederConfigNotFoundException(CONFIG_ID)).when(feederConfigService).delete(USER_ID, FEEDER_ID, CONFIG_ID);

		mockMvc.perform(delete("/feeders/{feederId}/configs/{id}", FEEDER_ID, CONFIG_ID)
						.header("Authorization", bearer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder config not found"));
	}

	// --- Matrices -----------------------------------------------------------------------------

	@ParameterizedTest(name = "{0}")
	@MethodSource("protectedRoutes")
	void routes_shouldReturn401_whenThereIsNoToken(RequestBuilder request) throws Exception {
		mockMvc.perform(request)
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(feederConfigService);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("routesWithAnId")
	void routesWithAnId_shouldReturn400_whenTheIdIsNotAUuid(MockHttpServletRequestBuilder request) throws Exception {
		mockMvc.perform(request.header("Authorization", bearer()))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));

		verifyNoInteractions(feederConfigService);
	}

	// --- Helpers ------------------------------------------------------------------------------

	private static Stream<String> invalidCreateBodies() {
		return Stream.of(
				"",
				"{}",
				"{\"type\":\"INTERVAL\"}",                          // dose missing
				"{\"dose\":1}",                                     // type missing
				"{\"type\":\"INVALID_TYPE\",\"dose\":1}",           // invalid enum
				"not json");
	}

	private static Stream<String> invalidUpdateBodies() {
		return Stream.of(
				"",
				"{}",
				"{\"type\":\"INTERVAL\"}",
				"{\"dose\":1}",
				"not json");
	}

	private static Stream<RequestBuilder> protectedRoutes() {
		return Stream.of(
				post("/feeders/" + FEEDER_ID + "/configs"),
				get("/feeders/" + FEEDER_ID + "/configs"),
				get("/feeders/" + FEEDER_ID + "/configs/" + CONFIG_ID),
				put("/feeders/" + FEEDER_ID + "/configs/" + CONFIG_ID),
				post("/feeders/" + FEEDER_ID + "/configs/" + CONFIG_ID + "/activate"),
				post("/feeders/" + FEEDER_ID + "/configs/" + CONFIG_ID + "/deactivate"),
				delete("/feeders/" + FEEDER_ID + "/configs/" + CONFIG_ID));
	}

	private static Stream<MockHttpServletRequestBuilder> routesWithAnId() {
		return Stream.of(
				get("/feeders/not-a-uuid/configs"),
				get("/feeders/" + FEEDER_ID + "/configs/not-a-uuid"),
				put("/feeders/" + FEEDER_ID + "/configs/not-a-uuid").contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY),
				delete("/feeders/" + FEEDER_ID + "/configs/not-a-uuid"));
	}

	private String bearer() {
		return "Bearer " + jwtService.issue(user()).value();
	}

	private static User user() {
		User user = User.create("google-abc", "tiago@example.com", "Tiago");
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	private static Feeder feeder() {
		Feeder feeder = Feeder.create(DEVICE_ID, "Feeder", null, user());
		ReflectionTestUtils.setField(feeder, "id", FEEDER_ID);
		return feeder;
	}

	private static FeederConfig intervalConfig() {
		FeederConfig config = FeederConfig.createInterval(feeder(), 30, 1);
		ReflectionTestUtils.setField(config, "id", CONFIG_ID);
		ReflectionTestUtils.setField(config, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(config, "updatedAt", UPDATED_AT);
		return config;
	}

	private static FeederConfig dailyTimeConfig() {
		FeederConfig config = FeederConfig.createDailyTime(feeder(), LocalTime.of(8, 30),
				EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 1);
		ReflectionTestUtils.setField(config, "id", CONFIG_ID);
		ReflectionTestUtils.setField(config, "createdAt", CREATED_AT);
		return config;
	}
}
