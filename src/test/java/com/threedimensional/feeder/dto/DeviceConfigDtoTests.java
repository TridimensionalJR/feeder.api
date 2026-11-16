package com.threedimensional.feeder.dto;

import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.User;
import com.threedimensional.feeder.model.enums.ConfigType;
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
 * Wire-level contract tests for hardware configuration DTOs: verifying accurate schedule mapping,
 * preservation of nulls across schedule shapes, and proving that lazy parent associations (User)
 * are never navigated when building the payload.
 */
class DeviceConfigDtoTests {

	private static final UUID DEVICE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID FEEDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID CONFIG_INTERVAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID CONFIG_DAILY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final Instant SERVER_TIME = Instant.parse("2026-11-15T20:00:00Z");
	private static final LocalTime TIME_OF_DAY = LocalTime.of(8, 30);
	private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

	@Test
	void from_shouldMapAllFields_whenFeederAndRulesAreActive() {
		Feeder feeder = feeder();
		FeederConfig intervalConfig = intervalConfig(feeder);
		FeederConfig dailyConfig = dailyConfig(feeder);

		DeviceConfigResponse response = DeviceConfigResponse.from(feeder, List.of(intervalConfig, dailyConfig), SERVER_TIME);

		assertThat(response.active()).isTrue();
		assertThat(response.timezone()).isEqualTo("America/Bahia");
		assertThat(response.serverTime()).isEqualTo(SERVER_TIME);
		assertThat(response.schedules()).hasSize(2);

		DeviceScheduleItemResponse item1 = response.schedules().get(0);
		assertThat(item1.id()).isEqualTo(CONFIG_INTERVAL_ID);
		assertThat(item1.type()).isEqualTo(ConfigType.INTERVAL);
		assertThat(item1.intervalMinutes()).isEqualTo(30);
		assertThat(item1.timeOfDay()).isNull();
		assertThat(item1.weekdays()).containsExactlyInAnyOrder(DayOfWeek.values());
		assertThat(item1.dose()).isEqualTo(1);

		DeviceScheduleItemResponse item2 = response.schedules().get(1);
		assertThat(item2.id()).isEqualTo(CONFIG_DAILY_ID);
		assertThat(item2.type()).isEqualTo(ConfigType.DAILY_TIME);
		assertThat(item2.intervalMinutes()).isNull();
		assertThat(item2.timeOfDay()).isEqualTo(TIME_OF_DAY);
		assertThat(item2.weekdays()).containsExactlyInAnyOrderElementsOf(WEEKDAYS);
		assertThat(item2.dose()).isEqualTo(2);
	}

	@Test
	void from_shouldReportInactive_whenFeederIsDeactivated() {
		Feeder feeder = feeder();
		feeder.deactivate();

		DeviceConfigResponse response = DeviceConfigResponse.from(feeder, List.of(), SERVER_TIME);

		assertThat(response.active()).isFalse();
		assertThat(response.schedules()).isEmpty();
	}

	@Test
	void from_shouldNotExposeOwnerData() {
		assertThat(componentNames(DeviceConfigResponse.class))
				.containsExactly("active", "timezone", "serverTime", "schedules");
		assertThat(componentNames(DeviceScheduleItemResponse.class))
				.containsExactly("id", "type", "intervalMinutes", "timeOfDay", "weekdays", "dose");
	}

	@Test
	void from_shouldNotReadTheOwner_whenBuildingResponse() {
		Feeder feeder = mock(Feeder.class);

		DeviceConfigResponse.from(feeder, List.of(), SERVER_TIME);

		verify(feeder, never()).getUser();
	}

	private static List<String> componentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();
	}

	private static Feeder feeder() {
		User owner = User.create("google-abc", "tiago@example.com", "Tiago");
		Feeder feeder = Feeder.create(DEVICE_ID, "Kitchen Feeder", "Kitchen", owner);
		ReflectionTestUtils.setField(feeder, "id", FEEDER_ID);
		return feeder;
	}

	private static FeederConfig intervalConfig(Feeder feeder) {
		FeederConfig config = FeederConfig.createInterval(feeder, 30, 1);
		ReflectionTestUtils.setField(config, "id", CONFIG_INTERVAL_ID);
		return config;
	}

	private static FeederConfig dailyConfig(Feeder feeder) {
		FeederConfig config = FeederConfig.createDailyTime(feeder, TIME_OF_DAY, WEEKDAYS, 2);
		ReflectionTestUtils.setField(config, "id", CONFIG_DAILY_ID);
		return config;
	}
}
