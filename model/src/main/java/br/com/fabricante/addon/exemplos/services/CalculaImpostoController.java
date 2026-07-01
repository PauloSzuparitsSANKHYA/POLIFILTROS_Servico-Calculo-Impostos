package br.com.fabricante.addon.exemplos.services;

import br.com.fabricante.addon.exemplos.dto.BuscarAlternativosRequestDTO;
import br.com.fabricante.addon.exemplos.dto.BuscarAlternativosResponseDTO;
import br.com.fabricante.addon.exemplos.dto.BuscarProdutosRequestDTO;
import br.com.fabricante.addon.exemplos.dto.BuscarProdutosResponseDTO;
import br.com.fabricante.addon.exemplos.dto.CalculaImpostoRequestDTO;
import br.com.fabricante.addon.exemplos.dto.CalculaImpostoResponseDTO;
import br.com.fabricante.addon.exemplos.dto.IncluirItensRequestDTO;
import br.com.fabricante.addon.exemplos.dto.IncluirItensResponseDTO;
import br.com.fabricante.addon.exemplos.dto.ItemCarrinhoDTO;
import br.com.fabricante.addon.exemplos.dto.ProdutoAlternativoDTO;
import br.com.fabricante.addon.exemplos.dto.ProdutoListaDTO;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidCreateVO;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.comercial.BarramentoRegra;
import br.com.sankhya.modelcore.comercial.CentralFaturamento;
import br.com.sankhya.modelcore.comercial.ConfirmacaoNotaHelper;
import br.com.sankhya.modelcore.comercial.LiberacaoSolicitada;
import br.com.sankhya.modelcore.comercial.impostos.DadosImpostoItemNota;
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper;
import br.com.sankhya.modelcore.dwfdata.vo.ItemNotaVO;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.studio.annotations.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Logger;

@Service(serviceName = "CalculaImpostoSP")
public class CalculaImpostoController {

    private static final Logger logger = Logger.getLogger(CalculaImpostoController.class.getName());

