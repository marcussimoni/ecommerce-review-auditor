package br.com.ecommercereviewauditor.config;

import br.com.ecommercereviewauditor.entities.AvaliacaoProduto;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PromptBuilder {

    private final String systemPromptPath;
    private final String cachedSystemPrompt;

    public PromptBuilder() {
        this(resolvePromptPathFromYaml());
    }

    public PromptBuilder(String systemPromptPath) {
        this.systemPromptPath = systemPromptPath;
        this.cachedSystemPrompt = loadSystemPrompt(systemPromptPath);
    }

    @SuppressWarnings("unchecked")
    private static String resolvePromptPathFromYaml() {
        try (InputStream in = PromptBuilder.class.getClassLoader().getResourceAsStream("application.yaml")) {
            if (in == null) {
                return "prompts/system-prompt-v2.txt";
            }
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root != null && root.get("prompt") instanceof Map<?, ?> promptMap) {
                Object fileObj = promptMap.get("system-file");
                if (fileObj != null && !fileObj.toString().isBlank()) {
                    return fileObj.toString().trim();
                }
            }
            return "prompts/system-prompt-v3.txt";
        } catch (Exception e) {
            throw new RuntimeException("Falha ao ler prompt.system-file no application.yaml", e);
        }
    }

    private String loadSystemPrompt(String path) {
        try {
            Path fsPath = Path.of(path);
            if (Files.exists(fsPath) && Files.isRegularFile(fsPath)) {
                return Files.readString(fsPath, StandardCharsets.UTF_8).trim();
            }

            String normalized = path.startsWith("classpath:") ? path.substring("classpath:".length()) : path;
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }

            try (InputStream in = getClass().getClassLoader().getResourceAsStream(normalized)) {
                if (in != null) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
            }

            throw new IllegalStateException("Arquivo de system prompt não encontrado: " + path);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar system prompt do arquivo: " + path, e);
        }
    }

    public String getSystemPromptPath() {
        return systemPromptPath;
    }

    public String buildSystemPrompt() {
        return cachedSystemPrompt;
    }

    public String buildUserPrompt(String produtoId, List<AvaliacaoProduto> avaliacoes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analise as avaliações do produtoId: ").append(produtoId).append("\n");
        sb.append("Quantidade de avaliações: ").append(avaliacoes.size()).append("\n\n");
        sb.append("<DADOS_AVALIACOES>\n");

        for (AvaliacaoProduto av : avaliacoes) {
            sb.append("- id=").append(av.id())
                    .append(" | estrelas=").append(av.estrelas())
                    .append(" | vendedorId=").append(av.vendedorId() != null ? av.vendedorId() : "N/A")
                    .append(" | comentario=").append(sanitizeText(av.comentario()))
                    .append("\n");
        }

        sb.append("</DADOS_AVALIACOES>");
        return sb.toString();
    }

    private String sanitizeText(String texto) {
        if (texto == null) return "";
        return texto.replaceAll("[\\r\\n]+", " ").trim();
    }
}