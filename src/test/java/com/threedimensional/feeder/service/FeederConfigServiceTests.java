package com.threedimensional.feeder.service;

import com.threedimensional.feeder.exception.DuplicateFeederConfigException;
import com.threedimensional.feeder.exception.FeederConfigNotFoundException;
import com.threedimensional.feeder.exception.FeederNotFoundException;
import com.threedimensional.feeder.exception.InvalidFeederDataException;
import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.User;
import com.threedimensional.feeder.model.enums.ConfigType;
import com.threedimensional.feeder.repository.FeederConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Business rules of the feeder configuration lifecycle. Scoped entirely to Mockito: outcomes
 * under test include ownership boundary enforcement, duplicate rejection across both schedule shapes,
 * domain validation wrapping, and flush races converting to 409 conflicts.
 */
@ExtendWith(MockitoExtension.class)
class FeederConfigServiceTests {

	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID FEEDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID CONFIG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID OTHER_CONFIG_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID DEVICE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final LocalTime TIME_OF_DAY = LocalTime.of(8, 0);
	private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

	@Mock
	private FeederConfigRepository feederConfigRepository;

	@Mock
	private FeederService feederService;

	@InjectMocks
	private FeederConfigService feederConfigService;

	private User user;
	private Feeder feeder;
	private FeederConfig intervalConfig;
	private FeederConfig dailyTimeConfig;

	@BeforeEach
	void setUp() {
		user = User.create("google-abc", "tiago@example.com", "Tiago");
		feeder = Feeder.create(DEVICE_ID, "Alimentador da sala", "Tiago's feeder", user);
		ReflectionTestUtils.setField(feeder, "id", FEEDER_ID);

		intervalConfig = FeederConfig.createInterval(feeder, 30, 1);
		ReflectionTestUtils.setField(intervalConfig, "id", CONFIG_ID);

		dailyTimeConfig = FeederConfig.createDailyTime(feeder, TIME_OF_DAY, WEEKDAYS, 2);
		ReflectionTestUtils.setField(dailyTimeConfig, "id", CONFIG_ID);
	}

	// --- create -------------------------------------------------------------------------------