    private static final String SQL_ALTERNATIVOS =
        "WITH LISTA_PRODUTOS AS (" +
        "    SELECT :CODPROD AS CODPROD, 0 AS ORDEMHIERARQUIA FROM DUAL" +
        "    UNION" +
        "    SELECT PAI.CODPRODALT AS CODPROD, 1 AS ORDEMHIERARQUIA" +
        "    FROM TGFPAL PAI" +
        "    WHERE PAI.CODPROD = :CODPROD" +
        "    UNION" +
        "    SELECT FILHO.CODPROD AS CODPROD, 1 AS ORDEMHIERARQUIA" +
        "    FROM TGFPAL FILHO" +
        "    WHERE FILHO.CODPRODALT = :CODPROD" +
        "    UNION" +
        "    SELECT PAI.CODPRODALT AS CODPROD, 2 AS ORDEMHIERARQUIA" +
        "    FROM TGFPAL FILHO" +
        "    JOIN TGFPAL PAI ON FILHO.CODPROD = PAI.CODPROD" +
        "    WHERE FILHO.CODPRODALT = :CODPROD" +
        "      AND PAI.CODPRODALT <> :CODPROD" +
        ") " +
        "SELECT * FROM (" +
        "    SELECT" +
        "        LP.ORDEMHIERARQUIA," +
        "        PRO.CODPROD," +
        "        PRO.REFERENCIA," +
        "        PRO.DESCRPROD," +
        "        PRO.MARCA," +
        "        PRO.AD_COMERCIALIZA AS COMERCIALIZA," +
        "        PRO.CODGRUPOPROD," +
        "        GRU.DESCRGRUPOPROD," +
        "        PER.CODGRUMARKUP," +
        "        GRUMKP.DESCRGRUMARKUP," +
        "        PER.PRIORIDADE," +
        "        PMK.CODMARKUP," +
        "        NVL(CUS.CUSGER, 0) / NULLIF(1 - ((NVL(PMK.PERCMARKUP, 0) + NVL((SELECT SUM(PERCDESP) FROM AD_DESPOPER WHERE ATIVO = 'S' AND CODEMP = :CODEMP), 0)) / 100.0), 0) AS PRECO_CALCULADO," +
        "        NVL(EST.ESTOQUE, 0) - NVL(EST.RESERVADO, 0) AS QTDESTOQUE," +
        "        NVL(EST.RESERVADO, 0) AS QTDRESERVADO," +
        "        COALESCE((SELECT SUM(I.QTDNEG) FROM TGFITE I JOIN TGFCAB C ON I.NUNOTA = C.NUNOTA WHERE I.CODPROD = PRO.CODPROD AND I.PENDENTE = 'S' AND C.TIPMOV = 'O' AND C.STATUSNOTA = 'L'), 0) AS COMPRAPENDENTE," +
        "        (SELECT TO_CHAR(MIN(ITE2.AD_DTPREVENTGER), 'DD/MM/YYYY') FROM TGFITE ITE2 JOIN TGFCAB CAB2 ON CAB2.NUNOTA = ITE2.NUNOTA WHERE ITE2.CODPROD = PRO.CODPROD AND ITE2.CODEMP = :CODEMP AND CAB2.STATUSNOTA = 'L' AND CAB2.PENDENTE = 'S' AND ITE2.AD_DTPREVENTGER IS NOT NULL) AS DTPROXENTREGA," +
        "        (SELECT MAX(ITE.VLRUNIT) FROM TGFCAB CAB JOIN TGFITE ITE ON ITE.NUNOTA = CAB.NUNOTA WHERE ITE.CODPROD = PRO.CODPROD AND CAB.CODPARC = :CODPARC AND CAB.CODTIPOPER = 1000 AND CAB.STATUSNOTA = 'L' AND CAB.DTMOV = (SELECT MAX(DTMOV) FROM TGFCAB WHERE CODTIPOPER = 1000 AND STATUSNOTA = 'L')) AS VLRULTIMACOTACAO," +
        "        (SELECT MAX(ITE.VLRUNIT) FROM TGFCAB CAB JOIN TGFITE ITE ON ITE.NUNOTA = CAB.NUNOTA WHERE ITE.CODPROD = PRO.CODPROD AND CAB.CODPARC = :CODPARC AND CAB.TIPMOV = 'V' AND CAB.STATUSNOTA = 'L' AND CAB.DTMOV = (SELECT MAX(DTMOV) FROM TGFCAB WHERE CAB.TIPMOV = 'V' AND STATUSNOTA = 'L')) AS VLRULTIMAVENDA" +
        "    FROM LISTA_PRODUTOS LP" +
        "    JOIN TGFPRO PRO ON LP.CODPROD = PRO.CODPROD" +
        "    JOIN TGFGRU GRU ON PRO.CODGRUPOPROD = GRU.CODGRUPOPROD" +
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
        "    LEFT JOIN AD_GRUMARKUP GRUMKP ON PER.CODGRUMARKUP = GRUMKP.CODGRUMARKUP" +
        "    LEFT JOIN TGFCUS CUS ON CUS.CODPROD = PRO.CODPROD" +
        "        AND (CUS.CODEMP = :CODEMP OR :CODEMP IS NULL)" +
        "        AND CUS.DTATUAL = (SELECT MAX(DTATUAL) FROM TGFCUS WHERE CODPROD = PRO.CODPROD AND CODEMP = :CODEMP)" +
        "    LEFT JOIN TGFEST EST ON PRO.CODPROD = EST.CODPROD AND EST.CODEMP = :CODEMP" +
        "        AND ((:IS_TOP1057 = 1 AND EST.CODLOCAL = 201) OR (:IS_TOP1057 = 0 AND EST.CODLOCAL = 101))" +
        ")" +
        " PIVOT (" +
        "    MAX(PRECO_CALCULADO)" +
        "    FOR CODMARKUP IN (1 AS FAIXA1, 2 AS FAIXA2, 3 AS FAIXA3, 4 AS FAIXA4, 5 AS FAIXA5)" +
        ")" +
        " ORDER BY ORDEMHIERARQUIA ASC, CODPROD ASC" +
        " OFFSET :POFFSET ROWS FETCH NEXT :PLIMIT ROWS ONLY";

