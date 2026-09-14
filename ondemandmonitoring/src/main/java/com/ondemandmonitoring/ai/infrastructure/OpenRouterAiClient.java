package com.ondemandmonitoring.ai.infrastructure;

import com.ondemandmonitoring.ai.dto.SurveillanceDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class OpenRouterAiClient {

  private final RestClient restClient;

  @Value("${openrouter.api-key}")
  private String apiKey;

  @Value("${openrouter.default-model}")
  private String defaultModel;

  @Value("${openrouter.fallback-models}")
  private List<String> fallbackModels;

  public OpenRouterAiClient(
      @Value("${openrouter.base-url}") String baseUrl,
      @Value("${openrouter.site-url}") String siteUrl,
      @Value("${openrouter.site-name}") String siteName) {

    this.restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader("HTTP-Referer", siteUrl)
        .defaultHeader("X-Title", siteName)
        .build();
  }

  public String generateCompletion(String systemPrompt, String userContent) {
    List<String> models = new ArrayList<>();
    models.add(defaultModel);
    models.addAll(fallbackModels);

    OpenRouterRequest payload = OpenRouterRequest.builder()
        .models(models)
        .temperature(0.1)
        .responseFormat(new ResponseFormat("json_object"))
        .messages(List.of(
            new Message("system", systemPrompt),
            new Message("user", userContent)))
        .build();

    OpenRouterResponse response = restClient.post()
        .uri("/chat/completions")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .body(payload)
        .retrieve()
        .body(OpenRouterResponse.class);

    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
      return response.getChoices().get(0).getMessage().getContent();
    }
    return null;
  }
}
