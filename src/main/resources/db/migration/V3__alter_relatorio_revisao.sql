ALTER TABLE relatorios_revisao
    ADD COLUMN IF NOT EXISTS avaliacoes_divergentes_ids VARCHAR(500);

ALTER TABLE relatorios_revisao
    ADD COLUMN IF NOT EXISTS detalhes_divergencias VARCHAR(2000);