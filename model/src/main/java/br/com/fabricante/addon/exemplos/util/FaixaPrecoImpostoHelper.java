package br.com.fabricante.addon.exemplos.util;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper;
import br.com.sankhya.modelcore.dwfdata.vo.ItemNotaVO;
import br.com.sankhya.modelcore.util.DynamicEntityNames;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.util.logging.Logger;

public class FaixaPrecoImpostoHelper {

    private static final Logger logger = Logger.getLogger(FaixaPrecoImpostoHelper.class.getName());

    private static final BigDecimal CEM = new BigDecimal("100");

    private static final String SQL_PIS_COFINS =
        "SELECT NOMEIMP, ALIQ FROM (" +
        "    SELECT" +
        "        IFE.NOMEIMP," +
        "        IFE.ALIQ," +
        "        ROW_NUMBER() OVER (" +
        "            PARTITION BY IFE.NOMEIMP" +
        "            ORDER BY IFE.CODTIPOPER DESC, IFE.CODPARC DESC, IFE.CODEMP DESC" +
        "        ) AS RN" +
        "    FROM TGFIFE IFE" +
        "    INNER JOIN TGFPRO PRO ON PRO.CODPROD = :CODPROD" +
        "    WHERE" +
        "    (" +
        "        (IFE.NOMEIMP = 'PIS'    AND IFE.GRUPOIMP = PRO.GRUPOPIS)" +
        "        OR" +
        "        (IFE.NOMEIMP = 'COFINS' AND IFE.GRUPOIMP = PRO.GRUPOCOFINS)" +
        "    )" +
        "    AND IFE.ENTSAI IN ('A','S')" +
        "    AND (IFE.CODEMP = 0 OR IFE.CODEMP = :CODEMP)" +
        "    AND (IFE.CODPARC = 0 OR IFE.CODPARC = :CODPARC)" +
        "    AND (IFE.CODTIPOPER = 0 OR IFE.CODTIPOPER = :CODTIPOPER)" +
        ") " +
        "WHERE RN = 1 " +
        "ORDER BY NOMEIMP";

    /**
     * FAIXA_FINAL = numerador / ((1 - ALIQICMSEFETIVA/100) * (1 - (ALIQPIS+ALIQCOFINS)/100)),
     * onde ALIQICMSEFETIVA = ALIQICMS * (1 - reducaoBase/100), com reducaoBase vindo da tela
     * Alíquotas de ICMS (InfoAliquota.reducaoBase).
     * Retorna o denominador (produto dos dois fatores) a aplicar sobre o numerador calculado em SQL.
     */
    public static BigDecimal calcularDenominadorImpostos(EntityFacade entityFacade, BigDecimal nunota,
            BigDecimal codEmp, BigDecimal codProd, BigDecimal codParc, BigDecimal codTipOper) throws Exception {
        DynamicVO itemDVO = (DynamicVO) entityFacade.getDefaultValueObjectInstance(DynamicEntityNames.ITEM_NOTA);
        ItemNotaVO itemVO = (ItemNotaVO) itemDVO.wrapInterface(ItemNotaVO.class);
        itemVO.setNUNOTA(nunota);
        itemVO.setCODEMP(codEmp);
        itemVO.setCODPROD(codProd);
        itemVO.setVLRUNIT(BigDecimal.ONE);
        itemVO.setVLRTOT(BigDecimal.ONE);
        itemVO.setQTDNEG(BigDecimal.ONE);

        ImpostosHelpper imposto = new ImpostosHelpper();
        imposto.setAtualizaImpostos(false);
        imposto.setCalcularTudo(true);
        imposto.calcularImpostosItem(itemVO, codProd);

        BigDecimal aliqIcms = itemDVO.asBigDecimalOrZero("ALIQICMS");

        BigDecimal reducaoBaseIcms = BigDecimal.ZERO;
        ImpostosHelpper.InfoAliquota infoAliquota = imposto.getInfoAliquota();
        if (infoAliquota != null && infoAliquota.reducaoBase != null) {
            reducaoBaseIcms = infoAliquota.reducaoBase;
        }
        BigDecimal aliqIcmsEfetiva = aliqIcms.multiply(
                BigDecimal.ONE.subtract(reducaoBaseIcms.divide(CEM, 10, RoundingMode.HALF_UP)));

        BigDecimal[] pisCofins = buscarAliquotasPisCofins(entityFacade, codProd, codEmp, codParc, codTipOper);
        BigDecimal aliqPis = pisCofins[0];
        BigDecimal aliqCofins = pisCofins[1];

        BigDecimal fatorIcms = BigDecimal.ONE.subtract(aliqIcmsEfetiva.divide(CEM, 10, RoundingMode.HALF_UP));
        BigDecimal fatorPisCofins = BigDecimal.ONE.subtract(
                aliqPis.add(aliqCofins).divide(CEM, 10, RoundingMode.HALF_UP));
        BigDecimal denominador = fatorIcms.multiply(fatorPisCofins);

        logger.info("[IMPOSTOS] CODPROD=" + codProd
                + " | ICMS=" + aliqIcms + "% | reducaoBaseICMS=" + reducaoBaseIcms + "% | ICMSEfetivo=" + aliqIcmsEfetiva + "%"
                + " | PIS=" + aliqPis + "% | COFINS=" + aliqCofins + "%"
                + " | denominador=(1-" + aliqIcmsEfetiva + "/100)*(1-(" + aliqPis + "+" + aliqCofins + ")/100)=" + denominador);

        return denominador;
    }

    /**
     * Alíquotas de PIS/COFINS cadastradas na TGFIFE para o produto. Retorna {ALIQPIS, ALIQCOFINS}.
     */
    public static BigDecimal[] buscarAliquotasPisCofins(EntityFacade entityFacade, BigDecimal codProd,
            BigDecimal codEmp, BigDecimal codParc, BigDecimal codTipOper) throws Exception {
        BigDecimal aliqPis = BigDecimal.ZERO;
        BigDecimal aliqCofins = BigDecimal.ZERO;
        NativeSql sqlPisCofins = new NativeSql(entityFacade.getJdbcWrapper());
        sqlPisCofins.setNamedParameter("CODPROD", codProd);
        sqlPisCofins.setNamedParameter("CODEMP", codEmp);
        sqlPisCofins.setNamedParameter("CODPARC", codParc);
        sqlPisCofins.setNamedParameter("CODTIPOPER", codTipOper);
        ResultSet rsPisCofins = sqlPisCofins.executeQuery(SQL_PIS_COFINS);
        while (rsPisCofins.next()) {
            String nomeImp = rsPisCofins.getString("NOMEIMP");
            BigDecimal aliq = rsPisCofins.getBigDecimal("ALIQ");
            if (aliq == null) continue;
            if ("PIS".equals(nomeImp)) {
                aliqPis = aliq;
            } else if ("COFINS".equals(nomeImp)) {
                aliqCofins = aliq;
            }
        }
        return new BigDecimal[]{aliqPis, aliqCofins};
    }

    public static BigDecimal aplicarDenominador(BigDecimal numerador, BigDecimal denominador) {
        if (numerador == null || numerador.compareTo(BigDecimal.ZERO) == 0) return null;
        return numerador.divide(denominador, 2, RoundingMode.HALF_UP);
    }

    public static boolean temCusto(BigDecimal... faixas) {
        for (BigDecimal faixa : faixas) {
            if (faixa != null && faixa.compareTo(BigDecimal.ZERO) > 0) return true;
        }
        return false;
    }
}
