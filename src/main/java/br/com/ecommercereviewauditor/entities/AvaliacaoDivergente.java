package br.com.ecommercereviewauditor.entities;

public record AvaliacaoDivergente(
        Long avaliacaoId,
        Integer estrelas,
        String vendedorId,
        String categoriaFatorExterno,
        String justificativa
) {}