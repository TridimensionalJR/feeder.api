package com.threedimensional.feeder.service;

import com.threedimensional.feeder.dto.DeviceConfigResponse;
import com.threedimensional.feeder.exception.DeviceNotPairedException;
import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.User;
import com.threedimensional.feeder.model.enums.ConfigType;
import com.threedimensional.feeder.repository.FeederConfigRepository;
import com.threedimensional.feeder.repository.FeederRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Business rules for device configuration queries. Verifies device lookup by hardware id,
 * filtering strictly active rules, and handling of unregistered hardware identifiers.
 */
@ExtendWith(MockitoExtension.class)
class DeviceConfigServiceTests {

	private static final UUID DEVICE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID FEEDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final Instant FIXED_NOW = Instant.parse("2026-11-15T20:30:00Z");

	@Mock
	private FeederRepository feederRepository;

	@Mock
	private FeederConfigRepository feederConfigRepository;

	private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

	private DeviceConfigService deviceConfigService;

	private Feeder feeder;
	private FeederConfig activeRule;

	@BeforeEach
	void setUp() {
		deviceConfigService = new DeviceConfigService(feederRepository, feederConfigRepository, clock);

		User user = User.create("google-abc", "tiago@example.com", "Tiago");
		feeder = Feeder.create(DEVICE_ID, "Sala", "Kitchen", user);
		ReflectionTestUtils.setField(feeder, "id", FEEDER_ID);

		activeRule = FeederConfig.createInterval(feeder, 30, 2);
	}

	@Test
	void getConfiguration_shouldReturnActiveConfigs_whenDeviceIsPaired() {
		when(feederRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(feeder));
		when(feederConfigRepository.findByFeederIdAndIsActiveTrue(FEEDER_ID)).thenReturn(List.of(activeRule));

		DeviceConfigResponse response = deviceConfigService.getConfiguration(DEVICE_ID);

		verify(feederRepository).findByDeviceId(DEVICE_ID);
		verify(feederConfigRepository).findByFeederIdAndIsActiveTrue(FEEDER_ID);

		assertThat(response.active()).isTrue();
		assertThat(response.timezone()).isEqualTo("America/Bahia");
		assertThat(response.serverTime()).isEqualTo(FIXED_NOW);
		assertThat(response.schedules()).hasSize(1);
		assertThat(response.schedules().getFirst().type()).isEqualTo(ConfigType.INTERVAL);
		assertThat(response.schedules().getFirst().intervalMinutes()).isEqualTo(30);
		assertThat(response.schedules().getFirst().dose()).isEqualTo(2);
	}

	@Test
	void getConfiguration_shouldThrowDeviceNotPaired_whenDeviceIsNotPaired() {
		when(feederRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> deviceConfigService.getConfiguration(DEVICE_ID))
				.isInstanceOf(DeviceNotPairedException.class)
				.hasMessageContaining(DEVICE_ID.toString());

		verify(feederRepository).findByDeviceId(DEVICE_ID);
		verifyNoMoreInteractions(feederConfigRepository);
	}

	@Test
	void getConfiguration_shouldReturnEmptySchedules_whenFeederHasNoActiveRules() {
		when(feederRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(feeder));
		when(feederConfigRepository.findByFeederIdAndIsActiveTrue(FEEDER_ID)).thenReturn(List.of());

		DeviceConfigResponse response = deviceConfigService.getConfiguration(DEVICE_ID);

		assertThat(response.schedules()).isEmpty();
	}
}