	@Test
	void create_shouldCreateIntervalRule_whenInputIsValid() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByFeederIdAndIntervalMinutes(FEEDER_ID, 30)).thenReturn(Optional.empty());
		when(feederConfigRepository.saveAndFlush(any(FeederConfig.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		FeederConfig created = feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 1);

		ArgumentCaptor<FeederConfig> saved = ArgumentCaptor.forClass(FeederConfig.class);
		verify(feederService).get(USER_ID, FEEDER_ID);
		verify(feederConfigRepository).findByFeederIdAndIntervalMinutes(FEEDER_ID, 30);
		verify(feederConfigRepository).saveAndFlush(saved.capture());

		assertThat(created.getType()).isEqualTo(ConfigType.INTERVAL);
		assertThat(created.getIntervalMinutes()).isEqualTo(30);
		assertThat(created.getTimeOfDay()).isNull();
		assertThat(created.getDose()).isEqualTo(1);
		assertThat(created.getIsActive()).isTrue();
		assertThat(saved.getValue().getFeeder()).isSameAs(feeder);
	}

	@Test
	void create_shouldCreateDailyTimeRule_whenInputIsValid() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByFeederIdAndTimeOfDay(FEEDER_ID, TIME_OF_DAY)).thenReturn(Optional.empty());
		when(feederConfigRepository.saveAndFlush(any(FeederConfig.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		FeederConfig created = feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.DAILY_TIME, null, TIME_OF_DAY, WEEKDAYS, 2);

		ArgumentCaptor<FeederConfig> saved = ArgumentCaptor.forClass(FeederConfig.class);
		verify(feederService).get(USER_ID, FEEDER_ID);
		verify(feederConfigRepository).findByFeederIdAndTimeOfDay(FEEDER_ID, TIME_OF_DAY);
		verify(feederConfigRepository).saveAndFlush(saved.capture());

		assertThat(created.getType()).isEqualTo(ConfigType.DAILY_TIME);
		assertThat(created.getTimeOfDay()).isEqualTo(TIME_OF_DAY);
		assertThat(created.getIntervalMinutes()).isNull();
		assertThat(created.getWeekdays()).containsExactlyInAnyOrderElementsOf(WEEKDAYS);
		assertThat(created.getDose()).isEqualTo(2);
		assertThat(created.getIsActive()).isTrue();
	}

	@Test
	void create_shouldThrowFeederNotFound_whenFeederIsNotOwned() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenThrow(new FeederNotFoundException(FEEDER_ID));

		assertThatThrownBy(() -> feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 1))
				.isInstanceOf(FeederNotFoundException.class);

		verify(feederService).get(USER_ID, FEEDER_ID);
		verifyNoMoreInteractions(feederConfigRepository);
	}

	@Test
	void create_shouldThrowDuplicate_whenIntervalAlreadyExists() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByFeederIdAndIntervalMinutes(FEEDER_ID, 30))
				.thenReturn(Optional.of(intervalConfig));

		assertThatThrownBy(() -> feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 1))
				.isInstanceOf(DuplicateFeederConfigException.class)
				.hasMessageContaining("interval 30 minutes");

		verify(feederConfigRepository, never()).saveAndFlush(any());
	}

	@Test
	void create_shouldThrowDuplicate_whenTimeOfDayAlreadyExists() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByFeederIdAndTimeOfDay(FEEDER_ID, TIME_OF_DAY))
				.thenReturn(Optional.of(dailyTimeConfig));

		assertThatThrownBy(() -> feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.DAILY_TIME, null, TIME_OF_DAY, WEEKDAYS, 1))
				.isInstanceOf(DuplicateFeederConfigException.class)
				.hasMessageContaining(TIME_OF_DAY.toString());

		verify(feederConfigRepository, never()).saveAndFlush(any());
	}

	@Test
	void create_shouldThrowDuplicate_whenSaveAndFlushFailsIntegrityCheckForInterval() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByFeederIdAndIntervalMinutes(FEEDER_ID, 30)).thenReturn(Optional.empty());
		when(feederConfigRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_feeder_configs_interval"));

		assertThatThrownBy(() -> feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 1))
				.isInstanceOf(DuplicateFeederConfigException.class)
				.hasMessageContaining("interval 30 minutes");
	}

	@Test
	void create_shouldThrowDuplicate_whenSaveAndFlushFailsIntegrityCheckForDailyTime() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByFeederIdAndTimeOfDay(FEEDER_ID, TIME_OF_DAY)).thenReturn(Optional.empty());
		when(feederConfigRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_feeder_configs_time"));

		assertThatThrownBy(() -> feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.DAILY_TIME, null, TIME_OF_DAY, WEEKDAYS, 1))
				.isInstanceOf(DuplicateFeederConfigException.class)
				.hasMessageContaining(TIME_OF_DAY.toString());
	}

	@Test
	void create_shouldThrowInvalidFeederData_whenDomainRejectsDose() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByFeederIdAndIntervalMinutes(FEEDER_ID, 30)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, 30, null, null, 0))
				.isInstanceOf(InvalidFeederDataException.class)
				.hasMessageStartingWith("dose must be between");

		verify(feederConfigRepository, never()).saveAndFlush(any());
	}

	@Test
	void create_shouldThrowInvalidFeederData_whenDomainRejectsInterval() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);

		assertThatThrownBy(() -> feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.INTERVAL, null, null, null, 1))
				.isInstanceOf(InvalidFeederDataException.class)
				.hasMessage("interval minutes must not be null");

		verify(feederConfigRepository, never()).saveAndFlush(any());
	}

	@Test
	void create_shouldThrowInvalidFeederData_whenDomainRejectsWeekdaysForDailyTime() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByFeederIdAndTimeOfDay(FEEDER_ID, TIME_OF_DAY)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> feederConfigService.create(USER_ID, FEEDER_ID, ConfigType.DAILY_TIME, null, TIME_OF_DAY, Set.of(), 1))
				.isInstanceOf(InvalidFeederDataException.class)
				.hasMessage("weekdays must not be empty");

		verify(feederConfigRepository, never()).saveAndFlush(any());
	}

	// --- list ---------------------------------------------------------------------------------

	@Test
	void list_shouldReturnConfigsInChronologicalOrder_whenFeederIsOwned() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByFeederIdOrderByTimeOfDayAscNullsFirst(FEEDER_ID))
				.thenReturn(List.of(intervalConfig, dailyTimeConfig));

		List<FeederConfig> result = feederConfigService.list(USER_ID, FEEDER_ID);

		verify(feederService).get(USER_ID, FEEDER_ID);
		verify(feederConfigRepository).findByFeederIdOrderByTimeOfDayAscNullsFirst(FEEDER_ID);
		assertThat(result).containsExactly(intervalConfig, dailyTimeConfig);
	}

	@Test
	void list_shouldThrowFeederNotFound_whenFeederIsNotOwned() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenThrow(new FeederNotFoundException(FEEDER_ID));

		assertThatThrownBy(() -> feederConfigService.list(USER_ID, FEEDER_ID))
				.isInstanceOf(FeederNotFoundException.class);

		verifyNoMoreInteractions(feederConfigRepository);
	}

	// --- get ----------------------------------------------------------------------------------

	@Test
	void get_shouldReturnConfig_whenOwnedAndFound() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.of(intervalConfig));

		FeederConfig result = feederConfigService.get(USER_ID, FEEDER_ID, CONFIG_ID);

		assertThat(result).isSameAs(intervalConfig);
	}

	@Test
	void get_shouldThrowFeederNotFound_whenFeederIsNotOwned() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenThrow(new FeederNotFoundException(FEEDER_ID));

		assertThatThrownBy(() -> feederConfigService.get(USER_ID, FEEDER_ID, CONFIG_ID))
				.isInstanceOf(FeederNotFoundException.class);

		verifyNoMoreInteractions(feederConfigRepository);
	}

	@Test
	void get_shouldThrowFeederConfigNotFound_whenConfigDoesNotExistOnFeeder() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> feederConfigService.get(USER_ID, FEEDER_ID, CONFIG_ID))
				.isInstanceOf(FeederConfigNotFoundException.class);
	}

	// --- update -------------------------------------------------------------------------------

	@Test
	void update_shouldUpdateConfig_whenInputIsValid() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.of(intervalConfig));
		when(feederConfigRepository.findByFeederIdAndIntervalMinutes(FEEDER_ID, 60)).thenReturn(Optional.empty());
		when(feederConfigRepository.saveAndFlush(any(FeederConfig.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		FeederConfig updated = feederConfigService.update(USER_ID, FEEDER_ID, CONFIG_ID, ConfigType.INTERVAL, 60, null, null, 3);

		assertThat(updated.getIntervalMinutes()).isEqualTo(60);
		assertThat(updated.getDose()).isEqualTo(3);
		verify(feederConfigRepository).saveAndFlush(intervalConfig);
	}

	@Test
	void update_shouldAllowKeepingSameIntervalOnSameConfig() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.of(intervalConfig));
		when(feederConfigRepository.findByFeederIdAndIntervalMinutes(FEEDER_ID, 30)).thenReturn(Optional.of(intervalConfig));
		when(feederConfigRepository.saveAndFlush(any(FeederConfig.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		FeederConfig updated = feederConfigService.update(USER_ID, FEEDER_ID, CONFIG_ID, ConfigType.INTERVAL, 30, null, null, 2);

		assertThat(updated.getDose()).isEqualTo(2);
	}

	@Test
	void update_shouldThrowDuplicate_whenIntervalBelongsToAnotherConfig() {
		FeederConfig another = FeederConfig.createInterval(feeder, 60, 1);
		ReflectionTestUtils.setField(another, "id", OTHER_CONFIG_ID);

		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.of(intervalConfig));
		when(feederConfigRepository.findByFeederIdAndIntervalMinutes(FEEDER_ID, 60)).thenReturn(Optional.of(another));

		assertThatThrownBy(() -> feederConfigService.update(USER_ID, FEEDER_ID, CONFIG_ID, ConfigType.INTERVAL, 60, null, null, 1))
				.isInstanceOf(DuplicateFeederConfigException.class)
				.hasMessageContaining("interval 60 minutes");
	}

	@Test
	void update_shouldThrowDuplicate_whenTimeOfDayBelongsToAnotherConfig() {
		FeederConfig another = FeederConfig.createDailyTime(feeder, TIME_OF_DAY, WEEKDAYS, 1);
		ReflectionTestUtils.setField(another, "id", OTHER_CONFIG_ID);

		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.of(dailyTimeConfig));
		when(feederConfigRepository.findByFeederIdAndTimeOfDay(FEEDER_ID, TIME_OF_DAY)).thenReturn(Optional.of(another));

		assertThatThrownBy(() -> feederConfigService.update(USER_ID, FEEDER_ID, CONFIG_ID, ConfigType.DAILY_TIME, null, TIME_OF_DAY, WEEKDAYS, 2))
				.isInstanceOf(DuplicateFeederConfigException.class)
				.hasMessageContaining(TIME_OF_DAY.toString());
	}

	@Test
	void update_shouldThrowDuplicate_whenFlushCausesIntegrityViolation() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.of(intervalConfig));
		when(feederConfigRepository.findByFeederIdAndIntervalMinutes(FEEDER_ID, 60)).thenReturn(Optional.empty());
		when(feederConfigRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_feeder_configs_interval"));

		assertThatThrownBy(() -> feederConfigService.update(USER_ID, FEEDER_ID, CONFIG_ID, ConfigType.INTERVAL, 60, null, null, 1))
				.isInstanceOf(DuplicateFeederConfigException.class);
	}

	// --- activate & deactivate ----------------------------------------------------------------

	@Test
	void activate_shouldActivateConfig() {
		intervalConfig.deactivate();
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.of(intervalConfig));

		FeederConfig result = feederConfigService.activate(USER_ID, FEEDER_ID, CONFIG_ID);

		assertThat(result.getIsActive()).isTrue();
	}

	@Test
	void deactivate_shouldDeactivateConfig() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.of(intervalConfig));

		FeederConfig result = feederConfigService.deactivate(USER_ID, FEEDER_ID, CONFIG_ID);

		assertThat(result.getIsActive()).isFalse();
	}

	// --- delete -------------------------------------------------------------------------------

	@Test
	void delete_shouldDeleteConfig_whenFound() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.of(intervalConfig));

		feederConfigService.delete(USER_ID, FEEDER_ID, CONFIG_ID);

		verify(feederConfigRepository).delete(intervalConfig);
	}

	@Test
	void delete_shouldThrowFeederNotFound_whenFeederIsNotOwned() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenThrow(new FeederNotFoundException(FEEDER_ID));

		assertThatThrownBy(() -> feederConfigService.delete(USER_ID, FEEDER_ID, CONFIG_ID))
				.isInstanceOf(FeederNotFoundException.class);

		verify(feederConfigRepository, never()).delete(any());
	}

	@Test
	void delete_shouldThrowFeederConfigNotFound_whenConfigDoesNotExistOnFeeder() {
		when(feederService.get(USER_ID, FEEDER_ID)).thenReturn(feeder);
		when(feederConfigRepository.findByIdAndFeederId(CONFIG_ID, FEEDER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> feederConfigService.delete(USER_ID, FEEDER_ID, CONFIG_ID))
				.isInstanceOf(FeederConfigNotFoundException.class);

		verify(feederConfigRepository, never()).delete(any());
	}
}
