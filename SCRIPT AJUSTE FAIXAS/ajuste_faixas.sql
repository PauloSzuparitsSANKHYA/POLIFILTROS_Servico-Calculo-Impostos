-- ============================================================================
-- Recalculo de AD_FAIXAPRECO (TGFITE) com base na parte SQL da formula de
-- faixas (custo/desconto/markup - desconto promocional TGFDES aplicado sobre
-- o CUSGER, igual ao buscarAlternativos e ao ItemNotaListener).
--
-- IMPORTANTE: este script NAO aplica o grossup de impostos (ICMS/PIS/COFINS)
-- que o buscarAlternativos e o ItemNotaListener aplicam em Java via
-- ImpostosHelpper - por ser um script SQL standalone, sem acesso a essa
-- engine de impostos. Por isso as faixas calculadas aqui sao intencionalmente
-- menores (sem imposto embutido) do que as faixas exibidas/comparadas nesses
-- dois pontos do addon.
-- Escopo: somente notas de VENDA (TGFCAB.TIPMOV = 'V')
-- Banco alvo: Oracle
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1) SELECT DE CONFERENCIA
--    Mostra apenas os itens cujo AD_FAIXAPRECO mudaria, para validar antes
--    de rodar o UPDATE (secao 2). Rode esta consulta primeiro.
-- ----------------------------------------------------------------------------
WITH ITENS AS (
    SELECT I.NUNOTA, I.SEQUENCIA, I.CODPROD, I.VLRUNIT, I.AD_FAIXAPRECO AS FAIXA_ATUAL, C.CODEMP, C.CODPARC
    FROM TGFITE I
    JOIN TGFCAB C ON C.NUNOTA = I.NUNOTA
    WHERE I.VLRUNIT > 0
      AND I.CODPROD IS NOT NULL
      AND C.TIPMOV = 'V'
),
PRODUTOS AS (
    -- desconto (TGFDES) pode variar por parceiro, entao a chave de calculo
    -- passa a ser (CODEMP, CODPROD, CODPARC) e nao so (CODEMP, CODPROD)
    SELECT DISTINCT CODEMP, CODPROD, CODPARC FROM ITENS
),
FAIXAS_PRODUTO AS (
    SELECT
        P.CODEMP, P.CODPROD, P.CODPARC,
        F.FAIXA1, F.FAIXA2, F.FAIXA3, F.FAIXA4, F.FAIXA5
    FROM PRODUTOS P
    CROSS APPLY (
        -- =========== identico ao SQL_FAIXAS_PRODUTO do ItemNotaListener ===========
        SELECT FAIXA1, FAIXA2, FAIXA3, FAIXA4, FAIXA5 FROM (
            SELECT PMK.CODMARKUP,
                (NVL(CUS.CUSGER, 0) * (1 - NVL(DES.PERCENTUAL, 0) / 100.0)) / NULLIF(1 - ((NVL(PMK.PERCMARKUP, 0) +
                    NVL((SELECT SUM(PERCDESP) FROM AD_DESPOPER WHERE ATIVO = 'S' AND CODEMP = P.CODEMP), 0)) / 100.0), 0) AS PRECO_CALCULADO
            FROM TGFPRO PRO
            LEFT JOIN AD_PERFPRODMKP PER ON PER.ROWID = (
                SELECT P2.ROWID FROM AD_PERFPRODMKP P2
                WHERE (P2.CODPROD = PRO.CODPROD OR P2.CODPROD IS NULL)
                  AND (P2.CODGRUPOPROD = PRO.CODGRUPOPROD OR P2.CODGRUPOPROD IS NULL)
                  AND (P2.CODMARCA = PRO.CODMARCA OR P2.CODMARCA IS NULL)
                  AND (P2.CODPARCFORN = PRO.CODPARCFORN OR P2.CODPARCFORN IS NULL)
                  AND (P2.CODEMP = P.CODEMP OR P2.CODEMP IS NULL)
                  AND P2.PRIORIDADE = (
                      SELECT MAX(P3.PRIORIDADE) FROM AD_PERFPRODMKP P3
                      WHERE (P3.CODPROD = PRO.CODPROD OR P3.CODPROD IS NULL)
                        AND (P3.CODGRUPOPROD = PRO.CODGRUPOPROD OR P3.CODGRUPOPROD IS NULL)
                        AND (P3.CODMARCA = PRO.CODMARCA OR P3.CODMARCA IS NULL)
                        AND (P3.CODPARCFORN = PRO.CODPARCFORN OR P3.CODPARCFORN IS NULL)
                        AND (P3.CODEMP = P.CODEMP OR P3.CODEMP IS NULL)
                  )
                ORDER BY
                    CASE WHEN P2.CODPROD = PRO.CODPROD THEN 1 ELSE 0 END DESC,
                    CASE WHEN P2.CODGRUPOPROD = PRO.CODGRUPOPROD THEN 1 ELSE 0 END DESC,
                    CASE WHEN P2.CODMARCA = PRO.CODMARCA THEN 1 ELSE 0 END DESC,
                    CASE WHEN P2.CODPARCFORN = PRO.CODPARCFORN THEN 1 ELSE 0 END DESC
                FETCH FIRST 1 ROW ONLY
            )
            LEFT JOIN AD_PERMARKUP PMK ON PMK.CODGRUMARKUP = PER.CODGRUMARKUP AND PMK.CODMARKUP <= 5
            LEFT JOIN TGFCUS CUS ON CUS.CODPROD = PRO.CODPROD
                AND CUS.CODEMP = P.CODEMP
                AND CUS.DTATUAL = (SELECT MAX(DTATUAL) FROM TGFCUS WHERE CODPROD = PRO.CODPROD AND CODEMP = P.CODEMP)
            LEFT JOIN TGFDES DES ON DES.ROWID = (
                SELECT D2.ROWID FROM TGFDES D2
                WHERE (D2.CODEMP = P.CODEMP OR D2.CODEMP = 0)
                  AND (D2.CODPARC = P.CODPARC OR D2.CODPARC = 0)
                  AND D2.CODPROD = PRO.CODPROD
                  AND D2.DTINICIAL <= SYSDATE AND D2.DTFINAL >= SYSDATE
                ORDER BY D2.DTFINAL DESC
                FETCH FIRST 1 ROW ONLY
            )
            WHERE PRO.CODPROD = P.CODPROD
        ) PIVOT (
            MAX(PRECO_CALCULADO)
            FOR CODMARKUP IN (1 AS FAIXA1, 2 AS FAIXA2, 3 AS FAIXA3, 4 AS FAIXA4, 5 AS FAIXA5)
        )
        -- ============================================================================
    ) F
),
DIFFS AS (
    SELECT
        I.NUNOTA, I.SEQUENCIA, I.VLRUNIT, I.CODPROD, I.FAIXA_ATUAL,
        FX.FAIXA1, FX.FAIXA2, FX.FAIXA3, FX.FAIXA4, FX.FAIXA5,
        LEAST(NVL(FX.FAIXA1,1e15), NVL(FX.FAIXA2,1e15), NVL(FX.FAIXA3,1e15), NVL(FX.FAIXA4,1e15), NVL(FX.FAIXA5,1e15)) AS MIN_FAIXA,
        ABS(I.VLRUNIT - FX.FAIXA1) AS DIFF1,
        ABS(I.VLRUNIT - FX.FAIXA2) AS DIFF2,
        ABS(I.VLRUNIT - FX.FAIXA3) AS DIFF3,
        ABS(I.VLRUNIT - FX.FAIXA4) AS DIFF4,
        ABS(I.VLRUNIT - FX.FAIXA5) AS DIFF5
    FROM ITENS I
    JOIN FAIXAS_PRODUTO FX ON FX.CODEMP = I.CODEMP AND FX.CODPROD = I.CODPROD AND FX.CODPARC = I.CODPARC
),
MELHOR AS (
    SELECT
        NUNOTA, SEQUENCIA, VLRUNIT, CODPROD, FAIXA_ATUAL, MIN_FAIXA,
        FAIXA1, FAIXA2, FAIXA3, FAIXA4, FAIXA5,
        DIFF1, DIFF2, DIFF3, DIFF4, DIFF5,
        LEAST(NVL(DIFF1,1e15), NVL(DIFF2,1e15), NVL(DIFF3,1e15), NVL(DIFF4,1e15), NVL(DIFF5,1e15)) AS MENOR_DIFF
    FROM DIFFS
),
RESULTADO AS (
    SELECT
        NUNOTA, SEQUENCIA, VLRUNIT, CODPROD, FAIXA_ATUAL, MIN_FAIXA,
        FAIXA1, FAIXA2, FAIXA3, FAIXA4, FAIXA5,
        CASE
            WHEN FAIXA1 IS NOT NULL AND DIFF1 = MENOR_DIFF THEN 1
            WHEN FAIXA2 IS NOT NULL AND DIFF2 = MENOR_DIFF THEN 2
            WHEN FAIXA3 IS NOT NULL AND DIFF3 = MENOR_DIFF THEN 3
            WHEN FAIXA4 IS NOT NULL AND DIFF4 = MENOR_DIFF THEN 4
            WHEN FAIXA5 IS NOT NULL AND DIFF5 = MENOR_DIFF THEN 5
        END AS MELHOR_INDICE
    FROM MELHOR
)
SELECT
    NUNOTA, SEQUENCIA, CODPROD, VLRUNIT,
    FAIXA_ATUAL,
    NOVA_FAIXA,
    FAIXA1, FAIXA2, FAIXA3, FAIXA4, FAIXA5
