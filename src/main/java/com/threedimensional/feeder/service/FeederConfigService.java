package com.threedimensional.feeder.service;

import com.threedimensional.feeder.exception.DuplicateFeederConfigException;
import com.threedimensional.feeder.exception.FeederConfigNotFoundException;
import com.threedimensional.feeder.exception.InvalidFeederDataException;
import com.threedimensional.feeder.model.Feeder;
import com.threedimensional.feeder.model.FeederConfig;
import com.threedimensional.feeder.model.enums.ConfigType;
import com.threedimensional.feeder.repository.FeederConfigRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The feeder configuration lifecycle. Every operation is scoped to the caller's account and the
 * owning feeder: the caller's id is extracted from the JWT token, and ownership of the feeder is
 * validated through {@link FeederService#get(UUID, UUID)} before any rule is inspected or mutated.
 * <p>
 * Scoping rules to both the user and the feeder ensures an attacker cannot probe foreign feeders or
 * configurations: accessing another account's feeder yields a 404 (feeder not found), while accessing
 * a non-existent or foreign configuration under an owned feeder yields a 404 (feeder config not found).
 * Neither reveals whether foreign resources exist.
 * <p>
 * Invariant enforcement is mirrored between domain checks, service-level uniqueness pre-checks,
 * and database UNIQUE/CHECK constraints. Flush calls inside mutating methods ensure race conditions
 * are caught as {@link DuplicateFeederConfigException} (409) rather than escaping as unexpected 500s.
 */
@Service
public class FeederConfigService {

    private final FeederConfigRepository feederConfigRepository;
    private final FeederService feederService;

    public FeederConfigService(FeederConfigRepository feederConfigRepository, FeederService feederService) {
        this.feederConfigRepository = feederConfigRepository;
        this.feederService = feederService;
    }

    /**
     * Creates a new feeding rule on an owned feeder. Checks for duplicate interval or time-of-day
     * rules before saving, and flushes to guarantee that concurrent inserts losing a race result in
     * a clean 409 {@link DuplicateFeederConfigException} rather than an unhandled 500 error.
     */
    @Transactional
    public FeederConfig create(UUID userId, UUID feederId, ConfigType type, Integer intervalMinutes,
                               LocalTime timeOfDay, Set<DayOfWeek> weekdays, Integer dose) {
        Feeder feeder = feederService.get(userId, feederId);

        if (type == ConfigType.INTERVAL && intervalMinutes != null) {
            if (feederConfigRepository.findByFeederIdAndIntervalMinutes(feederId, intervalMinutes).isPresent()) {
                throw DuplicateFeederConfigException.forInterval(feederId, intervalMinutes);
            }
        } else if (type == ConfigType.DAILY_TIME && timeOfDay != null) {
            if (feederConfigRepository.findByFeederIdAndTimeOfDay(feederId, timeOfDay).isPresent()) {
                throw DuplicateFeederConfigException.forTimeOfDay(feederId, timeOfDay);
            }
        }

        FeederConfig config = translatingInvalidInput(() -> {
            if (dose == null) {
                throw new IllegalArgumentException("dose must not be null");
            }
            if (type == ConfigType.INTERVAL) {
                if (intervalMinutes == null) {
                    throw new IllegalArgumentException("interval minutes must not be null");
                }
                return FeederConfig.createInterval(feeder, intervalMinutes, dose);
            } else if (type == ConfigType.DAILY_TIME) {
                return FeederConfig.createDailyTime(feeder, timeOfDay, weekdays, dose);
            } else {
                throw new IllegalArgumentException("type must not be null");
            }
        });

        try {
            return feederConfigRepository.saveAndFlush(config);
        } catch (DataIntegrityViolationException e) {
            if (type == ConfigType.INTERVAL) {
                throw DuplicateFeederConfigException.forInterval(feederId, intervalMinutes);
            } else {
                throw DuplicateFeederConfigException.forTimeOfDay(feederId, timeOfDay);
            }
        }
    }

    /**
     * Lists every rule configured for the given feeder in chronological order: intervals first (due to
     * null time of day), followed by daily timed rules in ascending order.
     */
    @Transactional(readOnly = true)
    public List<FeederConfig> list(UUID userId, UUID feederId) {
        feederService.get(userId, feederId);
        return feederConfigRepository.findByFeederIdOrderByTimeOfDayAscNullsFirst(feederId);
    }

    /**
     * Loads a single configuration belonging to an owned feeder.
     */
    @Transactional(readOnly = true)
    public FeederConfig get(UUID userId, UUID feederId, UUID id) {
        return loadOwned(userId, feederId, id);
    }

    /**
     * Updates an existing rule's scheduling attributes. Validates uniqueness against other rules of the
     * same feeder, and flushes changes to trigger and handle any constraint violations.
     */
    @Transactional
    public FeederConfig update(UUID userId, UUID feederId, UUID id, ConfigType type,
                               Integer intervalMinutes, LocalTime timeOfDay,
                               Set<DayOfWeek> weekdays, Integer dose) {
        FeederConfig config = loadOwned(userId, feederId, id);

        if (type == ConfigType.INTERVAL && intervalMinutes != null) {
            Optional<FeederConfig> existing = feederConfigRepository.findByFeederIdAndIntervalMinutes(feederId, intervalMinutes);
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw DuplicateFeederConfigException.forInterval(feederId, intervalMinutes);
            }
        } else if (type == ConfigType.DAILY_TIME && timeOfDay != null) {
            Optional<FeederConfig> existing = feederConfigRepository.findByFeederIdAndTimeOfDay(feederId, timeOfDay);
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw DuplicateFeederConfigException.forTimeOfDay(feederId, timeOfDay);
            }
        }

        translatingInvalidInput(() -> {
            if (dose == null) {
                throw new IllegalArgumentException("dose must not be null");
            }
            if (type == ConfigType.INTERVAL && intervalMinutes == null) {
                throw new IllegalArgumentException("interval minutes must not be null");
            }
            config.update(type, intervalMinutes, timeOfDay, weekdays, dose);
            return config;
        });

        try {
            return feederConfigRepository.saveAndFlush(config);
        } catch (DataIntegrityViolationException e) {
            if (type == ConfigType.INTERVAL) {
                throw DuplicateFeederConfigException.forInterval(feederId, intervalMinutes);
            } else {
                throw DuplicateFeederConfigException.forTimeOfDay(feederId, timeOfDay);
            }
        }
    }

    /** Switches the feeding rule on. Idempotent. */
    @Transactional
    public FeederConfig activate(UUID userId, UUID feederId, UUID id) {
        FeederConfig config = loadOwned(userId, feederId, id);
        config.activate();
        return config;
    }

    /** Switches the feeding rule off. Idempotent. */
    @Transactional
    public FeederConfig deactivate(UUID userId, UUID feederId, UUID id) {
        FeederConfig config = loadOwned(userId, feederId, id);
        config.deactivate();
        return config;
    }

    /** Removes a feeding rule from the feeder. */
    @Transactional
    public void delete(UUID userId, UUID feederId, UUID id) {
        FeederConfig config = loadOwned(userId, feederId, id);
        feederConfigRepository.delete(config);
    }

    private FeederConfig loadOwned(UUID userId, UUID feederId, UUID id) {
        feederService.get(userId, feederId);
        return feederConfigRepository.findByIdAndFeederId(id, feederId)
                .orElseThrow(() -> new FeederConfigNotFoundException(id));
    }

    private static <T> T translatingInvalidInput(Supplier<T> domainCall) {
        try {
            return domainCall.get();
        } catch (IllegalArgumentException e) {
            throw new InvalidFeederDataException(e.getMessage(), e);
        }
    }
}
