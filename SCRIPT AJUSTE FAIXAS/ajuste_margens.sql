-- ============================================================================
-- Recalculo de AD_MARGEMBRUTA e AD_MARGEMLIQ (TGFITE) para itens ja gravados.
--
-- Mesma regra aplicada pelo ItemNotaListener.atualizarMargens:
--   BRUTA = (1 - CUSGER*(1-DESC/100) / (VLRUNIT*(1-ICMS/100)*(1-(PIS+COFINS)/100))) * 100
--   LIQ   = BRUTA - SUM(AD_DESPOPER.PERCDESP)            (ambas ROUND 4)
--   VLRUNIT <= 0 -> 0 / 0
--
--   CUSGER : ultimo TGFCUS do produto na empresa da nota
--   DESC   : TGFDES vigente HOJE (TRUNC(SYSDATE)), igual a antiga expressao
--   ICMS/PIS/COFINS : TGFDIN do item (CODIMP 1, 6, 7). Se o item nao tiver
--            nenhum registro na TGFDIN, usa TGFITE.ALIQICMS e PIS/COFINS
--            da TGFIFE (mesmo fallback do listener).
--
-- Itens com preco liquido de impostos = 0 (ICMS ou PIS+COFINS = 100%) sao
-- ignorados, como no listener.
--
-- Escopo: somente PEDIDOS (TGFCAB.TIPMOV = 'P'). Para incluir outros tipos,
-- ajuste o filtro C.TIPMOV nas duas secoes.
-- Banco alvo: Oracle 12c+
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1) SELECT DE CONFERENCIA
--    Mostra apenas os itens cujas margens mudariam. Rode antes da secao 2.
-- ----------------------------------------------------------------------------
WITH BASE AS (
    SELECT
        I.NUNOTA,
        I.SEQUENCIA,
        I.CODPROD,
        I.VLRUNIT,
        I.AD_MARGEMBRUTA AS MARGEMBRUTA_ATUAL,
        I.AD_MARGEMLIQ   AS MARGEMLIQ_ATUAL,
        NVL((SELECT MAX(CUS.CUSGER) KEEP (DENSE_RANK LAST ORDER BY CUS.DTATUAL)
             FROM TGFCUS CUS
             WHERE CUS.CODPROD = I.CODPROD
               AND CUS.CODEMP = NVL(C.CODEMP, 1)), 0) AS CUSGER,
        NVL((SELECT DES.PERCENTUAL
             FROM TGFDES DES
             WHERE DES.CODPROD = I.CODPROD
               AND (DES.CODEMP = NVL(C.CODEMP, 1) OR DES.CODEMP = 0)
               AND (DES.CODPARC = NVL(C.CODPARC, 0) OR DES.CODPARC = 0)
               AND TRUNC(SYSDATE) BETWEEN DES.DTINICIAL AND DES.DTFINAL
             ORDER BY DES.DTFINAL DESC
             FETCH FIRST 1 ROW ONLY), 0) AS PERCDESC,
        NVL((SELECT SUM(DSP.PERCDESP)
             FROM AD_DESPOPER DSP
             WHERE DSP.ATIVO = 'S'
               AND DSP.CODEMP = NVL(C.CODEMP, 1)), 0) AS PERCDESP,
        CASE
            WHEN EXISTS (SELECT 1 FROM TGFDIN D WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA) THEN
                NVL((SELECT MAX(D.ALIQUOTA) FROM TGFDIN D
                     WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA AND D.CODIMP = 1), 0)
            ELSE NVL(I.ALIQICMS, 0)
        END AS ALIQ_ICMS,
        CASE
            WHEN EXISTS (SELECT 1 FROM TGFDIN D WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA) THEN
                NVL((SELECT MAX(D.ALIQUOTA) FROM TGFDIN D
                     WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA AND D.CODIMP = 6), 0)
              + NVL((SELECT MAX(D.ALIQUOTA) FROM TGFDIN D
                     WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA AND D.CODIMP = 7), 0)
            ELSE
                NVL((SELECT IFE.ALIQ
                     FROM TGFIFE IFE
                     JOIN TGFPRO PRO ON PRO.CODPROD = I.CODPROD
                     WHERE IFE.NOMEIMP = 'PIS'
                       AND IFE.GRUPOIMP = PRO.GRUPOPIS
                       AND IFE.ENTSAI IN ('A', 'S')
                       AND (IFE.CODEMP = 0 OR IFE.CODEMP = NVL(C.CODEMP, 1))
                       AND (IFE.CODPARC = 0 OR IFE.CODPARC = NVL(C.CODPARC, 0))
                       AND (IFE.CODTIPOPER = 0 OR IFE.CODTIPOPER = NVL(C.CODTIPOPER, 0))
                     ORDER BY IFE.CODTIPOPER DESC, IFE.CODPARC DESC, IFE.CODEMP DESC
                     FETCH FIRST 1 ROW ONLY), 0)
              + NVL((SELECT IFE.ALIQ
                     FROM TGFIFE IFE
                     JOIN TGFPRO PRO ON PRO.CODPROD = I.CODPROD
                     WHERE IFE.NOMEIMP = 'COFINS'
                       AND IFE.GRUPOIMP = PRO.GRUPOCOFINS
                       AND IFE.ENTSAI IN ('A', 'S')
                       AND (IFE.CODEMP = 0 OR IFE.CODEMP = NVL(C.CODEMP, 1))
                       AND (IFE.CODPARC = 0 OR IFE.CODPARC = NVL(C.CODPARC, 0))
                       AND (IFE.CODTIPOPER = 0 OR IFE.CODTIPOPER = NVL(C.CODTIPOPER, 0))
                     ORDER BY IFE.CODTIPOPER DESC, IFE.CODPARC DESC, IFE.CODEMP DESC
                     FETCH FIRST 1 ROW ONLY), 0)
        END AS ALIQ_PISCOFINS
    FROM TGFITE I
    JOIN TGFCAB C ON C.NUNOTA = I.NUNOTA
    WHERE I.CODPROD IS NOT NULL
      AND C.TIPMOV = 'P'
),
CALC AS (
    SELECT
        B.*,
        B.CUSGER * (1 - B.PERCDESC / 100) AS CUSTO_LIQ,
        B.VLRUNIT * (1 - B.ALIQ_ICMS / 100) * (1 - B.ALIQ_PISCOFINS / 100) AS PRECO_LIQ
    FROM BASE B
),
RESULT AS (
    SELECT
        CALC.*,
        CASE WHEN NVL(VLRUNIT, 0) <= 0 THEN 0
             ELSE ROUND((1 - CUSTO_LIQ / PRECO_LIQ) * 100, 4) END AS NOVA_MARGEMBRUTA,
        CASE WHEN NVL(VLRUNIT, 0) <= 0 THEN 0
             ELSE ROUND((1 - CUSTO_LIQ / PRECO_LIQ) * 100 - PERCDESP, 4) END AS NOVA_MARGEMLIQ
    FROM CALC
    WHERE NVL(VLRUNIT, 0) <= 0 OR PRECO_LIQ <> 0
)
SELECT
    NUNOTA, SEQUENCIA, CODPROD, VLRUNIT,
    CUSGER, PERCDESC, ALIQ_ICMS, ALIQ_PISCOFINS, PERCDESP,
    MARGEMBRUTA_ATUAL, NOVA_MARGEMBRUTA,
    MARGEMLIQ_ATUAL,   NOVA_MARGEMLIQ
