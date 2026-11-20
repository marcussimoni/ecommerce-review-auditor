package br.com.ecommercereviewauditor.client;

import br.com.ecommercereviewauditor.config.OllamaConfig;
import br.com.ecommercereviewauditor.config.PromptBuilder;
import br.com.ecommercereviewauditor.entities.AnaliseAvaliacoesResponse;
import br.com.ecommercereviewauditor.entities.AvaliacaoProduto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class OllamaChatClient {

    private final OllamaConfig config;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public OllamaChatClient(OllamaConfig config, PromptBuilder promptBuilder) {
        this.config = config;
        this.promptBuilder = promptBuilder;
        this.objectMapper = new ObjectMapper();
    }

    public AnaliseAvaliacoesResponse analisarAvaliacoes(String produtoId, List<AvaliacaoProduto> avaliacoes) {
        try {
            ObjectNode payload = buildRequestPayload(produtoId, avaliacoes);
            String requestBody = objectMapper.writeValueAsString(payload);

            String endpoint = config.getBaseUrl() + "/v1/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = config.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Erro na chamada ao Ollama (" + response.statusCode() + "): " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String contentJson = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();

            if (contentJson == null || contentJson.isBlank()) {
                throw new RuntimeException("Resposta vazia em choices[0].message.content do Ollama");
            }

            return objectMapper.readValue(contentJson, AnaliseAvaliacoesResponse.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao analisar avaliações via /v1/chat/completions", e);
        }
    }

    private ObjectNode buildRequestPayload(String produtoId, List<AvaliacaoProduto> avaliacoes) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getModel());
        root.put("temperature", config.getTemperature());
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", promptBuilder.buildSystemPrompt());

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", promptBuilder.buildUserPrompt(produtoId, avaliacoes));

        root.set("response_format", buildJsonSchemaResponseFormat());
        return root;
    }

    private ObjectNode buildJsonSchemaResponseFormat() {
        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_schema");

        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", "analise_avaliacoes_produto");
        jsonSchema.put("strict", true);

        ObjectNode schema = jsonSchema.putObject("schema");
        schema.put("type", "object");

        ObjectNode props = schema.putObject("properties");
        props.putObject("produtoId").put("type", "string");
        props.putObject("precisaRevisao").put("type", "boolean");

        ObjectNode motivo = props.putObject("motivoPrincipal");
        motivo.put("type", "string");
        ArrayNode enumValues = motivo.putArray("enum");
        enumValues.add("NENHUM");
        enumValues.add("LOGISTICA_ENTREGA");
        enumValues.add("EMBALAGEM_DANIFICADA");
        enumValues.add("ATENDIMENTO_VENDEDOR");
        enumValues.add("MISTO_EXTERNO");

        props.putObject("totalAvaliacoesAnalisadas").put("type", "integer");
        props.putObject("avaliacoesFatoresExternos").put("type", "integer");

        ObjectNode divergentesProp = props.putObject("avaliacoesDivergentes");
        divergentesProp.put("type", "array");

        ObjectNode itemSchema = divergentesProp.putObject("items");
        itemSchema.put("type", "object");

        ObjectNode itemProps = itemSchema.putObject("properties");
        itemProps.putObject("avaliacaoId").put("type", "integer");
        itemProps.putObject("estrelas").put("type", "integer");
        itemProps.putObject("vendedorId").put("type", "string");
        itemProps.putObject("categoriaFatorExterno").put("type", "string");
        itemProps.putObject("justificativa").put("type", "string");

        ArrayNode itemRequired = itemSchema.putArray("required");
        itemRequired.add("avaliacaoId");
        itemRequired.add("estrelas");
        itemRequired.add("vendedorId");
        itemRequired.add("categoriaFatorExterno");
        itemRequired.add("justificativa");
        itemSchema.put("additionalProperties", false);

        props.putObject("resumo").put("type", "string");

        ArrayNode required = schema.putArray("required");
        required.add("produtoId");
        required.add("precisaRevisao");
        required.add("motivoPrincipal");
        required.add("totalAvaliacoesAnalisadas");
        required.add("avaliacoesFatoresExternos");
        required.add("avaliacoesDivergentes");
        required.add("resumo");

        schema.put("additionalProperties", false);
        return responseFormat;
    }
}