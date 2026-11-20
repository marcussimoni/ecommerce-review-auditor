package br.com.ecommercereviewauditor.service;

import br.com.ecommercereviewauditor.config.DatabaseConfig;
import br.com.ecommercereviewauditor.entities.AvaliacaoProduto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AvaliacaoJdbcService {

    private final DatabaseConfig databaseConfig;

    public AvaliacaoJdbcService(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public List<AvaliacaoProduto> buscarPorProdutoId(String produtoId) {
        String sql = """
                SELECT id, produto_id, nome_produto, vendedor_id, estrelas, comentario, data_avaliacao
                FROM avaliacoes_produto
                WHERE produto_id = ?
                ORDER BY data_avaliacao DESC, id DESC
                """;

        List<AvaliacaoProduto> resultado = new ArrayList<>();

        try (Connection conn = databaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, produtoId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("data_avaliacao");
                    resultado.add(new AvaliacaoProduto(
                            rs.getLong("id"),
                            rs.getString("produto_id"),
                            rs.getString("nome_produto"),
                            rs.getString("vendedor_id"),
                            rs.getInt("estrelas"),
                            rs.getString("comentario"),
                            ts != null ? ts.toLocalDateTime() : null
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar avaliações para o produto " + produtoId, e);
        }

        return resultado;
    }
}