FROM RESULT
WHERE DECODE(MARGEMBRUTA_ATUAL, NOVA_MARGEMBRUTA, 0, 1) = 1
   OR DECODE(MARGEMLIQ_ATUAL,   NOVA_MARGEMLIQ,   0, 1) = 1
ORDER BY NUNOTA, SEQUENCIA;


-- ----------------------------------------------------------------------------
-- 2) UPDATE (MERGE) DE APLICACAO
--    Grava as margens recalculadas. UPDATE direto nao dispara o listener.
-- ----------------------------------------------------------------------------
MERGE INTO TGFITE ITE
USING (
    WITH BASE AS (
        SELECT
            I.NUNOTA,
            I.SEQUENCIA,
            I.VLRUNIT,
            I.AD_MARGEMBRUTA AS MARGEMBRUTA_ATUAL,
            I.AD_MARGEMLIQ   AS MARGEMLIQ_ATUAL,
            NVL((SELECT MAX(CUS.CUSGER) KEEP (DENSE_RANK LAST ORDER BY CUS.DTATUAL)
                 FROM TGFCUS CUS
                 WHERE CUS.CODPROD = I.CODPROD
                   AND CUS.CODEMP = NVL(C.CODEMP, 1)), 0) AS CUSGER,
            NVL((SELECT DES.PERCENTUAL
                 FROM TGFDES DES
                 WHERE DES.CODPROD = I.CODPROD
                   AND (DES.CODEMP = NVL(C.CODEMP, 1) OR DES.CODEMP = 0)
                   AND (DES.CODPARC = NVL(C.CODPARC, 0) OR DES.CODPARC = 0)
                   AND TRUNC(SYSDATE) BETWEEN DES.DTINICIAL AND DES.DTFINAL
                 ORDER BY DES.DTFINAL DESC
                 FETCH FIRST 1 ROW ONLY), 0) AS PERCDESC,
            NVL((SELECT SUM(DSP.PERCDESP)
                 FROM AD_DESPOPER DSP
                 WHERE DSP.ATIVO = 'S'
                   AND DSP.CODEMP = NVL(C.CODEMP, 1)), 0) AS PERCDESP,
            CASE
                WHEN EXISTS (SELECT 1 FROM TGFDIN D WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA) THEN
                    NVL((SELECT MAX(D.ALIQUOTA) FROM TGFDIN D
                         WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA AND D.CODIMP = 1), 0)
                ELSE NVL(I.ALIQICMS, 0)
            END AS ALIQ_ICMS,
            CASE
                WHEN EXISTS (SELECT 1 FROM TGFDIN D WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA) THEN
                    NVL((SELECT MAX(D.ALIQUOTA) FROM TGFDIN D
                         WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA AND D.CODIMP = 6), 0)
                  + NVL((SELECT MAX(D.ALIQUOTA) FROM TGFDIN D
                         WHERE D.NUNOTA = I.NUNOTA AND D.SEQUENCIA = I.SEQUENCIA AND D.CODIMP = 7), 0)
                ELSE
                    NVL((SELECT IFE.ALIQ
                         FROM TGFIFE IFE
                         JOIN TGFPRO PRO ON PRO.CODPROD = I.CODPROD
                         WHERE IFE.NOMEIMP = 'PIS'
                           AND IFE.GRUPOIMP = PRO.GRUPOPIS
                           AND IFE.ENTSAI IN ('A', 'S')
                           AND (IFE.CODEMP = 0 OR IFE.CODEMP = NVL(C.CODEMP, 1))
                           AND (IFE.CODPARC = 0 OR IFE.CODPARC = NVL(C.CODPARC, 0))
                           AND (IFE.CODTIPOPER = 0 OR IFE.CODTIPOPER = NVL(C.CODTIPOPER, 0))
                         ORDER BY IFE.CODTIPOPER DESC, IFE.CODPARC DESC, IFE.CODEMP DESC
                         FETCH FIRST 1 ROW ONLY), 0)
                  + NVL((SELECT IFE.ALIQ
                         FROM TGFIFE IFE
                         JOIN TGFPRO PRO ON PRO.CODPROD = I.CODPROD
                         WHERE IFE.NOMEIMP = 'COFINS'
                           AND IFE.GRUPOIMP = PRO.GRUPOCOFINS
                           AND IFE.ENTSAI IN ('A', 'S')
                           AND (IFE.CODEMP = 0 OR IFE.CODEMP = NVL(C.CODEMP, 1))
                           AND (IFE.CODPARC = 0 OR IFE.CODPARC = NVL(C.CODPARC, 0))
                           AND (IFE.CODTIPOPER = 0 OR IFE.CODTIPOPER = NVL(C.CODTIPOPER, 0))
                         ORDER BY IFE.CODTIPOPER DESC, IFE.CODPARC DESC, IFE.CODEMP DESC
                         FETCH FIRST 1 ROW ONLY), 0)
            END AS ALIQ_PISCOFINS
        FROM TGFITE I
        JOIN TGFCAB C ON C.NUNOTA = I.NUNOTA
        WHERE I.CODPROD IS NOT NULL
          AND C.TIPMOV = 'P'
    ),
    CALC AS (
        SELECT
            B.*,
            B.CUSGER * (1 - B.PERCDESC / 100) AS CUSTO_LIQ,
            B.VLRUNIT * (1 - B.ALIQ_ICMS / 100) * (1 - B.ALIQ_PISCOFINS / 100) AS PRECO_LIQ
        FROM BASE B
    ),
    RESULT AS (
        SELECT
            NUNOTA, SEQUENCIA, MARGEMBRUTA_ATUAL, MARGEMLIQ_ATUAL,
            CASE WHEN NVL(VLRUNIT, 0) <= 0 THEN 0
                 ELSE ROUND((1 - CUSTO_LIQ / PRECO_LIQ) * 100, 4) END AS NOVA_MARGEMBRUTA,
            CASE WHEN NVL(VLRUNIT, 0) <= 0 THEN 0
                 ELSE ROUND((1 - CUSTO_LIQ / PRECO_LIQ) * 100 - PERCDESP, 4) END AS NOVA_MARGEMLIQ
        FROM CALC
        WHERE NVL(VLRUNIT, 0) <= 0 OR PRECO_LIQ <> 0
    )
    SELECT NUNOTA, SEQUENCIA, NOVA_MARGEMBRUTA, NOVA_MARGEMLIQ
    FROM RESULT
    WHERE DECODE(MARGEMBRUTA_ATUAL, NOVA_MARGEMBRUTA, 0, 1) = 1
       OR DECODE(MARGEMLIQ_ATUAL,   NOVA_MARGEMLIQ,   0, 1) = 1
) R
ON (ITE.NUNOTA = R.NUNOTA AND ITE.SEQUENCIA = R.SEQUENCIA)
WHEN MATCHED THEN UPDATE SET
    ITE.AD_MARGEMBRUTA = R.NOVA_MARGEMBRUTA,
    ITE.AD_MARGEMLIQ   = R.NOVA_MARGEMLIQ;

COMMIT;