    private static final String SQL_PRODUTOS_BASE =
        "SELECT PRO.CODPROD, PRO.REFERENCIA, PRO.DESCRPROD, PRO.CODVOL," +
        " (SELECT TO_CHAR(MIN(ITE.DTINICIO), 'DD/MM/YYYY') FROM TGFITE ITE" +
        "  JOIN TGFCAB CAB ON CAB.NUNOTA = ITE.NUNOTA" +
        "  WHERE ITE.CODPROD = PRO.CODPROD AND CAB.STATUSNOTA = 'L' AND CAB.PENDENTE = 'S'" +
        "  AND ITE.DTINICIO IS NOT NULL) AS DTPROXENTREGA" +
        " FROM TGFPRO PRO" +
        " WHERE PRO.ATIVO = 'S'";

    private static final String SQL_PRODUTOS_FILTRO =
        " AND (UPPER(PRO.DESCRPROD) LIKE UPPER('%' || :FILTRO || '%')" +
        "   OR UPPER(PRO.REFERENCIA) LIKE UPPER('%' || :FILTRO || '%')" +
        "   OR TO_CHAR(PRO.CODPROD) LIKE '%' || :FILTRO || '%')";

    private static final String SQL_PRODUTOS_ORDEM =
        " ORDER BY PRO.DESCRPROD" +
        " OFFSET :POFFSET ROWS FETCH NEXT :PLIMIT ROWS ONLY";

    private static final String SQL_BATCH_PRODUTO_REFERENCIA =
        "SELECT CODPROD, CODVOL, NVL(CODLOCALPADRAO, 0) AS CODLOCALPADRAO, REFERENCIA" +
        " FROM TGFPRO WHERE CODPROD IN (%s)";

