package br.com.ecommercereviewauditor;

import br.com.ecommercereviewauditor.client.OllamaChatClient;
import br.com.ecommercereviewauditor.config.DatabaseConfig;
import br.com.ecommercereviewauditor.config.OllamaConfig;
import br.com.ecommercereviewauditor.config.PromptBuilder;
import br.com.ecommercereviewauditor.entities.AnaliseAvaliacoesResponse;
import br.com.ecommercereviewauditor.entities.AvaliacaoDivergente;
import br.com.ecommercereviewauditor.entities.AvaliacaoProduto;
import br.com.ecommercereviewauditor.service.AvaliacaoJdbcService;
import br.com.ecommercereviewauditor.service.RelatorioJdbcService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ReviewAgentTestIT {

    private final AvaliacaoJdbcService avaliacaoService;
    private final OllamaChatClient ollamaChatClient;
    private static final DatabaseConfig dbConfig;

    static {
        dbConfig = new DatabaseConfig();
    }

    public ReviewAgentTestIT(){
        OllamaConfig ollamaConfig = new OllamaConfig();
        PromptBuilder promptBuilder = new PromptBuilder();

        avaliacaoService = new AvaliacaoJdbcService(dbConfig);
        ollamaChatClient = new OllamaChatClient(ollamaConfig, promptBuilder);
    }

    @BeforeAll
    public static void init() {
        dbConfig.migrate();
    }

    @ParameterizedTest(name = "[{index}] {0} -> esperado={1}")
    @MethodSource("provedorCasosIniciais")
    void executarCasosDeTesteIniciais(String produtoId, boolean esperadoRevisao) {

        List<AvaliacaoProduto> avaliacoes = avaliacaoService.buscarPorProdutoId(produtoId);

        AnaliseAvaliacoesResponse resp = ollamaChatClient.analisarAvaliacoes(produtoId, avaliacoes);
        List<AvaliacaoDivergente> divergentesReais = RelatorioJdbcService.filtrarFatoresExternosReais(
                resp.avaliacoesDivergentes());
        boolean obtidoRevisao = RelatorioJdbcService.precisaRevisaoReal(
                resp.precisaRevisao(), divergentesReais);


        String motivos = RelatorioJdbcService.consolidarMotivos(
                resp.motivoPrincipal(), divergentesReais);
        System.out.printf("[%s] %s | esperado=%s | obtido=%s | motivos=%s | divergentes=%s%n",
                esperadoRevisao == obtidoRevisao ? "PASS" : "FAIL",
                produtoId,
                esperadoRevisao,
                obtidoRevisao,
                motivos,
                extrairIds(divergentesReais));

        Assertions.assertEquals(esperadoRevisao, obtidoRevisao);

    }

    private static Stream<Arguments> provedorCasosIniciais() {
        return casosIniciais().entrySet().stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    private static Map<String, Boolean> casosIniciais() {
        Map<String, Boolean> casos = new LinkedHashMap<>();
        casos.put("PROD-001", true);
        casos.put("PROD-002", false);
        casos.put("PROD-003", true);
        casos.put("PROD-004", false);
        casos.put("PROD-005", true);
        casos.put("PROD-006", false);
        casos.put("PROD-007", true);
        casos.put("PROD-008", true);
        casos.put("PROD-009", true);
        casos.put("PROD-010", false);
        casos.put("PROD-011", true);
        casos.put("PROD-012", false);
        casos.put("PROD-013", true);
        casos.put("PROD-014", true);
        casos.put("PROD-015", true);
        return Collections.unmodifiableMap(casos);
    }

    private static String extrairIds(List<AvaliacaoDivergente> divergentes) {
        if (divergentes == null || divergentes.isEmpty()) {
            return "[]";
        }
        return divergentes.stream()
                .map(d -> String.valueOf(d.avaliacaoId()))
                .reduce((a, b) -> a + ", " + b)
                .map(s -> "[" + s + "]")
                .orElse("[]");
    }

}
