package br.com.ecommercereviewauditor.entities;

import java.time.LocalDateTime;

public record AvaliacaoProduto(
        Long id,
        String produtoId,
        String nomeProduto,
        String vendedorId,
        Integer estrelas,
        String comentario,
        LocalDateTime dataAvaliacao
) {}