    public BuscarProdutosResponseDTO buscarProdutos(BuscarProdutosRequestDTO request) throws Exception {
        int offset = request.getOffset() < 0  ? 0  : request.getOffset();
        int limit  = request.getLimit()  <= 0 ? 20 : request.getLimit();
        String filtro = request.getFiltro() != null ? request.getFiltro().trim() : "";

        List<ProdutoListaDTO> produtos = new ArrayList<>();
        JapeSession.SessionHandle hnd = null;
        try {
            hnd = JapeSession.open();
            EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
            NativeSql sql = new NativeSql(dwf.getJdbcWrapper());

            StringBuilder query = new StringBuilder(SQL_PRODUTOS_BASE);
            if (!filtro.isEmpty()) {
                query.append(SQL_PRODUTOS_FILTRO);
                sql.setNamedParameter("FILTRO", filtro);
            }
            query.append(SQL_PRODUTOS_ORDEM);
            sql.setNamedParameter("POFFSET", new BigDecimal(offset));
            sql.setNamedParameter("PLIMIT",  new BigDecimal(limit));

            ResultSet rs = sql.executeQuery(query.toString());
            while (rs.next()) {
                ProdutoListaDTO dto = new ProdutoListaDTO();
                dto.setCodProd(rs.getBigDecimal("CODPROD"));
                dto.setReferencia(rs.getString("REFERENCIA"));
                dto.setDescrProd(rs.getString("DESCRPROD"));
                dto.setCodVol(rs.getString("CODVOL"));
                dto.setDtProxEntrega(rs.getString("DTPROXENTREGA"));
                produtos.add(dto);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar produtos: " + e.getMessage(), e);
        } finally {
            JapeSession.close(hnd);
        }

        BuscarProdutosResponseDTO response = new BuscarProdutosResponseDTO();
        response.setProdutos(produtos);
        response.setTemMais(produtos.size() >= limit);
        return response;
    }

    public BuscarAlternativosResponseDTO buscarAlternativos(BuscarAlternativosRequestDTO request) throws Exception {
        BigDecimal nuNota  = request.getNuNota();
        BigDecimal codProd = request.getCodProd();

        List<ProdutoAlternativoDTO> produtos = new ArrayList<>();
        Map<BigDecimal, BigDecimal> impostosMap  = new HashMap<>();
        Map<BigDecimal, BigDecimal> percDescMap  = new HashMap<>();
        java.util.Set<BigDecimal> failedProducts = new java.util.HashSet<>();
        int offset = request.getOffset() < 0  ? 0  : request.getOffset();
        int limit  = request.getLimit()  <= 0 ? 10 : request.getLimit();

        JapeSession.SessionHandle hnd = null;
        try {
            hnd = JapeSession.open();

            // 1. Ler CabecalhoNota existente
            DynamicVO cabExistente = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA).findByPK(nuNota);
            if (cabExistente == null) {
                throw new IllegalArgumentException("NF/Pedido não encontrado para NUNOTA=" + nuNota);
            }
            BigDecimal codEmp      = cabExistente.asBigDecimalOrZero("CODEMP");
            BigDecimal codParc     = cabExistente.asBigDecimalOrZero("CODPARC");
            BigDecimal codTipOper  = cabExistente.asBigDecimalOrZero("CODTIPOPER");
            BigDecimal codTipVenda = cabExistente.asBigDecimalOrZero("CODTIPVENDA");
            logger.info("buscarAlternativos - lido NUNOTA=" + nuNota + " CODEMP=" + codEmp + " CODPARC=" + codParc + " CODTIPOPER=" + codTipOper + " CODTIPVENDA=" + codTipVenda);

            // 2. SQL principal: busca produtos e alternativos com faixas de preço calculadas
            EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
            NativeSql nativeSql = new NativeSql(dwf.getJdbcWrapper());
            nativeSql.setNamedParameter("CODEMP",   codEmp);
            nativeSql.setNamedParameter("CODPROD",  codProd);
            nativeSql.setNamedParameter("CODPARC",  codParc);
            nativeSql.setNamedParameter("POFFSET",    new BigDecimal(offset));
            nativeSql.setNamedParameter("PLIMIT",     new BigDecimal(limit));
            nativeSql.setNamedParameter("IS_TOP1057", codTipOper.intValue() == 1057 ? BigDecimal.ONE : BigDecimal.ZERO);
            ResultSet rs = nativeSql.executeQuery(SQL_ALTERNATIVOS);
            while (rs.next()) {
                ProdutoAlternativoDTO dto = new ProdutoAlternativoDTO();
                dto.setOrdemHierarquia(rs.getInt("ORDEMHIERARQUIA"));
                dto.setCodProd(rs.getBigDecimal("CODPROD"));
                dto.setReferencia(rs.getString("REFERENCIA"));
                dto.setDescrProd(rs.getString("DESCRPROD"));
                dto.setMarca(rs.getString("MARCA"));
                dto.setComercializa(rs.getString("COMERCIALIZA"));
                dto.setCodGrupoProd(rs.getBigDecimal("CODGRUPOPROD"));
                dto.setDescrGrupoProd(rs.getString("DESCRGRUPOPROD"));
                dto.setCodGruMarkup(rs.getBigDecimal("CODGRUMARKUP"));
                dto.setDescrGruMarkup(rs.getString("DESCRGRUMARKUP"));
                dto.setPrioridade(rs.getBigDecimal("PRIORIDADE"));
                dto.setQtdEstoque(rs.getBigDecimal("QTDESTOQUE"));
                dto.setQtdReservado(rs.getBigDecimal("QTDRESERVADO"));
                dto.setCompraPendente(rs.getBigDecimal("COMPRAPENDENTE"));
                dto.setDtProxEntrega(rs.getString("DTPROXENTREGA"));
                dto.setVlrUltimaCotacao(rs.getBigDecimal("VLRULTIMACOTACAO"));
                dto.setVlrUltimaVenda(rs.getBigDecimal("VLRULTIMAVENDA"));
                dto.setFaixa1(rs.getBigDecimal("FAIXA1"));
                dto.setFaixa2(rs.getBigDecimal("FAIXA2"));
                dto.setFaixa3(rs.getBigDecimal("FAIXA3"));
                dto.setFaixa4(rs.getBigDecimal("FAIXA4"));
                dto.setFaixa5(rs.getBigDecimal("FAIXA5"));
                produtos.add(dto);
            }
            logger.info("buscarAlternativos - produtos encontrados: " + produtos.size() + " para CODPROD=" + codProd);

            // 3. Calcular impostos por item via ImpostosHelpper (sem escrita no banco)
            if (!produtos.isEmpty()) {
                EntityFacade entityFacade = EntityFacadeFactory.getDWFFacade();
                ImpostosHelpper imposto = new ImpostosHelpper();
                imposto.setAtualizaImpostos(false);
                imposto.setCalcularTudo(true);

                for (ProdutoAlternativoDTO p : produtos) {
                    BigDecimal codProdItem = p.getCodProd();
                    if (!temCusto(p)) {
                        impostosMap.put(codProdItem, BigDecimal.ZERO);
                        percDescMap.put(codProdItem, BigDecimal.ZERO);
                        continue;
                    }
                    try {
                        DynamicVO itemDVO = (DynamicVO) entityFacade
                                .getDefaultValueObjectInstance(DynamicEntityNames.ITEM_NOTA);
                        ItemNotaVO itemVO = (ItemNotaVO) itemDVO.wrapInterface(ItemNotaVO.class);
                        itemVO.setNUNOTA(nuNota);
                        itemVO.setCODEMP(codEmp);
                        itemVO.setCODPROD(codProdItem);
                        itemVO.setVLRUNIT(BigDecimal.ONE);
                        itemVO.setVLRTOT(BigDecimal.ONE);
                        itemVO.setQTDNEG(BigDecimal.ONE);


                        imposto.calcularImpostosItem(itemVO, codProdItem);

                        BigDecimal aliqPis = BigDecimal.ZERO;
                        Collection<DadosImpostoItemNota> pisDados = imposto.calcularPIS(itemVO);
                        if (pisDados != null) {
                            for (DadosImpostoItemNota d : pisDados) {
                                if (d.getAliquota() != null) aliqPis = aliqPis.add(d.getAliquota());
                            }
                        }


                        BigDecimal aliqCofins = BigDecimal.ZERO;
                        Collection<DadosImpostoItemNota> cofinsDados = imposto.calcularCOFINS(itemVO);
                        if (cofinsDados != null) {
                            for (DadosImpostoItemNota d : cofinsDados) {
                                if (d.getAliquota() != null) aliqCofins = aliqCofins.add(d.getAliquota());
                            }
                        }

                        BigDecimal aliqIcms = itemDVO.asBigDecimalOrZero("ALIQICMS");
                        BigDecimal aliqIpi  = itemDVO.asBigDecimalOrZero("ALIQIPI");
                        BigDecimal aliqIss  = itemDVO.asBigDecimalOrZero("ALIQISS");

                        logger.info("[IMPOSTOS] CODPROD=" + codProdItem
                                + " | ICMS=" + aliqIcms + "% | IPI=" + aliqIpi + "% | PIS=" + aliqPis
                                + "% | COFINS=" + aliqCofins + "% | ISS=" + aliqIss + "%");

                        BigDecimal totalAliq = (BigDecimal.ZERO
                                .add(aliqIcms)
                                .add(aliqIpi)
                                .add(aliqPis)
                                .add(aliqCofins)
                                .add(aliqIss)).setScale(2);

                        BigDecimal percDescItem = itemDVO.asBigDecimalOrZero("PERCDESC");

                        logger.info("[IMPOSTOS] CODPROD=" + codProdItem
                                + " | totalAliq=" + totalAliq + "% | percDesc=" + percDescItem + "%"
                                + " | formula: preco = custo / (1 - " + totalAliq + "/100) * (1 - " + percDescItem + "/100)");

                        impostosMap.put(codProdItem, totalAliq);
                        percDescMap.put(codProdItem, percDescItem);
                    } catch (Exception ex) {
                        logger.warning("buscarAlternativos - CODPROD=" + codProdItem + " ignorado: " + ex.getMessage());
                        failedProducts.add(codProdItem);
                    }
                }
                logger.info("buscarAlternativos - impostos calculados para " + impostosMap.size() + " produto(s)");
            }

            // 4. Aplicar fórmula: preco_final_faixa = numerador / (1 - impostos/100) * (1 - percDesc/100)
            for (ProdutoAlternativoDTO p : produtos) {
                if (failedProducts.contains(p.getCodProd())) {
                    p.setFaixa1(null);
                    p.setFaixa2(null);
                    p.setFaixa3(null);
                    p.setFaixa4(null);
                    p.setFaixa5(null);
                    continue;
                }
                BigDecimal impostos = impostosMap.getOrDefault(p.getCodProd(), BigDecimal.ZERO);
                BigDecimal denominador = BigDecimal.ONE.subtract(
                        impostos.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
                logger.info("[FORMULA] CODPROD=" + p.getCodProd()
                        + " | faixas brutas: F1=" + p.getFaixa1() + " F2=" + p.getFaixa2()
                        + " F3=" + p.getFaixa3() + " F4=" + p.getFaixa4() + " F5=" + p.getFaixa5()
                        + " | denominador=(1 - " + impostos + "/100)=" + denominador);
                if (denominador.compareTo(BigDecimal.ZERO) > 0) {
                    p.setFaixa1(aplicarDenominador(p.getFaixa1(), denominador));
                    p.setFaixa2(aplicarDenominador(p.getFaixa2(), denominador));
                    p.setFaixa3(aplicarDenominador(p.getFaixa3(), denominador));
                    p.setFaixa4(aplicarDenominador(p.getFaixa4(), denominador));
                    p.setFaixa5(aplicarDenominador(p.getFaixa5(), denominador));
                }
                BigDecimal percDesc = percDescMap.getOrDefault(p.getCodProd(), BigDecimal.ZERO);
                p.setPercDesc(percDesc);
                if (percDesc.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal fatorDesc = BigDecimal.ONE.subtract(
                            percDesc.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
                    p.setFaixa1(aplicarFator(p.getFaixa1(), fatorDesc));
                    p.setFaixa2(aplicarFator(p.getFaixa2(), fatorDesc));
                    p.setFaixa3(aplicarFator(p.getFaixa3(), fatorDesc));
                    p.setFaixa4(aplicarFator(p.getFaixa4(), fatorDesc));
                    p.setFaixa5(aplicarFator(p.getFaixa5(), fatorDesc));
                }
                logger.info("[FORMULA] CODPROD=" + p.getCodProd()
                        + " | faixas finais: F1=" + p.getFaixa1() + " F2=" + p.getFaixa2()
                        + " F3=" + p.getFaixa3() + " F4=" + p.getFaixa4() + " F5=" + p.getFaixa5()
                        + " | percDesc=" + percDesc + "%");
            }

        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar alternativos: " + e.getMessage(), e);
        } finally {
            JapeSession.close(hnd);
        }

        BuscarAlternativosResponseDTO response = new BuscarAlternativosResponseDTO();
        response.setProdutos(produtos);
        response.setTemMais(produtos.size() >= limit);
        return response;
    }

    public IncluirItensResponseDTO incluirItens(IncluirItensRequestDTO request) throws Exception {
        BigDecimal nuNota = request.getNuNota();
        List<ItemCarrinhoDTO> itens = request.getItens();

        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item informado para inserção.");
        }

        final int[] inseridos = { 0 };
        boolean notaJaConfirmada = false;
        JapeSession.SessionHandle hnd = null;
        try {
            hnd = JapeSession.open();

            DynamicVO cab = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA).findByPK(nuNota);
            if (cab == null) {
                throw new IllegalArgumentException("Nota não encontrada: NUNOTA=" + nuNota);
            }
            BigDecimal codTipOper = cab.asBigDecimalOrZero("CODTIPOPER");
            java.sql.Timestamp dhTipOper = cab.asTimestamp("DHTIPOPER");

            JapeWrapper topDAO = JapeFactory.dao("TipoOperacao");
            DynamicVO top = topDAO.findOne("CODTIPOPER = ? AND DHALTER = ?", codTipOper, dhTipOper);
            boolean altNfConf = top != null && "S".equals(top.asString("ALTNFCONF"));
            notaJaConfirmada = "L".equals(cab.asString("STATUSNOTA"));

            if (!altNfConf && notaJaConfirmada) {
                throw new IllegalStateException("Não é permitido incluir itens: NUNOTA=" + nuNota + " está com status Liberado (STATUSNOTA='L').");
            }

            // Pre-carrega todos os produtos (e origens) em uma única query antes da transação
            java.util.Set<BigDecimal> codProdParaCarregar = new java.util.HashSet<>();
            for (ItemCarrinhoDTO item : itens) {
                codProdParaCarregar.add(item.getCodProd());
                if (item.getCodProdOrigem() != null) codProdParaCarregar.add(item.getCodProdOrigem());
            }
            Map<BigDecimal, Object[]> produtoDataMap = carregarDadosProdutosComReferencia(
                    EntityFacadeFactory.getDWFFacade(), codProdParaCarregar);

            hnd.execEnsuringTX(new JapeSession.TXBlock() {
                public void doWithTx() throws Exception {
                    JapeWrapper iteDAO = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA);

                    for (ItemCarrinhoDTO item : itens) {
                        BigDecimal codProd = item.getCodProd();
                        BigDecimal vlrUnit = item.getVlrUnit() != null ? item.getVlrUnit() : BigDecimal.ZERO;
                        BigDecimal qtd     = item.getQtd() != null && item.getQtd().compareTo(BigDecimal.ZERO) > 0
                                             ? item.getQtd() : BigDecimal.ONE;

                        Object[] prodData = produtoDataMap.get(codProd);
                        if (prodData == null) {
                            throw new IllegalStateException("Dados do produto não encontrados: CODPROD=" + codProd);
                        }
                        String    codVol    = (String)     prodData[0];
                        BigDecimal codLocal = (BigDecimal) prodData[1];

                        FluidCreateVO itemVO = iteDAO.create();
                        itemVO.set("NUNOTA",   nuNota);
                        itemVO.set("CODPROD",  codProd);
                        itemVO.set("QTDNEG",   qtd);
                        itemVO.set("CODVOL",   codVol);
                        itemVO.set("VLRUNIT",  vlrUnit);
                        itemVO.set("VLRTOT",   vlrUnit.multiply(qtd));
                        if (codLocal.compareTo(BigDecimal.ZERO) > 0) {
                            itemVO.set("CODLOCALORIG", codLocal);
                        }
                        itemVO.set("BASEICMS", BigDecimal.ZERO);
                        itemVO.set("VLRICMS",  BigDecimal.ZERO);
                        itemVO.set("ALIQICMS", BigDecimal.ZERO);
                        itemVO.set("BASEIPI",  BigDecimal.ZERO);
                        itemVO.set("VLRIPI",   BigDecimal.ZERO);
                        itemVO.set("ALIQIPI",  BigDecimal.ZERO);
                        if (item.getCodProdOrigem() != null) {
                            Object[] origemData = produtoDataMap.get(item.getCodProdOrigem());
                            String referencia = origemData != null ? (String) origemData[2] : null;
                            itemVO.set("AD_REFSOLICITADA", referencia);
                        }
                        BigDecimal faixaPreco = calcularFaixaPrecoItem(vlrUnit,
                            item.getFaixa1(), item.getFaixa2(), item.getFaixa3(),
                            item.getFaixa4(), item.getFaixa5());
                        if (faixaPreco != null) {
                            itemVO.set("AD_FAIXAPRECO", faixaPreco);
                        }
                        itemVO.save();

                        logger.info("incluirItens - CODPROD=" + codProd + " QTD=" + qtd + " VLR=" + vlrUnit + " NUNOTA=" + nuNota);
                        inseridos[0]++;
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inserir itens na nota " + nuNota + ": " + e.getMessage(), e);
        } finally {
            JapeSession.close(hnd);
        }

        List<String> liberacoesPendentes;
        if (notaJaConfirmada) {
            liberacoesPendentes = reconfirmarNotaAposInclusao(nuNota);
        } else {
            calculaImpostoItensETotalizaNota(nuNota);
            liberacoesPendentes = new ArrayList<>();
        }

        IncluirItensResponseDTO response = new IncluirItensResponseDTO();
        response.setMensagem(inseridos[0] + " item(ns) inserido(s) com sucesso na nota " + nuNota + ".");
        response.setQtdItensInseridos(inseridos[0]);
        response.setLiberacoesPendentes(liberacoesPendentes);
        return response;
    }

    private List<String> reconfirmarNotaAposInclusao(BigDecimal nunota) throws Exception {
        JapeSession.SessionHandle hnd = null;
        final List<String> libPendentes = new ArrayList<>();
        try {
            hnd = JapeSession.open();
            hnd.execWithTX(new JapeSession.TXBlock() {
                public void doWithTx() throws Exception {
                    ImpostosHelpper impostohelp = new ImpostosHelpper();
                    impostohelp.forcaRecalculoBaseISS(true);
                    impostohelp.calcularISS(nunota);
                    impostohelp.calcularIRF(nunota);
                    impostohelp.calcularImpostos(nunota);

                    BarramentoRegra barramentoConfirmacao = BarramentoRegra.build(
                            CentralFaturamento.class, "regrasConfirmacaoSilenciosa.xml", AuthenticationInfo.getCurrent());

                    ConfirmacaoNotaHelper.confirmarNota(nunota, barramentoConfirmacao);

                    Collection<LiberacaoSolicitada> liberacoes = barramentoConfirmacao.getLiberacoesSolicitadas();
                    for (LiberacaoSolicitada lib : liberacoes) {
                        libPendentes.add("Evento " + lib.getEvento());
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Erro ao tentar reconfirmar a nota " + nunota + ": " + e.getMessage(), e);
        } finally {
            JapeSession.close(hnd);
        }
        return libPendentes;
    }

    private Map<BigDecimal, Object[]> carregarDadosProdutosComReferencia(EntityFacade dwf, java.util.Set<BigDecimal> codProds) throws Exception {
        Map<BigDecimal, Object[]> map = new HashMap<>();
        if (codProds.isEmpty()) return map;
        StringBuilder inList = new StringBuilder();
        for (BigDecimal cp : codProds) {
            if (inList.length() > 0) inList.append(",");
            inList.append(cp.toPlainString());
        }
        NativeSql sql = new NativeSql(dwf.getJdbcWrapper());
        ResultSet rs = sql.executeQuery(String.format(SQL_BATCH_PRODUTO_REFERENCIA, inList));
        while (rs.next()) {
            BigDecimal cp = rs.getBigDecimal("CODPROD");
            map.put(cp, new Object[]{
                rs.getString("CODVOL"),
                rs.getBigDecimal("CODLOCALPADRAO"),
                rs.getString("REFERENCIA")
            });
        }
        return map;
    }

    private BigDecimal calcularFaixaPrecoItem(BigDecimal vlrUnit, BigDecimal... faixas) {
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

    private void calculaImpostoItensETotalizaNota(BigDecimal nunota) throws Exception {
        JapeSession.SessionHandle hnd = null;
        try {
            hnd = JapeSession.open();
            hnd.execWithTX(new JapeSession.TXBlock() {
                public void doWithTx() throws Exception {
                    executarCalculoImpostos(nunota);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Erro ao calcular impostos/totalizar nota: " + e.getMessage(), e);
        } finally {
            JapeSession.close(hnd);
        }
    }

    private void executarCalculoImpostos(BigDecimal nunota) throws Exception {
        ImpostosHelpper imposto = new ImpostosHelpper();
        imposto.setForcarRecalculo(true);
        imposto.calcularImpostos(nunota);
        imposto.totalizarNota(nunota);
    }

    private boolean temCusto(ProdutoAlternativoDTO p) {
        return (p.getFaixa1() != null && p.getFaixa1().compareTo(BigDecimal.ZERO) > 0)
            || (p.getFaixa2() != null && p.getFaixa2().compareTo(BigDecimal.ZERO) > 0)
            || (p.getFaixa3() != null && p.getFaixa3().compareTo(BigDecimal.ZERO) > 0)
            || (p.getFaixa4() != null && p.getFaixa4().compareTo(BigDecimal.ZERO) > 0)
            || (p.getFaixa5() != null && p.getFaixa5().compareTo(BigDecimal.ZERO) > 0);
    }

    private BigDecimal aplicarDenominador(BigDecimal numerador, BigDecimal denominador) {
        if (numerador == null || numerador.compareTo(BigDecimal.ZERO) == 0) return null;
        return numerador.divide(denominador, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal aplicarFator(BigDecimal valor, BigDecimal fator) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) == 0) return null;
        return valor.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

}
