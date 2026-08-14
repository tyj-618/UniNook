package com.uninook.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campuscircle.ai")
public class AiProperties {

    private String provider = "mock";
    private String baseUrl;
    private String apiKey;
    private String model;
    private int timeoutSeconds = 20;
    private int maxRetries = 1;
    private int maxRequestsPerMinute = 5;
    private int maxOutputTokens = 600;
    private int streamReadTimeoutSeconds = 90;
    private int chatSessionTtlSeconds = 1800;
    private int chatSessionMaxMessages = 12;
    private int agentMaxSteps = 6;
    private int agentMaxValidationRetries = 3;
    private boolean structuredOutput;
    private Boolean enableThinking;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getStreamReadTimeoutSeconds() {
        return streamReadTimeoutSeconds;
    }

    public void setStreamReadTimeoutSeconds(int streamReadTimeoutSeconds) {
        this.streamReadTimeoutSeconds = streamReadTimeoutSeconds;
    }

    public int getChatSessionTtlSeconds() {
        return chatSessionTtlSeconds;
    }

    public void setChatSessionTtlSeconds(int chatSessionTtlSeconds) {
        this.chatSessionTtlSeconds = chatSessionTtlSeconds;
    }

    public int getChatSessionMaxMessages() {
        return chatSessionMaxMessages;
    }

    public void setChatSessionMaxMessages(int chatSessionMaxMessages) {
        this.chatSessionMaxMessages = chatSessionMaxMessages;
    }

    public int getAgentMaxSteps() {
        return agentMaxSteps;
    }

    public void setAgentMaxSteps(int agentMaxSteps) {
        this.agentMaxSteps = agentMaxSteps;
    }

    public int getAgentMaxValidationRetries() {
        return agentMaxValidationRetries;
    }

    public void setAgentMaxValidationRetries(int agentMaxValidationRetries) {
        this.agentMaxValidationRetries = agentMaxValidationRetries;
    }

    public boolean isStructuredOutput() {
        return structuredOutput;
    }

    public void setStructuredOutput(boolean structuredOutput) {
        this.structuredOutput = structuredOutput;
    }

    public Boolean getEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }
}
