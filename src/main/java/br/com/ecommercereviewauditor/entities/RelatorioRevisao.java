package br.com.ecommercereviewauditor.entities;

import java.time.LocalDateTime;

public record RelatorioRevisao(
        Long id,
        String produtoId,
        String motivoRevisao,
        String resumoAnalise,
        Integer totalAvaliacoesAnalisadas,
        Integer avaliacoesFatoresExternos,
        String avaliacoesDivergentesIds,
        String detalhesDivergencias,
        LocalDateTime criadoEm
) {}