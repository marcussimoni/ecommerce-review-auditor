package br.com.ecommercereviewauditor.service;

import br.com.ecommercereviewauditor.config.DatabaseConfig;
import br.com.ecommercereviewauditor.entities.AvaliacaoDivergente;
import br.com.ecommercereviewauditor.entities.RelatorioRevisao;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RelatorioJdbcService {

    private final DatabaseConfig databaseConfig;

    private static final Set<String> CATEGORIAS_NAO_EXTERNAS = Set.of(
            "NENHUM",
            "NONE",
            "PRODUTO",
            "DEFEITO_PRODUTO",
            "DEFEITO",
            "QUALIDADE_PRODUTO"
    );

    public RelatorioJdbcService(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public static List<AvaliacaoDivergente> filtrarFatoresExternosReais(List<AvaliacaoDivergente> divergentes) {
        if (divergentes == null || divergentes.isEmpty()) {
            return List.of();
        }
        return divergentes.stream()
                .filter(d -> d != null && d.categoriaFatorExterno() != null)
                .filter(d -> {
                    String normalizada = d.categoriaFatorExterno().trim().toUpperCase();
                    return !normalizada.isBlank() && !CATEGORIAS_NAO_EXTERNAS.contains(normalizada);
                })
                .toList();
    }

    public static boolean precisaRevisaoReal(boolean precisaRevisaoModelo, List<AvaliacaoDivergente> divergentes) {
        List<AvaliacaoDivergente> reais = filtrarFatoresExternosReais(divergentes);
        return precisaRevisaoModelo && !reais.isEmpty();
    }

    public static String consolidarMotivos(String motivoPrincipal, List<AvaliacaoDivergente> avaliacoesDivergentes) {
        List<AvaliacaoDivergente> reais = filtrarFatoresExternosReais(avaliacoesDivergentes);

        String motivosDistintos = reais.stream()
                .map(AvaliacaoDivergente::categoriaFatorExterno)
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase())
                .distinct()
                .collect(Collectors.joining(", "));

        if (!motivosDistintos.isBlank()) {
            return motivosDistintos;
        }

        if (motivoPrincipal != null && !motivoPrincipal.isBlank()) {
            String catPrincipal = motivoPrincipal.trim().toUpperCase();
            if (!CATEGORIAS_NAO_EXTERNAS.contains(catPrincipal)) {
                return catPrincipal;
            }
        }
        return "NENHUM";
    }

    public RelatorioRevisao salvarRelatorio(
            String produtoId,
            String motivoRevisao,
            String resumoAnalise,
            int totalAvaliacoesAnalisadas,
            int avaliacoesFatoresExternos,
            List<AvaliacaoDivergente> avaliacoesDivergentes
    ) {
        List<AvaliacaoDivergente> reais = filtrarFatoresExternosReais(avaliacoesDivergentes);
        String motivoConsolidado = consolidarMotivos(motivoRevisao, reais);
        int quantidadeReal = reais.isEmpty() ? 0 : reais.size();

        String idsFormatados = reais.stream()
                .map(d -> String.valueOf(d.avaliacaoId()))
                .collect(Collectors.joining(", "));

        String detalhesFormatados = reais.stream()
                .map(d -> String.format("[ID %s | %s★ | vend=%s | %s] %s",
                        d.avaliacaoId(),
                        d.estrelas(),
                        d.vendedorId() != null ? d.vendedorId() : "N/A",
                        d.categoriaFatorExterno(),
                        d.justificativa()))
                .collect(Collectors.joining(" | "));

        String sql = """
            INSERT INTO relatorios_revisao (
                produto_id,
                motivo_revisao,
                resumo_analise,
                total_avaliacoes_analisadas,
                avaliacoes_fatores_externos,
                avaliacoes_divergentes_ids,
                detalhes_divergencias
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, produtoId);
            stmt.setString(2, motivoConsolidado);
            stmt.setString(3, resumoAnalise);
            stmt.setInt(4, totalAvaliacoesAnalisadas);
            stmt.setInt(5, quantidadeReal);
            stmt.setString(6, idsFormatados);
            stmt.setString(7, detalhesFormatados);
            stmt.executeUpdate();

            Long geradoId = null;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    geradoId = keys.getLong(1);
                }
            }

            return new RelatorioRevisao(
                    geradoId,
                    produtoId,
                    motivoConsolidado,
                    resumoAnalise,
                    totalAvaliacoesAnalisadas,
                    quantidadeReal,
                    idsFormatados,
                    detalhesFormatados,
                    LocalDateTime.now()
            );
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar relatório de revisão para o produto " + produtoId, e);
        }
    }

    public List<RelatorioRevisao> listarPorProdutoId(String produtoId) {
        String sql = """
            SELECT id, produto_id, motivo_revisao, resumo_analise,
                   total_avaliacoes_analisadas, avaliacoes_fatores_externos,
                   avaliacoes_divergentes_ids, detalhes_divergencias, criado_em
            FROM relatorios_revisao
            WHERE produto_id = ?
            ORDER BY criado_em DESC, id DESC
            """;

        List<RelatorioRevisao> lista = new ArrayList<>();

        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, produtoId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar relatórios do produto " + produtoId, e);
        }

        return lista;
    }

    public List<RelatorioRevisao> listarTodosRelatorios() {
        String sql = """
            SELECT id, produto_id, motivo_revisao, resumo_analise,
                   total_avaliacoes_analisadas, avaliacoes_fatores_externos,
                   avaliacoes_divergentes_ids, detalhes_divergencias, criado_em
            FROM relatorios_revisao
            ORDER BY criado_em DESC, id DESC
            """;

        List<RelatorioRevisao> lista = new ArrayList<>();

        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                lista.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar todos os relatórios de revisão", e);
        }

        return lista;
    }

    private RelatorioRevisao mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("criado_em");
        return new RelatorioRevisao(
                rs.getLong("id"),
                rs.getString("produto_id"),
                rs.getString("motivo_revisao"),
                rs.getString("resumo_analise"),
                rs.getInt("total_avaliacoes_analisadas"),
                rs.getInt("avaliacoes_fatores_externos"),
                rs.getString("avaliacoes_divergentes_ids"),
                rs.getString("detalhes_divergencias"),
                ts != null ? ts.toLocalDateTime() : null
        );
    }
}