package com.ondemandmonitoring.controlgateway.client;

import com.ondemandmonitoring.controlgateway.exception.ControlGatewayException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BackendControlAuthorizationClient implements ControlAuthorizationClient {

    private final RestClient restClient;

    public BackendControlAuthorizationClient(RestClient backendRestClient) {
        this.restClient = backendRestClient;
    }

    @Override
    public ControlAuthorizationContext authorize(
            String missionId, String droneId, String operatorAccessToken) {
        BackendResponse response = restClient.get()
                .uri(builder -> {
                    builder.path("/api/v1/missions/{missionId}/control-context");
                    if (droneId != null && !droneId.isBlank()) {
                        builder.queryParam("droneId", droneId);
                    }
                    return builder.build(missionId);
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorAccessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, backendResponse) -> {
                    throw new ControlGatewayException(
                            "MISSION_AUTHORIZATION_FAILED",
                            "Backend rejected mission control authorization",
                            backendResponse.getStatusCode().value());
                })
                .body(BackendResponse.class);
        if (response == null || response.data() == null) {
            throw new ControlGatewayException(
                    "MISSION_AUTHORIZATION_FAILED", "Backend returned no control context", 502);
        }
        return response.data();
    }

    private record BackendResponse(boolean success, ControlAuthorizationContext data) {
    }
}
