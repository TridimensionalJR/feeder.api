package com.threedimensional.feeder.controller;

import com.threedimensional.feeder.dto.CreateFeederConfigRequest;
import com.threedimensional.feeder.dto.FeederConfigResponse;
import com.threedimensional.feeder.dto.UpdateFeederConfigRequest;
import com.threedimensional.feeder.service.FeederConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints for managing feeding schedules owned by a specific feeder: registration, chronological
 * listing, single-rule lookup, updating, switching on/off, and deletion.
 * <p>
 * Every route is owner-scoped: the caller's identity is resolved from the JWT token through
 * {@link CurrentUser}, and feeder ownership is verified before any configuration is accessed.
 * Foreign feeders and foreign configurations always answer with the appropriate 404 problem response,
 * preventing enumeration of foreign resources.
 */
@RestController
@RequestMapping("/feeders/{feederId}/configs")
public class FeederConfigController {

    private final FeederConfigService feederConfigService;

    public FeederConfigController(FeederConfigService feederConfigService) {
        this.feederConfigService = feederConfigService;
    }

    /**
     * Creates a new feeding schedule for the designated feeder. Returns 201 Created with a relative
     * Location header pointing to the created schedule.
     */
    @PostMapping
    public ResponseEntity<FeederConfigResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID feederId,
                                                       @Valid @RequestBody CreateFeederConfigRequest request) {
        FeederConfigResponse response = FeederConfigResponse.from(feederConfigService.create(
                CurrentUser.id(jwt), feederId, request.type(), request.intervalMinutes(),
                request.timeOfDay(), request.weekdays(), request.dose()));
        return ResponseEntity.created(URI.create("/feeders/" + feederId + "/configs/" + response.id())).body(response);
    }

    /**
     * Lists every schedule of the feeder, ordered chronologically (INTERVAL rules first, followed by
     * DAILY_TIME rules in ascending wall-clock time order).
     */
    @GetMapping
    public List<FeederConfigResponse> list(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID feederId) {
        return feederConfigService.list(CurrentUser.id(jwt), feederId).stream()
                .map(FeederConfigResponse::from)
                .toList();
    }

    /**
     * Retrieves a single feeding configuration.
     */
    @GetMapping("/{id}")
    public FeederConfigResponse get(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID feederId,
                                    @PathVariable UUID id) {
        return FeederConfigResponse.from(feederConfigService.get(CurrentUser.id(jwt), feederId, id));
    }

    /**
     * Replaces the scheduling attributes of the configuration.
     */
    @PutMapping("/{id}")
    public FeederConfigResponse update(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID feederId,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody UpdateFeederConfigRequest request) {
        return FeederConfigResponse.from(feederConfigService.update(
                CurrentUser.id(jwt), feederId, id, request.type(), request.intervalMinutes(),
                request.timeOfDay(), request.weekdays(), request.dose()));
    }

    /**
     * Activates a single feeding schedule. Idempotent.
     */
    @PostMapping("/{id}/activate")
    public FeederConfigResponse activate(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID feederId,
                                         @PathVariable UUID id) {
        return FeederConfigResponse.from(feederConfigService.activate(CurrentUser.id(jwt), feederId, id));
    }

    /**
     * Deactivates a single feeding schedule. Idempotent.
     */
    @PostMapping("/{id}/deactivate")
    public FeederConfigResponse deactivate(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID feederId,
                                           @PathVariable UUID id) {
        return FeederConfigResponse.from(feederConfigService.deactivate(CurrentUser.id(jwt), feederId, id));
    }

    /**
     * Removes a feeding configuration. Answers 204 No Content with no body.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID feederId,
                       @PathVariable UUID id) {
        feederConfigService.delete(CurrentUser.id(jwt), feederId, id);
    }
}
