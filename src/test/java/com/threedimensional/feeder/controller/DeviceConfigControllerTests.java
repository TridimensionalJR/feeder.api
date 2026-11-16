package com.threedimensional.feeder.controller;

import com.threedimensional.feeder.config.JwtConfig;
import com.threedimensional.feeder.config.SecurityConfig;
import com.threedimensional.feeder.dto.DeviceConfigResponse;
import com.threedimensional.feeder.dto.DeviceScheduleItemResponse;
import com.threedimensional.feeder.exception.DeviceNotPairedException;
import com.threedimensional.feeder.model.enums.ConfigType;
import com.threedimensional.feeder.service.DeviceConfigService;
import com.threedimensional.feeder.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of {@link DeviceConfigController}: public unauthenticated hardware access,
 * successful 200 payload delivery, 404 translation for unpaired hardware, and 400 rejection for malformed UUIDs.
 */
@WebMvcTest(DeviceConfigController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class})
class DeviceConfigControllerTests {

	private static final UUID DEVICE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID SCHEDULE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final Instant SERVER_TIME = Instant.parse("2026-11-15T21:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DeviceConfigService deviceConfigService;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	void getConfig_shouldReturn200_withoutAuthentication() throws Exception {
		DeviceScheduleItemResponse schedule =
				new DeviceScheduleItemResponse(SCHEDULE_ID, ConfigType.INTERVAL, 30, null, null, 2);
		DeviceConfigResponse response =
				new DeviceConfigResponse(true, "America/Bahia", SERVER_TIME, List.of(schedule));

		when(deviceConfigService.getConfiguration(DEVICE_ID)).thenReturn(response);

		mockMvc.perform(get("/devices/{deviceId}/config", DEVICE_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.timezone").value("America/Bahia"))
				.andExpect(jsonPath("$.serverTime").value(SERVER_TIME.toString()))
				.andExpect(jsonPath("$.schedules[0].id").value(SCHEDULE_ID.toString()))
				.andExpect(jsonPath("$.schedules[0].type").value("INTERVAL"))
				.andExpect(jsonPath("$.schedules[0].intervalMinutes").value(30))
				.andExpect(jsonPath("$.schedules[0].dose").value(2));

		verify(deviceConfigService).getConfiguration(DEVICE_ID);
		verifyNoMoreInteractions(deviceConfigService);
	}

	@Test
	void getConfig_shouldReturn404_whenDeviceIsNotPaired() throws Exception {
		when(deviceConfigService.getConfiguration(DEVICE_ID)).thenThrow(new DeviceNotPairedException(DEVICE_ID));

		mockMvc.perform(get("/devices/{deviceId}/config", DEVICE_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("device not paired"));

		verify(deviceConfigService).getConfiguration(DEVICE_ID);
	}

	@Test
	void getConfig_shouldReturn400_whenDeviceIdIsNotAUuid() throws Exception {
		mockMvc.perform(get("/devices/not-a-valid-uuid/config"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));

		verifyNoInteractions(deviceConfigService);
	}
}
