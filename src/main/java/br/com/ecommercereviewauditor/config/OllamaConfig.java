package br.com.ecommercereviewauditor.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

public class OllamaConfig {

    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final double temperature;
    private final HttpClient httpClient;

    @SuppressWarnings("unchecked")
    public OllamaConfig() {
        Map<String, Object> root = loadYaml();
        Object ollamaObj = root.get("ollama");
        Map<String, Object> ollama = (ollamaObj instanceof Map<?, ?>)
                ? (Map<String, Object>) ollamaObj
                : Map.of();

        String rawUrl = ollama.getOrDefault("base-url", "http://localhost:11434").toString().trim();
        this.baseUrl = rawUrl.endsWith("/") ? rawUrl.substring(0, rawUrl.length() - 1) : rawUrl;
        this.model = ollama.getOrDefault("model", "llama3.1:8b").toString().trim();
        this.timeoutSeconds = Integer.parseInt(ollama.getOrDefault("timeout-seconds", 120).toString());
        this.temperature = Double.parseDouble(ollama.getOrDefault("temperature", 0.1).toString());

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(this.timeoutSeconds, 30)))
                .build();
    }

    private Map<String, Object> loadYaml() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            if (in == null) {
                throw new IllegalStateException("application.yaml não encontrado em src/main/resources");
            }
            Yaml yaml = new Yaml();
            Map<String, Object> loaded = yaml.load(in);
            return loaded != null ? loaded : Map.of();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar configuração do Ollama em application.yaml", e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public double getTemperature() {
        return temperature;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }
}