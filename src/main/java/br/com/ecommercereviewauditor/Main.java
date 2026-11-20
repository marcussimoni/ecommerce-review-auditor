package br.com.ecommercereviewauditor;

import br.com.ecommercereviewauditor.client.OllamaChatClient;
import br.com.ecommercereviewauditor.config.DatabaseConfig;
import br.com.ecommercereviewauditor.config.H2Console;
import br.com.ecommercereviewauditor.config.OllamaConfig;
import br.com.ecommercereviewauditor.config.PromptBuilder;
import br.com.ecommercereviewauditor.entities.AnaliseAvaliacoesResponse;
import br.com.ecommercereviewauditor.entities.AvaliacaoDivergente;
import br.com.ecommercereviewauditor.entities.AvaliacaoProduto;
import br.com.ecommercereviewauditor.entities.RelatorioRevisao;
import br.com.ecommercereviewauditor.service.AvaliacaoJdbcService;
import br.com.ecommercereviewauditor.service.RelatorioJdbcService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {

	static void main(String[] args) {
		DatabaseConfig dbConfig = new DatabaseConfig();
		dbConfig.migrate();

		OllamaConfig ollamaConfig = new OllamaConfig();
		PromptBuilder promptBuilder = new PromptBuilder();

		AvaliacaoJdbcService avaliacaoService = new AvaliacaoJdbcService(dbConfig);
		RelatorioJdbcService relatorioService = new RelatorioJdbcService(dbConfig);
		OllamaChatClient ollamaChatClient = new OllamaChatClient(ollamaConfig, promptBuilder);

		H2Console.startServer();

		System.out.println("=== Review Agent CLI (Java + JDBC + H2 + Ollama) ===");
		System.out.println("Modelo configurado: " + ollamaConfig.getModel() + " (" + ollamaConfig.getBaseUrl() + ")");
		System.out.println("Prompt ativo      : " + promptBuilder.getSystemPromptPath());
		System.out.println("Comandos:");
		System.out.println("  - <ID_PRODUTO> : analisa avaliações do banco (ex.: PROD-001 a PROD-005)");
		System.out.println("  - relatorios   : lista todos os relatórios de revisão salvos no banco");
		System.out.println("  - testes       : executa casos de teste iniciais no terminal");
		System.out.println("  - sair         : encerra a aplicação");

		try (Scanner scanner = new Scanner(System.in)) {
			while (true) {
				System.out.print("\nComando ou ID do produto: ");
				String entrada = scanner.nextLine().trim();

				if (entrada.equalsIgnoreCase("sair")) {
					System.out.println("Encerrando...");
					H2Console.stopServer();
					break;
				}
				if (entrada.isEmpty()) {
					continue;
				}
				if (entrada.equalsIgnoreCase("relatorios")) {
					listarRelatoriosSalvos(relatorioService);
					continue;
				}
				if (entrada.equalsIgnoreCase("testes")) {
					executarCasosDeTesteIniciais(avaliacaoService, relatorioService, ollamaChatClient);
					continue;
				}

				analisarProdutoDoBanco(entrada, avaliacaoService, relatorioService, ollamaChatClient);
			}
		}
	}

	public static void analisarProdutoDoBanco(
			String produtoId,
			AvaliacaoJdbcService avaliacaoService,
			RelatorioJdbcService relatorioService,
			OllamaChatClient ollamaChatClient
	) {
		List<AvaliacaoProduto> avaliacoes = avaliacaoService.buscarPorProdutoId(produtoId);
		if (avaliacoes.isEmpty()) {
			System.out.println("Nenhuma avaliação encontrada para o produto: " + produtoId);
			return;
		}

		System.out.printf("Analisando %d avaliação(ões) para [%s]...%n", avaliacoes.size(), produtoId);
		AnaliseAvaliacoesResponse analise = ollamaChatClient.analisarAvaliacoes(produtoId, avaliacoes);

		List<AvaliacaoDivergente> divergentesReais = RelatorioJdbcService.filtrarFatoresExternosReais(
				analise.avaliacoesDivergentes());
		boolean precisaRevisao = RelatorioJdbcService.precisaRevisaoReal(
				analise.precisaRevisao(), divergentesReais);

		imprimirResultadoAnalise(analise, divergentesReais, precisaRevisao);

		if (precisaRevisao) {
			RelatorioRevisao salvo = relatorioService.salvarRelatorio(
					analise.produtoId(),
					analise.motivoPrincipal(),
					analise.resumo(),
					analise.totalAvaliacoesAnalisadas(),
					divergentesReais.size(),
					divergentesReais
			);
			System.out.println("-> Relatório persistido no H2 (ID: " + salvo.id()
					+ " | Motivos: " + salvo.motivoRevisao()
					+ " | Avaliações IDs: " + salvo.avaliacoesDivergentesIds() + ")");
		} else {
			System.out.println("-> Nenhuma revisão por fator externo necessária. Relatório não gerado.");
		}
	}

	private static void listarRelatoriosSalvos(RelatorioJdbcService relatorioService) {
		List<RelatorioRevisao> relatorios = relatorioService.listarTodosRelatorios();
		if (relatorios.isEmpty()) {
			System.out.println("Nenhum relatório de revisão salvo até o momento.");
			return;
		}

		System.out.println("\n=== Relatórios de Revisão Salvos (" + relatorios.size() + ") ===");
		for (RelatorioRevisao r : relatorios) {
			System.out.printf("#%d | Produto: %s | Motivos: %s | Externas: %d/%d | IDs Avaliações: [%s]%n",
					r.id(),
					r.produtoId(),
					r.motivoRevisao(),
					r.avaliacoesFatoresExternos(),
					r.totalAvaliacoesAnalisadas(),
					r.avaliacoesDivergentesIds() != null && !r.avaliacoesDivergentesIds().isBlank()
							? r.avaliacoesDivergentesIds()
							: "nenhum");
			if (r.detalhesDivergencias() != null && !r.detalhesDivergencias().isBlank()) {
				System.out.println("    Detalhes: " + r.detalhesDivergencias());
			}
			System.out.println("    Resumo  : " + r.resumoAnalise());
		}
	}

	private static void executarCasosDeTesteIniciais(
			AvaliacaoJdbcService avaliacaoService,
			RelatorioJdbcService relatorioService,
			OllamaChatClient ollamaChatClient
	) {
		Map<String, Boolean> casosEsperados = casosIniciais();
		int acertos = 0;

		System.out.println("\n--- Executando " + casosEsperados.size() + " Casos de Teste Iniciais ---");

		for (Map.Entry<String, Boolean> entry : casosEsperados.entrySet()) {
			String produtoId = entry.getKey();
			boolean esperadoRevisao = entry.getValue();

			List<AvaliacaoProduto> avaliacoes = avaliacaoService.buscarPorProdutoId(produtoId);
			if (avaliacoes.isEmpty()) {
				System.out.printf("[SKIP] %s: sem avaliações no banco.%n", produtoId);
				continue;
			}

			try {
				AnaliseAvaliacoesResponse resp = ollamaChatClient.analisarAvaliacoes(produtoId, avaliacoes);
				List<AvaliacaoDivergente> divergentesReais = RelatorioJdbcService.filtrarFatoresExternosReais(
						resp.avaliacoesDivergentes());
				boolean obtidoRevisao = RelatorioJdbcService.precisaRevisaoReal(
						resp.precisaRevisao(), divergentesReais);

				boolean passou = obtidoRevisao == esperadoRevisao;
				if (passou) {
					acertos++;
					if (obtidoRevisao) {
						relatorioService.salvarRelatorio(
								resp.produtoId(),
								resp.motivoPrincipal(),
								resp.resumo(),
								resp.totalAvaliacoesAnalisadas(),
								divergentesReais.size(),
								divergentesReais
						);
					}
				}
				String motivos = RelatorioJdbcService.consolidarMotivos(
						resp.motivoPrincipal(), divergentesReais);
				System.out.printf("[%s] %s | esperado=%s | obtido=%s | motivos=%s | divergentes=%s%n",
						passou ? "PASS" : "FAIL",
						produtoId,
						esperadoRevisao,
						obtidoRevisao,
						motivos,
						extrairIds(divergentesReais));
			} catch (Exception e) {
				System.out.printf("[ERRO] %s: %s%n", produtoId, e.getMessage());
			}
		}

		System.out.printf("Resumo dos testes: %d/%d passaram.%n", acertos, casosEsperados.size());
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

	private static Map<String, Boolean> casosIniciais() {
		return Map.of(
				"PROD-001", true,
				"PROD-002", false,
				"PROD-003", true,
				"PROD-004", false,
				"PROD-005", true
		);
	}

	private static void imprimirResultadoAnalise(
			AnaliseAvaliacoesResponse analise,
			List<AvaliacaoDivergente> divergentesReais,
			boolean precisaRevisao
	) {
		String motivosConsolidados = RelatorioJdbcService.consolidarMotivos(
				analise.motivoPrincipal(), divergentesReais);

		System.out.println("--------------------------------------------------");
		System.out.println("Produto ID           : " + analise.produtoId());
		System.out.println("Precisa de revisão?  : " + precisaRevisao);
		System.out.println("Motivos identificados  : " + motivosConsolidados);
		System.out.println("Avaliações externas  : " + divergentesReais.size()
				+ " de " + analise.totalAvaliacoesAnalisadas());
		System.out.println("IDs divergentes      : " + extrairIds(divergentesReais));
		if (!divergentesReais.isEmpty()) {
			for (AvaliacaoDivergente d : divergentesReais) {
				System.out.printf("   - Avaliação #%d (%d★ | vend=%s | %s): %s%n",
						d.avaliacaoId(), d.estrelas(), d.vendedorId(),
						d.categoriaFatorExterno(), d.justificativa());
			}
		}
		System.out.println("Resumo               : " + analise.resumo());
		System.out.println("--------------------------------------------------");
	}

	public static AvaliacaoProduto avaliacaoMock(
			Long id, String produtoId, String nomeProduto, String vendedorId, int estrelas, String comentario
	) {
		return new AvaliacaoProduto(id, produtoId, nomeProduto, vendedorId, estrelas, comentario, LocalDateTime.now());
	}
}
