package com.threedimensional.feeder.dto;

import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.User;
import com.threedimensional.feeder.model.enums.ConfigType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Wire-level contract tests for feeder schedule DTOs: Bean Validation presence rules, absence of
 * domain duplication, response field pinning, and verifying that lazy parent associations are never
 * navigated when serializing responses.
 */
class FeederConfigDtoTests {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private static final UUID CONFIG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID FEEDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID DEVICE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-02-03T04:05:06Z");
	private static final Instant LAST_RUN_AT = Instant.parse("2026-02-03T04:00:00Z");
	private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
	private static final LocalTime TIME_OF_DAY = LocalTime.of(8, 30);

	// --- Creating ---------------------------------------------------------------------------

	@Test
	void create_shouldRejectNullType_whenTypeIsMissing() {
		assertViolationsOn(new CreateFeederConfigRequest(null, 30, null, null, 1), "type");
	}

	@Test
	void create_shouldRejectNullDose_whenDoseIsMissing() {
		assertViolationsOn(new CreateFeederConfigRequest(ConfigType.INTERVAL, 30, null, null, null), "dose");
	}

	@Test
	void create_shouldAcceptValidIntervalRequest() {
		assertThat(VALIDATOR.validate(new CreateFeederConfigRequest(ConfigType.INTERVAL, 30, null, null, 1))).isEmpty();
	}

	@Test
	void create_shouldAcceptValidDailyTimeRequest() {
		assertThat(VALIDATOR.validate(new CreateFeederConfigRequest(ConfigType.DAILY_TIME, null, TIME_OF_DAY, WEEKDAYS, 2))).isEmpty();
	}

	/**
	 * Pins the split of responsibilities: bounds and exclusivity are owned by {@link FeederConfig},
	 * not by Bean Validation annotations here.
	 */
	@Test
	void create_shouldNotEnforceDomainBounds_whenTheDomainOwnsTheCheck() {
		assertThat(VALIDATOR.validate(new CreateFeederConfigRequest(ConfigType.INTERVAL, -5, TIME_OF_DAY, null, 0))).isEmpty();
	}

	// --- Updating ---------------------------------------------------------------------------

	@Test
	void update_shouldRejectNullType_whenTypeIsMissing() {
		assertViolationsOn(new UpdateFeederConfigRequest(null, 30, null, null, 1), "type");
	}

	@Test
	void update_shouldRejectNullDose_whenDoseIsMissing() {
		assertViolationsOn(new UpdateFeederConfigRequest(ConfigType.INTERVAL, 30, null, null, null), "dose");
	}

	@Test
	void update_shouldAcceptValidRequest() {
		assertThat(VALIDATOR.validate(new UpdateFeederConfigRequest(ConfigType.INTERVAL, 45, null, null, 2))).isEmpty();
	}

	// --- Response mapping -------------------------------------------------------------------

	@Test
	void from_shouldMapAllFields_fromIntervalConfig() {
		FeederConfig config = intervalConfig();

		FeederConfigResponse response = FeederConfigResponse.from(config);

		assertThat(response.id()).isEqualTo(CONFIG_ID);
		assertThat(response.type()).isEqualTo(ConfigType.INTERVAL);
		assertThat(response.intervalMinutes()).isEqualTo(30);
		assertThat(response.timeOfDay()).isNull();
		assertThat(response.weekdays()).containsExactlyInAnyOrder(DayOfWeek.values());
		assertThat(response.dose()).isEqualTo(1);
		assertThat(response.lastRunAt()).isEqualTo(LAST_RUN_AT);
		assertThat(response.active()).isTrue();
		assertThat(response.createdAt()).isEqualTo(CREATED_AT);
		assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
	}

	@Test
	void from_shouldMapAllFields_fromDailyTimeConfig() {
		FeederConfig config = dailyTimeConfig();

		FeederConfigResponse response = FeederConfigResponse.from(config);

		assertThat(response.id()).isEqualTo(CONFIG_ID);
		assertThat(response.type()).isEqualTo(ConfigType.DAILY_TIME);
		assertThat(response.intervalMinutes()).isNull();
		assertThat(response.timeOfDay()).isEqualTo(TIME_OF_DAY);
		assertThat(response.weekdays()).containsExactlyInAnyOrderElementsOf(WEEKDAYS);
		assertThat(response.dose()).isEqualTo(2);
		assertThat(response.lastRunAt()).isNull();
		assertThat(response.active()).isTrue();
		assertThat(response.createdAt()).isEqualTo(CREATED_AT);
		assertThat(response.updatedAt()).isNull();
	}

	@Test
	void from_shouldReportInactive_whenConfigIsDeactivated() {
		FeederConfig config = intervalConfig();
		config.deactivate();

		assertThat(FeederConfigResponse.from(config).active()).isFalse();
	}

	/**
	 * Structural proof that no owner/feeder relationship data is leaked through this response.
	 */
	@Test
	void from_shouldNotExposeFeederData() {
		assertThat(componentNames(FeederConfigResponse.class))
				.containsExactly("id", "type", "intervalMinutes", "timeOfDay", "weekdays", "dose",
						"lastRunAt", "active", "createdAt", "updatedAt");
	}

	/**
	 * Proves that the response builder never navigates the lazy parent Feeder association,
	 * protecting against LazyInitializationException outside transactional boundaries.
	 */
	@Test
	void from_shouldNotReadTheFeeder_whenBuildingTheResponse() {
		FeederConfig config = mock(FeederConfig.class);

		FeederConfigResponse.from(config);

		verify(config, never()).getFeeder();
	}

	// --- Helpers ----------------------------------------------------------------------------

	private static void assertViolationsOn(Object request, String... fields) {
		assertThat(VALIDATOR.validate(request).stream()
				.map(violation -> violation.getPropertyPath().toString())
				.toList())
				.containsExactlyInAnyOrder(fields);
	}

	private static List<String> componentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();
	}

	private static Feeder feeder() {
		User owner = User.create("google-abc", "tiago@example.com", "Tiago");
		Feeder feeder = Feeder.create(DEVICE_ID, "Feeder", "Kitchen", owner);
		ReflectionTestUtils.setField(feeder, "id", FEEDER_ID);
		return feeder;
	}

	private static FeederConfig intervalConfig() {
		FeederConfig config = FeederConfig.createInterval(feeder(), 30, 1);
		config.registerRun(LAST_RUN_AT);
		ReflectionTestUtils.setField(config, "id", CONFIG_ID);
		ReflectionTestUtils.setField(config, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(config, "updatedAt", UPDATED_AT);
		return config;
	}

	private static FeederConfig dailyTimeConfig() {
		FeederConfig config = FeederConfig.createDailyTime(feeder(), TIME_OF_DAY, WEEKDAYS, 2);
		ReflectionTestUtils.setField(config, "id", CONFIG_ID);
		ReflectionTestUtils.setField(config, "createdAt", CREATED_AT);
		return config;
	}
}
