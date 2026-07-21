package br.com.fabricante.addon.exemplos.listeners;

import br.com.fabricante.addon.exemplos.util.FaixaPrecoImpostoHelper;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.PersistenceEventAdapter;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.studio.annotations.Listener;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.logging.Logger;

@Listener(instanceNames = {"ItemNota"})
public class ItemNotaListener extends PersistenceEventAdapter {

    private static final Logger logger = Logger.getLogger(ItemNotaListener.class.getName());

    private static final String SQL_FAIXAS_PRODUTO =
        "SELECT FAIXA1, FAIXA2, FAIXA3, FAIXA4, FAIXA5 FROM (" +
        "    SELECT PMK.CODMARKUP," +
        "        (NVL(CUS.CUSGER, 0) * (1 - NVL(DES.PERCENTUAL, 0) / 100.0)) / NULLIF(1 - ((NVL(PMK.PERCMARKUP, 0) + NVL((SELECT SUM(PERCDESP) FROM AD_DESPOPER WHERE ATIVO = 'S' AND CODEMP = :CODEMP), 0)) / 100.0), 0) AS PRECO_CALCULADO" +
        "    FROM TGFPRO PRO" +
        "    LEFT JOIN AD_PERFPRODMKP PER ON (" +
        "        PER.ROWID = (" +
        "            SELECT P2.ROWID FROM AD_PERFPRODMKP P2" +
        "            WHERE (P2.CODPROD = PRO.CODPROD OR P2.CODPROD IS NULL)" +
        "                AND (P2.CODGRUPOPROD = PRO.CODGRUPOPROD OR P2.CODGRUPOPROD IS NULL)" +
        "                AND (P2.CODMARCA = PRO.CODMARCA OR P2.CODMARCA IS NULL)" +
        "                AND (P2.CODPARCFORN = PRO.CODPARCFORN OR P2.CODPARCFORN IS NULL)" +
        "                AND (P2.CODEMP = :CODEMP OR P2.CODEMP IS NULL)" +
        "                AND P2.PRIORIDADE = (" +
        "                    SELECT MAX(P3.PRIORIDADE) FROM AD_PERFPRODMKP P3" +
        "                    WHERE (P3.CODPROD = PRO.CODPROD OR P3.CODPROD IS NULL)" +
        "                        AND (P3.CODGRUPOPROD = PRO.CODGRUPOPROD OR P3.CODGRUPOPROD IS NULL)" +
        "                        AND (P3.CODMARCA = PRO.CODMARCA OR P3.CODMARCA IS NULL)" +
        "                        AND (P3.CODPARCFORN = PRO.CODPARCFORN OR P3.CODPARCFORN IS NULL)" +
        "                        AND (P3.CODEMP = :CODEMP OR P3.CODEMP IS NULL)" +
        "                )" +
        "            ORDER BY" +
        "                CASE WHEN P2.CODPROD = PRO.CODPROD THEN 1 ELSE 0 END DESC," +
        "                CASE WHEN P2.CODGRUPOPROD = PRO.CODGRUPOPROD THEN 1 ELSE 0 END DESC," +
        "                CASE WHEN P2.CODMARCA = PRO.CODMARCA THEN 1 ELSE 0 END DESC," +
        "                CASE WHEN P2.CODPARCFORN = PRO.CODPARCFORN THEN 1 ELSE 0 END DESC" +
        "            FETCH FIRST 1 ROW ONLY" +
        "        )" +
        "    )" +
        "    LEFT JOIN AD_PERMARKUP PMK ON PMK.CODGRUMARKUP = PER.CODGRUMARKUP AND PMK.CODMARKUP <= 5" +
        "    LEFT JOIN TGFCUS CUS ON CUS.CODPROD = PRO.CODPROD" +
        "        AND CUS.CODEMP = :CODEMP" +
        "        AND CUS.DTATUAL = (SELECT MAX(DTATUAL) FROM TGFCUS WHERE CODPROD = PRO.CODPROD AND CODEMP = :CODEMP)" +
        "    LEFT JOIN TGFDES DES ON (" +
        "        DES.ROWID = (" +
        "            SELECT D2.ROWID FROM TGFDES D2" +
        "            WHERE (D2.CODEMP = :CODEMP OR D2.CODEMP = 0)" +
        "                AND (D2.CODPARC = :CODPARC OR D2.CODPARC = 0)" +
        "                AND D2.CODPROD = PRO.CODPROD" +
        "                AND D2.DTINICIAL <= SYSDATE AND D2.DTFINAL >= SYSDATE" +
        "            ORDER BY D2.DTFINAL DESC" +
        "            FETCH FIRST 1 ROW ONLY" +
        "        )" +
        "    )" +
        "    WHERE PRO.CODPROD = :CODPROD" +
        ") PIVOT (" +
        "    MAX(PRECO_CALCULADO)" +
        "    FOR CODMARKUP IN (1 AS FAIXA1, 2 AS FAIXA2, 3 AS FAIXA3, 4 AS FAIXA4, 5 AS FAIXA5)" +
        ")";