FROM (
    SELECT
        NUNOTA, SEQUENCIA, CODPROD, VLRUNIT, FAIXA_ATUAL,
        FAIXA1, FAIXA2, FAIXA3, FAIXA4, FAIXA5,
        CASE
            WHEN MELHOR_INDICE IS NULL THEN NULL
            WHEN MIN_FAIXA IS NOT NULL AND VLRUNIT < MIN_FAIXA THEN 0
            ELSE MELHOR_INDICE
        END AS NOVA_FAIXA
    FROM RESULTADO
)
WHERE NOVA_FAIXA IS NOT NULL
  AND (FAIXA_ATUAL IS NULL OR FAIXA_ATUAL <> NOVA_FAIXA)   -- so mostra o que vai mudar
ORDER BY NUNOTA, SEQUENCIA;


-- ----------------------------------------------------------------------------
-- 2) UPDATE (MERGE) DE APLICACAO
--    So roda depois de validar o SELECT acima. Atualiza somente TGFCAB.TIPMOV = 'V'.
-- ----------------------------------------------------------------------------
MERGE INTO TGFITE ITE
USING (
  WITH ITENS AS (
      SELECT I.NUNOTA, I.SEQUENCIA, I.CODPROD, I.VLRUNIT, C.CODEMP, C.CODPARC
      FROM TGFITE I
      JOIN TGFCAB C ON C.NUNOTA = I.NUNOTA
      WHERE I.VLRUNIT > 0
        AND I.CODPROD IS NOT NULL
        AND C.TIPMOV = 'V'
  ),
  PRODUTOS AS (
      -- desconto (TGFDES) pode variar por parceiro, entao a chave de calculo
      -- passa a ser (CODEMP, CODPROD, CODPARC) e nao so (CODEMP, CODPROD)
      SELECT DISTINCT CODEMP, CODPROD, CODPARC FROM ITENS
  ),
  FAIXAS_PRODUTO AS (
      SELECT
          P.CODEMP, P.CODPROD, P.CODPARC,
          F.FAIXA1, F.FAIXA2, F.FAIXA3, F.FAIXA4, F.FAIXA5
      FROM PRODUTOS P
      CROSS APPLY (
          -- =========== identico ao SQL_FAIXAS_PRODUTO do ItemNotaListener ===========
          SELECT FAIXA1, FAIXA2, FAIXA3, FAIXA4, FAIXA5 FROM (
              SELECT PMK.CODMARKUP,
                  (NVL(CUS.CUSGER, 0) * (1 - NVL(DES.PERCENTUAL, 0) / 100.0)) / NULLIF(1 - ((NVL(PMK.PERCMARKUP, 0) +
                      NVL((SELECT SUM(PERCDESP) FROM AD_DESPOPER WHERE ATIVO = 'S' AND CODEMP = P.CODEMP), 0)) / 100.0), 0) AS PRECO_CALCULADO
              FROM TGFPRO PRO
              LEFT JOIN AD_PERFPRODMKP PER ON PER.ROWID = (
                  SELECT P2.ROWID FROM AD_PERFPRODMKP P2
                  WHERE (P2.CODPROD = PRO.CODPROD OR P2.CODPROD IS NULL)
                    AND (P2.CODGRUPOPROD = PRO.CODGRUPOPROD OR P2.CODGRUPOPROD IS NULL)
                    AND (P2.CODMARCA = PRO.CODMARCA OR P2.CODMARCA IS NULL)
                    AND (P2.CODPARCFORN = PRO.CODPARCFORN OR P2.CODPARCFORN IS NULL)
                    AND (P2.CODEMP = P.CODEMP OR P2.CODEMP IS NULL)
                    AND P2.PRIORIDADE = (
                        SELECT MAX(P3.PRIORIDADE) FROM AD_PERFPRODMKP P3
                        WHERE (P3.CODPROD = PRO.CODPROD OR P3.CODPROD IS NULL)
                          AND (P3.CODGRUPOPROD = PRO.CODGRUPOPROD OR P3.CODGRUPOPROD IS NULL)
                          AND (P3.CODMARCA = PRO.CODMARCA OR P3.CODMARCA IS NULL)
                          AND (P3.CODPARCFORN = PRO.CODPARCFORN OR P3.CODPARCFORN IS NULL)
                          AND (P3.CODEMP = P.CODEMP OR P3.CODEMP IS NULL)
                    )
                  ORDER BY
                      CASE WHEN P2.CODPROD = PRO.CODPROD THEN 1 ELSE 0 END DESC,
                      CASE WHEN P2.CODGRUPOPROD = PRO.CODGRUPOPROD THEN 1 ELSE 0 END DESC,
                      CASE WHEN P2.CODMARCA = PRO.CODMARCA THEN 1 ELSE 0 END DESC,
                      CASE WHEN P2.CODPARCFORN = PRO.CODPARCFORN THEN 1 ELSE 0 END DESC
                  FETCH FIRST 1 ROW ONLY
              )
              LEFT JOIN AD_PERMARKUP PMK ON PMK.CODGRUMARKUP = PER.CODGRUMARKUP AND PMK.CODMARKUP <= 5
              LEFT JOIN TGFCUS CUS ON CUS.CODPROD = PRO.CODPROD
                  AND CUS.CODEMP = P.CODEMP
                  AND CUS.DTATUAL = (SELECT MAX(DTATUAL) FROM TGFCUS WHERE CODPROD = PRO.CODPROD AND CODEMP = P.CODEMP)
              LEFT JOIN TGFDES DES ON DES.ROWID = (
                  SELECT D2.ROWID FROM TGFDES D2
                  WHERE (D2.CODEMP = P.CODEMP OR D2.CODEMP = 0)
                    AND (D2.CODPARC = P.CODPARC OR D2.CODPARC = 0)
                    AND D2.CODPROD = PRO.CODPROD
                    AND D2.DTINICIAL <= SYSDATE AND D2.DTFINAL >= SYSDATE
                  ORDER BY D2.DTFINAL DESC
                  FETCH FIRST 1 ROW ONLY
              )
              WHERE PRO.CODPROD = P.CODPROD
          ) PIVOT (
              MAX(PRECO_CALCULADO)
              FOR CODMARKUP IN (1 AS FAIXA1, 2 AS FAIXA2, 3 AS FAIXA3, 4 AS FAIXA4, 5 AS FAIXA5)
          )
          -- ============================================================================
      ) F
  ),
  DIFFS AS (
      SELECT
          I.NUNOTA, I.SEQUENCIA, I.VLRUNIT,
          FX.FAIXA1, FX.FAIXA2, FX.FAIXA3, FX.FAIXA4, FX.FAIXA5,
          LEAST(NVL(FX.FAIXA1,1e15), NVL(FX.FAIXA2,1e15), NVL(FX.FAIXA3,1e15), NVL(FX.FAIXA4,1e15), NVL(FX.FAIXA5,1e15)) AS MIN_FAIXA,
          ABS(I.VLRUNIT - FX.FAIXA1) AS DIFF1,
          ABS(I.VLRUNIT - FX.FAIXA2) AS DIFF2,
          ABS(I.VLRUNIT - FX.FAIXA3) AS DIFF3,
          ABS(I.VLRUNIT - FX.FAIXA4) AS DIFF4,
          ABS(I.VLRUNIT - FX.FAIXA5) AS DIFF5
      FROM ITENS I
      JOIN FAIXAS_PRODUTO FX ON FX.CODEMP = I.CODEMP AND FX.CODPROD = I.CODPROD AND FX.CODPARC = I.CODPARC
  ),
  MELHOR AS (
      SELECT
          NUNOTA, SEQUENCIA, VLRUNIT, MIN_FAIXA,
          FAIXA1, FAIXA2, FAIXA3, FAIXA4, FAIXA5,
          DIFF1, DIFF2, DIFF3, DIFF4, DIFF5,
          LEAST(NVL(DIFF1,1e15), NVL(DIFF2,1e15), NVL(DIFF3,1e15), NVL(DIFF4,1e15), NVL(DIFF5,1e15)) AS MENOR_DIFF
      FROM DIFFS
  ),
  RESULTADO AS (
      SELECT
          NUNOTA, SEQUENCIA, VLRUNIT, MIN_FAIXA,
          -- replica o loop do Java: primeira faixa (1..5) que bate com a menor diferenca, em caso de empate
          CASE
              WHEN FAIXA1 IS NOT NULL AND DIFF1 = MENOR_DIFF THEN 1
              WHEN FAIXA2 IS NOT NULL AND DIFF2 = MENOR_DIFF THEN 2
              WHEN FAIXA3 IS NOT NULL AND DIFF3 = MENOR_DIFF THEN 3
              WHEN FAIXA4 IS NOT NULL AND DIFF4 = MENOR_DIFF THEN 4
              WHEN FAIXA5 IS NOT NULL AND DIFF5 = MENOR_DIFF THEN 5
          END AS MELHOR_INDICE
      FROM MELHOR
  )
  SELECT NUNOTA, SEQUENCIA, NOVA_FAIXA FROM (
      SELECT
          NUNOTA, SEQUENCIA,
          CASE
              WHEN MELHOR_INDICE IS NULL THEN NULL                       -- produto sem nenhuma faixa (sem markup/custo) -> nao mexe
              WHEN MIN_FAIXA IS NOT NULL AND VLRUNIT < MIN_FAIXA THEN 0  -- abaixo da menor faixa -> AD_FAIXAPRECO = 0
              ELSE MELHOR_INDICE
          END AS NOVA_FAIXA
      FROM RESULTADO
  )
  WHERE NOVA_FAIXA IS NOT NULL
) RESULT
ON (ITE.NUNOTA = RESULT.NUNOTA AND ITE.SEQUENCIA = RESULT.SEQUENCIA)
WHEN MATCHED THEN UPDATE SET ITE.AD_FAIXAPRECO = RESULT.NOVA_FAIXA;

COMMIT;
