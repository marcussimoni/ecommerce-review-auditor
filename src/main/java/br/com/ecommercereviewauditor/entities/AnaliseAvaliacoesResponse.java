package br.com.ecommercereviewauditor.entities;

import java.util.List;

public record AnaliseAvaliacoesResponse(
        String produtoId,
        boolean precisaRevisao,
        String motivoPrincipal,
        int totalAvaliacoesAnalisadas,
        int avaliacoesFatoresExternos,
        List<AvaliacaoDivergente> avaliacoesDivergentes,
        String resumo
) {}