package com.ondemandmonitoring.controlgateway.controller;

import com.ondemandmonitoring.controlgateway.config.ControlGatewayProperties;
import com.ondemandmonitoring.controlgateway.dto.CommandResponse;
import com.ondemandmonitoring.controlgateway.service.FlightControlService;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;

@RestController
@RequestMapping("/api/control/v1/commands")
public class CommandEventController {

    private final FlightControlService flightControlService;
    private final TaskExecutor controlTaskExecutor;
    private final ControlGatewayProperties properties;

    public CommandEventController(FlightControlService flightControlService,
                                  TaskExecutor controlTaskExecutor,
                                  ControlGatewayProperties properties) {
        this.flightControlService = flightControlService;
        this.controlTaskExecutor = controlTaskExecutor;
        this.properties = properties;
    }

    @GetMapping(value = "/{commandId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String commandId,
                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        controlTaskExecutor.execute(() -> stream(commandId, FlightControlController.bearer(authorization), emitter));
        return emitter;
    }

    private void stream(String commandId, String token, SseEmitter emitter) {
        try {
            Iterator<CommandResponse> updates = flightControlService.watchCommand(commandId, token);
            while (updates.hasNext()) {
                CommandResponse update = updates.next();
                emitter.send(SseEmitter.event().name("command").id(commandId).data(update));
                if (update.terminal()) {
                    break;
                }
            }
            emitter.complete();
        } catch (IOException exception) {
            emitter.complete();
        } catch (RuntimeException exception) {
            emitter.completeWithError(exception);
        }
    }
}
