package com.threedimensional.feeder;

import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.User;
import com.threedimensional.feeder.model.enums.ConfigType;
import com.threedimensional.feeder.repository.FeederConfigRepository;
import com.threedimensional.feeder.repository.FeederRepository;
import com.threedimensional.feeder.repository.UserRepository;
import com.threedimensional.feeder.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for feeder schedules: full HTTP dispatch, authentication filters,
 * token verification, Flyway migrations on H2, real transaction boundaries, and uniqueness race handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FeederConfigIntegrationTests {

	private static final String AUTHORIZATION = "Authorization";
	private static final UUID DEVICE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
	private static final UUID OTHER_DEVICE_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
	private static final long TIMEOUT_SECONDS = 20;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private FeederRepository feederRepository;

	@Autowired
	private FeederConfigRepository feederConfigRepository;

	private User ana;
	private User bruno;
	private Feeder anaFeeder;
	private Feeder brunoFeeder;
	private String anaToken;
	private String brunoToken;

	@BeforeEach
	void setUp() {
		feederConfigRepository.deleteAll();
		feederRepository.deleteAll();
		userRepository.deleteAll();

		ana = userRepository.saveAndFlush(User.create("google-ana", "ana@example.com", "Ana"));
		bruno = userRepository.saveAndFlush(User.create("google-bruno", "bruno@example.com", "Bruno"));

		anaToken = "Bearer " + jwtService.issue(ana).value();
		brunoToken = "Bearer " + jwtService.issue(bruno).value();

		anaFeeder = feederRepository.saveAndFlush(Feeder.create(DEVICE_ID, "Ana's Feeder", "Kitchen", ana));
		brunoFeeder = feederRepository.saveAndFlush(Feeder.create(OTHER_DEVICE_ID, "Bruno's Feeder", "Living Room", bruno));
	}

	// --- Lifecycle CRUD -----------------------------------------------------------------------

	@Test
	void create_shouldRegisterIntervalSchedule_andReturn201() throws Exception {
		String body = "{\"type\":\"INTERVAL\",\"intervalMinutes\":30,\"dose\":2}";

		MvcResult result = mockMvc.perform(post("/feeders/{feederId}/configs", anaFeeder.getId())
						.header(AUTHORIZATION, anaToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/feeders/" + anaFeeder.getId() + "/configs/")))
				.andExpect(jsonPath("$.type").value("INTERVAL"))
				.andExpect(jsonPath("$.intervalMinutes").value(30))
				.andExpect(jsonPath("$.timeOfDay").value(nullValue()))
				.andExpect(jsonPath("$.dose").value(2))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn();

		List<FeederConfig> configs = feederConfigRepository.findByFeederId(anaFeeder.getId());
		assertThat(configs).hasSize(1);
		assertThat(configs.getFirst().getType()).isEqualTo(ConfigType.INTERVAL);
		assertThat(configs.getFirst().getIntervalMinutes()).isEqualTo(30);
		assertThat(configs.getFirst().getDose()).isEqualTo(2);
	}

	@Test
	void create_shouldRegisterDailyTimeSchedule_andReturn201() throws Exception {
		String body = "{\"type\":\"DAILY_TIME\",\"timeOfDay\":\"08:30:00\",\"weekdays\":[\"MONDAY\",\"WEDNESDAY\"],\"dose\":3}";

		mockMvc.perform(post("/feeders/{feederId}/configs", anaFeeder.getId())
						.header(AUTHORIZATION, anaToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("DAILY_TIME"))
				.andExpect(jsonPath("$.timeOfDay").value("08:30:00"))
				.andExpect(jsonPath("$.intervalMinutes").value(nullValue()))
				.andExpect(jsonPath("$.dose").value(3));

		List<FeederConfig> configs = feederConfigRepository.findByFeederId(anaFeeder.getId());
		assertThat(configs).hasSize(1);
		assertThat(configs.getFirst().getType()).isEqualTo(ConfigType.DAILY_TIME);
		assertThat(configs.getFirst().getTimeOfDay()).isEqualTo(LocalTime.of(8, 30));
	}

	@Test
	void create_shouldReturn409_whenIntervalCollidesOnSameFeeder() throws Exception {
		String body = "{\"type\":\"INTERVAL\",\"intervalMinutes\":30,\"dose\":1}";

		mockMvc.perform(post("/feeders/{feederId}/configs", anaFeeder.getId())
						.header(AUTHORIZATION, anaToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/feeders/{feederId}/configs", anaFeeder.getId())
						.header(AUTHORIZATION, anaToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("feeder config already exists"));
	}

	@Test
	void list_shouldReturnChronologicalConfigs_withIntervalsFirst() throws Exception {
		mockMvc.perform(post("/feeders/{feederId}/configs", anaFeeder.getId())
						.header(AUTHORIZATION, anaToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"DAILY_TIME\",\"timeOfDay\":\"18:00:00\",\"weekdays\":[\"MONDAY\"],\"dose\":1}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/feeders/{feederId}/configs", anaFeeder.getId())
						.header(AUTHORIZATION, anaToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"INTERVAL\",\"intervalMinutes\":60,\"dose\":1}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/feeders/{feederId}/configs", anaFeeder.getId())
						.header(AUTHORIZATION, anaToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"DAILY_TIME\",\"timeOfDay\":\"08:00:00\",\"weekdays\":[\"MONDAY\"],\"dose\":1}"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/feeders/{feederId}/configs", anaFeeder.getId())
						.header(AUTHORIZATION, anaToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].type").value("INTERVAL"))
				.andExpect(jsonPath("$[0].intervalMinutes").value(60))
				.andExpect(jsonPath("$[1].type").value("DAILY_TIME"))
				.andExpect(jsonPath("$[1].timeOfDay").value("08:00:00"))
				.andExpect(jsonPath("$[2].type").value("DAILY_TIME"))
				.andExpect(jsonPath("$[2].timeOfDay").value("18:00:00"));
	}

	@Test
	void update_shouldModifyScheduleAttributes() throws Exception {
		FeederConfig config = feederConfigRepository.saveAndFlush(FeederConfig.createInterval(anaFeeder, 30, 1));

		String updateBody = "{\"type\":\"INTERVAL\",\"intervalMinutes\":45,\"dose\":5}";

		mockMvc.perform(put("/feeders/{feederId}/configs/{id}", anaFeeder.getId(), config.getId())
						.header(AUTHORIZATION, anaToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.intervalMinutes").value(45))
				.andExpect(jsonPath("$.dose").value(5));

		FeederConfig updated = feederConfigRepository.findById(config.getId()).orElseThrow();
		assertThat(updated.getIntervalMinutes()).isEqualTo(45);
		assertThat(updated.getDose()).isEqualTo(5);
	}

	@Test
	void activateAndDeactivate_shouldToggleActiveState() throws Exception {
		FeederConfig config = feederConfigRepository.saveAndFlush(FeederConfig.createInterval(anaFeeder, 30, 1));

		mockMvc.perform(post("/feeders/{feederId}/configs/{id}/deactivate", anaFeeder.getId(), config.getId())
						.header(AUTHORIZATION, anaToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		assertThat(feederConfigRepository.findById(config.getId()).orElseThrow().getIsActive()).isFalse();

		mockMvc.perform(post("/feeders/{feederId}/configs/{id}/activate", anaFeeder.getId(), config.getId())
						.header(AUTHORIZATION, anaToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));

		assertThat(feederConfigRepository.findById(config.getId()).orElseThrow().getIsActive()).isTrue();
	}

	@Test
	void delete_shouldRemoveConfig_andReturn204() throws Exception {
		FeederConfig config = feederConfigRepository.saveAndFlush(FeederConfig.createInterval(anaFeeder, 30, 1));

		mockMvc.perform(delete("/feeders/{feederId}/configs/{id}", anaFeeder.getId(), config.getId())
						.header(AUTHORIZATION, anaToken))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		assertThat(feederConfigRepository.findById(config.getId())).isEmpty();
	}

	// --- Security & Owner Isolation -----------------------------------------------------------

	@Test
	void crossUserAccess_shouldReturn404FeederNotFound() throws Exception {
		mockMvc.perform(get("/feeders/{feederId}/configs", anaFeeder.getId())
						.header(AUTHORIZATION, brunoToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder not found"));
	}

	@Test
	void crossFeederConfigAccess_shouldReturn404ConfigNotFound() throws Exception {
		FeederConfig anaConfig = feederConfigRepository.saveAndFlush(FeederConfig.createInterval(anaFeeder, 30, 1));

		mockMvc.perform(get("/feeders/{feederId}/configs/{id}", brunoFeeder.getId(), anaConfig.getId())
						.header(AUTHORIZATION, brunoToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("feeder config not found"));
	}

	// --- Race Condition -----------------------------------------------------------------------

	@Test
	void create_shouldReturnExactlyOne201AndOne409_whenConcurrentRequestsRaceForSameSchedule() throws Exception {
		String body = "{\"type\":\"INTERVAL\",\"intervalMinutes\":30,\"dose\":1}";
		CyclicBarrier startLine = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		List<Integer> statuses;
		try {
			Future<Integer> attempt1 = executor.submit(() -> createAfter(startLine, anaToken, anaFeeder.getId(), body));
			Future<Integer> attempt2 = executor.submit(() -> createAfter(startLine, anaToken, anaFeeder.getId(), body));
			statuses = List.of(attempt1.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
					attempt2.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		} finally {
			executor.shutdown();
			assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
		}

		assertThat(statuses).doesNotContain(500);
		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		assertThat(feederConfigRepository.findByFeederId(anaFeeder.getId())).hasSize(1);
	}

	private int createAfter(CyclicBarrier barrier, String token, UUID feederId, String body) throws Exception {
		barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		return mockMvc.perform(post("/feeders/{feederId}/configs", feederId)
						.header(AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andReturn()
				.getResponse()
				.getStatus();
	}
}