    @Override
    public void beforeUpdate(PersistenceEvent event) throws Exception {
        DynamicVO vo = (DynamicVO) event.getVo();

        BigDecimal nunota    = vo.asBigDecimal("NUNOTA");
        BigDecimal codProd   = vo.asBigDecimal("CODPROD");
        BigDecimal sequencia = vo.asBigDecimal("SEQUENCIA");
        if (nunota == null || codProd == null || sequencia == null) return;

        BigDecimal vlrUnit = vo.asBigDecimalOrZero("VLRUNIT");
        if (vlrUnit == null || vlrUnit.compareTo(BigDecimal.ZERO) <= 0) return;

        // Só recalcula se o usuário realmente alterou o VLRUNIT
        EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
        NativeSql sqlVlr = new NativeSql(dwf.getJdbcWrapper());
        sqlVlr.setNamedParameter("NUNOTA", nunota);
        sqlVlr.setNamedParameter("SEQUENCIA", sequencia);
        ResultSet rsVlr = sqlVlr.executeQuery(
            "SELECT VLRUNIT FROM TGFITE WHERE NUNOTA = :NUNOTA AND SEQUENCIA = :SEQUENCIA"
        );
        if (!rsVlr.next()) return;
        BigDecimal vlrUnitAtual = rsVlr.getBigDecimal("VLRUNIT");
        if (vlrUnitAtual != null && vlrUnit.compareTo(vlrUnitAtual) == 0) return;

        DynamicVO cab = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA).findByPK(nunota);
        if (cab == null) return;

        BigDecimal codEmp = cab.asBigDecimalOrZero("CODEMP");
        BigDecimal codParc = cab.asBigDecimalOrZero("CODPARC");
        BigDecimal codTipOper = cab.asBigDecimalOrZero("CODTIPOPER");
        NativeSql sql = new NativeSql(dwf.getJdbcWrapper());
        sql.setNamedParameter("CODEMP", codEmp);
        sql.setNamedParameter("CODPARC", codParc);
        sql.setNamedParameter("CODPROD", codProd);
        ResultSet rs = sql.executeQuery(SQL_FAIXAS_PRODUTO);
        if (!rs.next()) return;

        BigDecimal faixa1 = rs.getBigDecimal("FAIXA1");
        BigDecimal faixa2 = rs.getBigDecimal("FAIXA2");
        BigDecimal faixa3 = rs.getBigDecimal("FAIXA3");
        BigDecimal faixa4 = rs.getBigDecimal("FAIXA4");
        BigDecimal faixa5 = rs.getBigDecimal("FAIXA5");

        if (FaixaPrecoImpostoHelper.temCusto(faixa1, faixa2, faixa3, faixa4, faixa5)) {
            BigDecimal denominador;
            try {
                denominador = FaixaPrecoImpostoHelper.calcularDenominadorImpostos(
                        dwf, nunota, codEmp, codProd, codParc, codTipOper);
            } catch (Exception ex) {
                logger.warning("ItemNotaListener - CODPROD=" + codProd + " AD_FAIXAPRECO não recalculado: " + ex.getMessage());
                return;
            }
            if (denominador.compareTo(BigDecimal.ZERO) > 0) {
                faixa1 = FaixaPrecoImpostoHelper.aplicarDenominador(faixa1, denominador);
                faixa2 = FaixaPrecoImpostoHelper.aplicarDenominador(faixa2, denominador);
                faixa3 = FaixaPrecoImpostoHelper.aplicarDenominador(faixa3, denominador);
                faixa4 = FaixaPrecoImpostoHelper.aplicarDenominador(faixa4, denominador);
                faixa5 = FaixaPrecoImpostoHelper.aplicarDenominador(faixa5, denominador);
            }
        }

        BigDecimal faixaPreco = calcularFaixaPreco(vlrUnit, faixa1, faixa2, faixa3, faixa4, faixa5);

        if (faixaPreco != null) {
            vo.setProperty("AD_FAIXAPRECO", faixaPreco);
            logger.info("ItemNotaListener - CODPROD=" + codProd + " VLRUNIT=" + vlrUnit + " AD_FAIXAPRECO=" + faixaPreco);
        }
    }

    private BigDecimal calcularFaixaPreco(BigDecimal vlrUnit, BigDecimal... faixas) {
        if (vlrUnit == null || vlrUnit.compareTo(BigDecimal.ZERO) <= 0) return null;
        BigDecimal minFaixa = null;
        int melhorIndice = -1;
        BigDecimal menorDiff = null;
        for (int i = 0; i < faixas.length; i++) {
            if (faixas[i] == null) continue;
            if (minFaixa == null || faixas[i].compareTo(minFaixa) < 0) minFaixa = faixas[i];
            BigDecimal diff = vlrUnit.subtract(faixas[i]).abs();
            if (menorDiff == null || diff.compareTo(menorDiff) < 0) {
                menorDiff = diff;
                melhorIndice = i;
            }
        }
        if (minFaixa != null && vlrUnit.compareTo(minFaixa) < 0) return BigDecimal.ZERO;
        return melhorIndice >= 0 ? new BigDecimal(melhorIndice + 1) : null;
    }
}
