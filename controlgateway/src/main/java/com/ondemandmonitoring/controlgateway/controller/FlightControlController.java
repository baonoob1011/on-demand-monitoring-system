package com.ondemandmonitoring.controlgateway.controller;

import com.ondemandmonitoring.controlgateway.dto.CommandRequest;
import com.ondemandmonitoring.controlgateway.dto.CommandResponse;
import com.ondemandmonitoring.controlgateway.dto.HealthResponse;
import com.ondemandmonitoring.controlgateway.dto.LocalMediaResponse;
import com.ondemandmonitoring.controlgateway.service.FlightControlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/control/v1")
public class FlightControlController {

    private final FlightControlService flightControlService;

    public FlightControlController(FlightControlService flightControlService) {
        this.flightControlService = flightControlService;
    }

    @GetMapping("/health")
    HealthResponse health(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return flightControlService.health(bearer(authorization));
    }

    @GetMapping("/missions/{missionId}/media")
    List<LocalMediaResponse> listMedia(@PathVariable String missionId,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        return flightControlService.listMedia(missionId, bearer(authorization));
    }

    @PostMapping("/missions/{missionId}/images")
    CommandResponse captureImage(@PathVariable String missionId, @Valid @RequestBody CommandRequest request,
                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        return flightControlService.captureImage(
                request.commandId(), missionId, request.droneId(), bearer(authorization));
    }

    @PostMapping("/missions/{missionId}/videos/start")
    CommandResponse startVideo(@PathVariable String missionId, @Valid @RequestBody CommandRequest request,
                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        return flightControlService.startVideo(
                request.commandId(), missionId, request.droneId(), bearer(authorization));
    }

    @PostMapping("/missions/{missionId}/videos/stop")
    CommandResponse stopVideo(@PathVariable String missionId, @Valid @RequestBody CommandRequest request,
                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        return flightControlService.stopVideo(
                request.commandId(), missionId, request.droneId(), bearer(authorization));
    }

    @PostMapping("/media/{localMediaId}/discard")
    CommandResponse discard(@PathVariable String localMediaId, @Valid @RequestBody CommandRequest request,
                            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return flightControlService.discardMedia(request.commandId(), localMediaId, bearer(authorization));
    }

    @PostMapping("/media/{localMediaId}/upload")
    ResponseEntity<CommandResponse> upload(@PathVariable String localMediaId,
                                           @Valid @RequestBody CommandRequest request,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        CommandResponse response = flightControlService.uploadMedia(
                request.commandId(), localMediaId, bearer(authorization));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    static String bearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return authorization.substring(7).trim();
    }
}
