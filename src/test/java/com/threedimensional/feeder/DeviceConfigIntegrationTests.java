package com.threedimensional.feeder;

import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.User;
import com.threedimensional.feeder.repository.FeederConfigRepository;
import com.threedimensional.feeder.repository.FeederRepository;
import com.threedimensional.feeder.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the hardware configuration polling route. Exercises real HTTP
 * dispatch without Authorization headers, database queries on H2, filtering of deactivated rules,
 * and 404 responses for unpaired hardware.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DeviceConfigIntegrationTests {

	private static final UUID DEVICE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
	private static final UUID UNPAIRED_DEVICE_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private FeederRepository feederRepository;

	@Autowired
	private FeederConfigRepository feederConfigRepository;

	private Feeder feeder;

	@BeforeEach
	void setUp() {
		feederConfigRepository.deleteAll();
		feederRepository.deleteAll();
		userRepository.deleteAll();

		User user = userRepository.saveAndFlush(User.create("google-user", "user@example.com", "Hardware Owner"));
		feeder = feederRepository.saveAndFlush(Feeder.create(DEVICE_ID, "Dining Room Feeder", "Near window", user));
	}

	@Test
	void getConfig_shouldReturnActiveRulesWithoutToken_whenDeviceIsPaired() throws Exception {
		FeederConfig activeInterval = FeederConfig.createInterval(feeder, 60, 2);
		FeederConfig activeDaily = FeederConfig.createDailyTime(feeder, LocalTime.of(8, 0),
				EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 1);
		FeederConfig pausedInterval = FeederConfig.createInterval(feeder, 120, 3);
		pausedInterval.deactivate();

		feederConfigRepository.saveAllAndFlush(java.util.List.of(activeInterval, activeDaily, pausedInterval));

		mockMvc.perform(get("/devices/{deviceId}/config", DEVICE_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.timezone").value("America/Bahia"))
				.andExpect(jsonPath("$.serverTime").isNotEmpty())
				.andExpect(jsonPath("$.schedules", hasSize(2)))
				.andExpect(jsonPath("$.schedules[0].intervalMinutes").value(60))
				.andExpect(jsonPath("$.schedules[0].dose").value(2))
				.andExpect(jsonPath("$.schedules[1].timeOfDay").value("08:00:00"))
				.andExpect(jsonPath("$.schedules[1].weekdays[0]").value("MONDAY"))
				.andExpect(jsonPath("$.schedules[1].weekdays[1]").value("FRIDAY"));
	}

	@Test
	void getConfig_shouldReturn404_whenHardwareIsNotPaired() throws Exception {
		mockMvc.perform(get("/devices/{deviceId}/config", UNPAIRED_DEVICE_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("device not paired"));
	}

	@Test
	void getConfig_shouldReflectDeactivatedFeederState() throws Exception {
		feeder.deactivate();
		feederRepository.saveAndFlush(feeder);

		mockMvc.perform(get("/devices/{deviceId}/config", DEVICE_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.schedules").isEmpty());
	}
}
