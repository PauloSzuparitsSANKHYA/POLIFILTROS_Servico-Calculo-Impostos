package br.com.fabricante.addon.exemplos.services;

import br.com.fabricante.addon.exemplos.dto.BuscarAlternativosRequestDTO;
import br.com.fabricante.addon.exemplos.dto.BuscarAlternativosResponseDTO;
import br.com.fabricante.addon.exemplos.dto.CalculaImpostoRequestDTO;
import br.com.fabricante.addon.exemplos.dto.CalculaImpostoResponseDTO;
import br.com.fabricante.addon.exemplos.dto.IncluirItensRequestDTO;
import br.com.fabricante.addon.exemplos.dto.IncluirItensResponseDTO;
import br.com.fabricante.addon.exemplos.dto.ItemCarrinhoDTO;
import br.com.fabricante.addon.exemplos.dto.ProdutoAlternativoDTO;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.PrePersistEntityState;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidCreateVO;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.comercial.centrais.CACHelper;
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.studio.annotations.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        "        (SELECT ITE.VLRUNIT FROM TGFCAB CAB JOIN TGFITE ITE ON ITE.NUNOTA = CAB.NUNOTA WHERE ITE.CODPROD = PRO.CODPROD AND CAB.CODPARC = :CODPARC AND CAB.CODTIPOPER = 1000 AND CAB.STATUSNOTA = 'L' AND CAB.DTMOV = (SELECT MAX(DTMOV) FROM TGFCAB WHERE CODTIPOPER = 1000 AND STATUSNOTA = 'L')) AS VLRULTIMACOTACAO," +
        "        (SELECT ITE.VLRUNIT FROM TGFCAB CAB JOIN TGFITE ITE ON ITE.NUNOTA = CAB.NUNOTA WHERE ITE.CODPROD = PRO.CODPROD AND CAB.CODPARC = :CODPARC AND CAB.TIPMOV = 'V' AND CAB.STATUSNOTA = 'L' AND CAB.DTMOV = (SELECT MAX(DTMOV) FROM TGFCAB WHERE CAB.TIPMOV = 'V' AND STATUSNOTA = 'L')) AS VLRULTIMAVENDA" +
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
        ")" +
        " PIVOT (" +
        "    MAX(PRECO_CALCULADO)" +
        "    FOR CODMARKUP IN (1 AS FAIXA1, 2 AS FAIXA2, 3 AS FAIXA3, 4 AS FAIXA4, 5 AS FAIXA5)" +
        ")" +
        " ORDER BY ORDEMHIERARQUIA ASC, CODPROD ASC";

//    public CalculaImpostoResponseDTO calcularValorUnitario(CalculaImpostoRequestDTO request) throws Exception {
//        BigDecimal codProd    = request.getCodProd();
//        BigDecimal codTipOper = request.getCodTipOper();
//        BigDecimal codEmp     = request.getCodEmp();
//        BigDecimal codParc    = request.getCodParc();
//
//        BigDecimal[] vlrUnit = { BigDecimal.ZERO };
//
//        JapeSession.SessionHandle hnd = null;
//        try {
//            hnd = JapeSession.open();
//
//            hnd.execWithFakeTX(new JapeSession.TXBlock() {
//                public void doWithTx() throws Exception {
//
//                    // 1. Busca dados do produto (CODVOL e local padrão)
//                    DynamicVO produtoVO = JapeFactory.dao(DynamicEntityNames.PRODUTO).findByPK(codProd);
//                    String codVol = produtoVO.asString("CODVOL");
//                    BigDecimal codLocal = produtoVO.asBigDecimalOrZero("CODLOCALPADRAO");
//
//                    // 2. Busca CODNAT a partir da TOP (pega a versão mais recente)
//                    Collection<DynamicVO> tops = JapeFactory.dao(DynamicEntityNames.TIPO_OPERACAO)
//                            .find("CODTIPOPER = ? ORDER BY DHTIPOPER DESC", codTipOper);
//                    BigDecimal codNat = BigDecimal.ONE;
//                    if (!tops.isEmpty()) {
//                        codNat = tops.iterator().next().asBigDecimalOrZero("CODNAT");
//                    }
//
//                    // 3. Cria o CabecalhoNota
//                    logger.info("Criando CabecalhoNota fake - CODPARC=" + codParc + " CODTIPOPER=" + codTipOper + " CODEMP=" + codEmp);
//                    DynamicVO cabVO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA)
//                            .create()
//                            .set("CODPARC", codParc)
//                            .set("CODTIPOPER", codTipOper)
//                            .set("CODTIPVENDA", BigDecimal.ZERO)
//                            .set("CODNAT", codNat)
//                            .set("CODEMP", codEmp)
//                            .set("DTNEG", new Timestamp(System.currentTimeMillis()))
//                            .set("NUMNOTA", BigDecimal.ZERO)
//                            .set("SERIENOTA", "0")
//                            .save();
//
//                    BigDecimal nunota = cabVO.asBigDecimal("NUNOTA");
//                    logger.info("CabecalhoNota criado - NUNOTA=" + nunota);
//
//                    // 4. Cria o ItemNota via CACHelper para que o Sankhya calcule os valores
//                    AuthenticationInfo auth = AuthenticationInfo.getCurrent();
//                    EntityFacade entityFacade = EntityFacadeFactory.getDWFFacade();
//                    CACHelper cacHelper = new CACHelper();
//
//                    DynamicVO itemVO = (DynamicVO) entityFacade
//                            .getDefaultValueObjectInstance(DynamicEntityNames.ITEM_NOTA);
//
//                    itemVO.setProperty("NUNOTA", nunota);
//                    itemVO.setProperty("CODPROD", codProd);
//                    itemVO.setProperty("QTDNEG", BigDecimal.ONE);
//                    itemVO.setProperty("VLRUNIT", BigDecimal.ZERO);
//                    itemVO.setProperty("VLRTOT", BigDecimal.ZERO);
//                    itemVO.setProperty("CODVOL", codVol);
//                    if (codLocal.compareTo(BigDecimal.ZERO) > 0) {
//                        itemVO.setProperty("CODLOCALORIG", codLocal);
//                    }
//
//                    Collection<PrePersistEntityState> itensNota = new ArrayList<>();
//                    itensNota.add(PrePersistEntityState.build(entityFacade, DynamicEntityNames.ITEM_NOTA, itemVO));
//
//                    cacHelper.incluirAlterarItem(nunota, auth, itensNota, true);
//                    logger.info("ItemNota inserido via CACHelper - NUNOTA=" + nunota + " CODPROD=" + codProd);
//
//                    // 5. Lê o VLRUNIT calculado pelo Sankhya
//                    Collection<DynamicVO> itens = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA)
//                            .find("NUNOTA = ?", nunota);
//
//                    if (!itens.isEmpty()) {
//                        vlrUnit[0] = itens.iterator().next().asBigDecimalOrZero("VLRUNIT");
//                        logger.info("VLRUNIT calculado=" + vlrUnit[0]);
//                    }
//                    // ao terminar o bloco, execWithFakeTX faz rollback automaticamente
//                }
//            });
//        } catch (Exception e) {
//            throw new RuntimeException("Erro ao calcular valor unitário: " + e.getMessage(), e);
//        } finally {
//            JapeSession.close(hnd);
//        }
//
//        CalculaImpostoResponseDTO response = new CalculaImpostoResponseDTO();
//        response.setVlrUnit(vlrUnit[0]);
//        return response;
//    }

    public BuscarAlternativosResponseDTO buscarAlternativos(BuscarAlternativosRequestDTO request) throws Exception {
        BigDecimal nuNota  = request.getNuNota();
        BigDecimal codProd = request.getCodProd();

        List<ProdutoAlternativoDTO> produtos = new ArrayList<>();
        Map<BigDecimal, BigDecimal> impostosMap  = new HashMap<>();
        Map<BigDecimal, BigDecimal> percDescMap  = new HashMap<>();
        java.util.Set<BigDecimal> failedProducts = new java.util.HashSet<>();

        JapeSession.SessionHandle hnd = null;
        try {
            hnd = JapeSession.open();

            // 1. Ler CabecalhoNota existente para obter CODEMP, CODPARC, CODTIPOPER e CODNAT
            DynamicVO cabExistente = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA).findByPK(nuNota);
            if (cabExistente == null) {
                throw new IllegalArgumentException("NF/Pedido não encontrado para NUNOTA=" + nuNota);
            }
            BigDecimal codEmp      = cabExistente.asBigDecimalOrZero("CODEMP");
            BigDecimal codParc     = cabExistente.asBigDecimalOrZero("CODPARC");
            BigDecimal codTipOper  = cabExistente.asBigDecimalOrZero("CODTIPOPER");
            BigDecimal codNat      = cabExistente.asBigDecimalOrZero("CODNAT");
            BigDecimal codTipVenda = cabExistente.asBigDecimalOrZero("CODTIPVENDA");
            BigDecimal codCenCus   = cabExistente.asBigDecimalOrZero("CODCENCUS");
            logger.info("buscarAlternativos - lido NUNOTA=" + nuNota + " CODEMP=" + codEmp + " CODPARC=" + codParc + " CODTIPOPER=" + codTipOper + " CODTIPVENDA=" + codTipVenda);

            // 2. Executar SQL para obter produtos e alternativos com numeradores de preço
            EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
            NativeSql nativeSql = new NativeSql(dwf.getJdbcWrapper());
            nativeSql.setNamedParameter("CODEMP", codEmp);
            nativeSql.setNamedParameter("CODPROD", codProd);
            nativeSql.setNamedParameter("CODPARC", codParc);
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

            // 3. Calcular impostos para cada produto via fake transaction
            if (!produtos.isEmpty()) {
                final BigDecimal fCodEmp      = codEmp;
                final BigDecimal fCodParc     = codParc;
                final BigDecimal fCodTipOper  = codTipOper;
                final BigDecimal fCodNat      = codNat;
                final BigDecimal fCodTipVenda = codTipVenda;
                final BigDecimal fCodCenCus   = codCenCus;

                hnd.execWithFakeTX(new JapeSession.TXBlock() {
                    public void doWithTx() throws Exception {

                        // Cria CabecalhoNota fake copiando todos os campos relevantes da nota real (Pattern 2 - skill sankhya-cabecalho-item)
                        DynamicVO cabVO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA)
                                .create()
                                .set("CODPARC",    fCodParc)
                                .set("CODTIPOPER", fCodTipOper)
                                .set("CODTIPVENDA", fCodTipVenda)
                                .set("CODNAT",     fCodNat)
                                .set("CODEMP",     fCodEmp)
                                .set("CODCENCUS",  fCodCenCus)
                                .set("DTNEG",      new Timestamp(System.currentTimeMillis()))
                                .set("NUMNOTA",    BigDecimal.ZERO)
                                .set("SERIENOTA",  "0")
                                .save();
                        BigDecimal nunota = cabVO.asBigDecimal("NUNOTA");
                        logger.info("buscarAlternativos - CabecalhoNota fake criado NUNOTA=" + nunota);

                        AuthenticationInfo auth = AuthenticationInfo.getCurrent();
                        EntityFacade entityFacade = EntityFacadeFactory.getDWFFacade();
                        CACHelper cacHelper = new CACHelper();

                        // Para cada produto, cria ItemNota e soma alíquotas via TGFDIN
                        for (ProdutoAlternativoDTO p : produtos) {
                            BigDecimal codProdItem = p.getCodProd();
                            try {
                                DynamicVO prodItemVO = JapeFactory.dao(DynamicEntityNames.PRODUTO).findByPK(codProdItem);
                                String codVol = prodItemVO.asString("CODVOL");
                                BigDecimal codLocal = prodItemVO.asBigDecimalOrZero("CODLOCALPADRAO");
                                BigDecimal multipVenda = prodItemVO.asBigDecimalOrZero("QTDEMB");
                                BigDecimal qtdNeg = multipVenda.compareTo(BigDecimal.ONE) > 0 ? multipVenda : BigDecimal.ONE;

                                DynamicVO itemVO = (DynamicVO) entityFacade
                                        .getDefaultValueObjectInstance(DynamicEntityNames.ITEM_NOTA);
                                itemVO.setProperty("NUNOTA", nunota);
                                itemVO.setProperty("CODPROD", codProdItem);
                                itemVO.setProperty("QTDNEG", qtdNeg);
                                itemVO.setProperty("VLRUNIT", BigDecimal.ZERO);
                                itemVO.setProperty("VLRTOT", BigDecimal.ZERO);
                                itemVO.setProperty("CODVOL", codVol);
                                itemVO.setProperty("AD_CALCIMPFAIXA", "S");
                                if (codLocal.compareTo(BigDecimal.ZERO) > 0) {
                                    itemVO.setProperty("CODLOCALORIG", codLocal);
                                }

                                Collection<PrePersistEntityState> itensNota = new ArrayList<>();
                                itensNota.add(PrePersistEntityState.build(entityFacade, DynamicEntityNames.ITEM_NOTA, itemVO));
                                cacHelper.incluirAlterarItem(nunota, auth, itensNota, true);
                                executarCalculoImpostos(nunota);

                                Collection<DynamicVO> itens = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA)
                                        .find("NUNOTA = ? AND CODPROD = ?", nunota, codProdItem);
                                if (!itens.isEmpty()) {
                                    DynamicVO itemLido = itens.iterator().next();
                                    BigDecimal sequencia = itemLido.asBigDecimalOrZero("SEQUENCIA");
                                    BigDecimal percDesc  = itemLido.asBigDecimalOrZero("PERCDESC");
                                    percDescMap.put(codProdItem, percDesc);

                                    NativeSql sqlDin = new NativeSql(entityFacade.getJdbcWrapper());
                                    sqlDin.setNamedParameter("NUNOTA", nunota);
                                    sqlDin.setNamedParameter("SEQUENCIA", sequencia);
                                    ResultSet rsDin = sqlDin.executeQuery(
                                        "SELECT NVL(SUM(ALIQUOTA), 0) AS TOTAL_ALIQ FROM TGFDIN WHERE NUNOTA = :NUNOTA AND SEQUENCIA = :SEQUENCIA"
                                    );
                                    BigDecimal totalAliq = BigDecimal.ZERO;
                                    if (rsDin.next()) {
                                        BigDecimal v = rsDin.getBigDecimal("TOTAL_ALIQ");
                                        if (v != null) totalAliq = v;
                                    }
                                    impostosMap.put(codProdItem, totalAliq);
                                    logger.info("CODPROD=" + codProdItem + " SEQUENCIA=" + sequencia + " TOTAL_ALIQ_TGFDIN=" + totalAliq);
                                }
                            } catch (Exception ex) {
                                logger.warning("buscarAlternativos - CODPROD=" + codProdItem + " ignorado no cálculo de impostos: " + ex.getMessage());
                                failedProducts.add(codProdItem);
                            }
                        }
                        // execWithFakeTX faz rollback automático — nenhuma nota persiste
                    }
                });
            }

            // 3. Aplicar fórmula: preco_final_faixa = numerador / (1 - impostos/100) * (1 - percDesc/100)
            for (ProdutoAlternativoDTO p : produtos) {
                if (failedProducts.contains(p.getCodProd())) {
                    p.setFaixa1(null);
                    p.setFaixa2(null);
                    p.setFaixa3(null);
                    p.setFaixa4(null);
                    p.setFaixa5(null);
                    continue;
                }
                // Passo 1: dividir pelo denominador de impostos
                BigDecimal impostos = impostosMap.getOrDefault(p.getCodProd(), BigDecimal.ZERO);
                BigDecimal denominador = BigDecimal.ONE.subtract(
                        impostos.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
                if (denominador.compareTo(BigDecimal.ZERO) > 0) {
                    p.setFaixa1(aplicarDenominador(p.getFaixa1(), denominador));
                    p.setFaixa2(aplicarDenominador(p.getFaixa2(), denominador));
                    p.setFaixa3(aplicarDenominador(p.getFaixa3(), denominador));
                    p.setFaixa4(aplicarDenominador(p.getFaixa4(), denominador));
                    p.setFaixa5(aplicarDenominador(p.getFaixa5(), denominador));
                }
                // Passo 2: aplicar desconto calculado pelo Sankhya (PERCDESC)
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
            }

        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar alternativos: " + e.getMessage(), e);
        } finally {
            JapeSession.close(hnd);
        }

        BuscarAlternativosResponseDTO response = new BuscarAlternativosResponseDTO();
        response.setProdutos(produtos);
        return response;
    }

    public IncluirItensResponseDTO incluirItens(IncluirItensRequestDTO request) throws Exception {
        BigDecimal nuNota = request.getNuNota();
        List<ItemCarrinhoDTO> itens = request.getItens();

        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item informado para inserção.");
        }

        final int[] inseridos = { 0 };
        JapeSession.SessionHandle hnd = null;
        try {
            hnd = JapeSession.open();

            hnd.execEnsuringTX(new JapeSession.TXBlock() {
                public void doWithTx() throws Exception {
                    JapeWrapper iteDAO = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA);

                    for (ItemCarrinhoDTO item : itens) {
                        BigDecimal codProd = item.getCodProd();
                        BigDecimal vlrUnit = item.getVlrUnit() != null ? item.getVlrUnit() : BigDecimal.ZERO;
                        BigDecimal qtd     = item.getQtd() != null && item.getQtd().compareTo(BigDecimal.ZERO) > 0
                                             ? item.getQtd() : BigDecimal.ONE;

                        DynamicVO prodVO    = JapeFactory.dao(DynamicEntityNames.PRODUTO).findByPK(codProd);
                        String    codVol    = prodVO.asString("CODVOL");
                        BigDecimal codLocal = prodVO.asBigDecimalOrZero("CODLOCALPADRAO");

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
                            DynamicVO prodOrigemVO = JapeFactory.dao(DynamicEntityNames.PRODUTO).findByPK(item.getCodProdOrigem());
                            String referencia = prodOrigemVO != null ? prodOrigemVO.asString("REFERENCIA") : null;
                            itemVO.set("AD_REFSOLICITADA", referencia);
                        }
                            BigDecimal faixaPreco = calcularFaixaPreco(vlrUnit,
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

        calculaImpostoItensETotalizaNota(nuNota);

        IncluirItensResponseDTO response = new IncluirItensResponseDTO();
        response.setMensagem(inseridos[0] + " item(ns) inserido(s) com sucesso na nota " + nuNota + ".");
        response.setQtdItensInseridos(inseridos[0]);
        return response;
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

    private BigDecimal aplicarDenominador(BigDecimal numerador, BigDecimal denominador) {
        if (numerador == null || numerador.compareTo(BigDecimal.ZERO) == 0) return null;
        return numerador.divide(denominador, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal aplicarFator(BigDecimal valor, BigDecimal fator) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) == 0) return null;
        return valor.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularFaixaPreco(BigDecimal vlrUnit, BigDecimal... faixas) {
        if (vlrUnit == null || vlrUnit.compareTo(BigDecimal.ZERO) <= 0) return null;
        int melhorIndice = -1;
        BigDecimal menorDiff = null;
        for (int i = 0; i < faixas.length; i++) {
            if (faixas[i] == null) continue;
            BigDecimal diff = vlrUnit.subtract(faixas[i]).abs();
            if (menorDiff == null || diff.compareTo(menorDiff) < 0) {
                menorDiff = diff;
                melhorIndice = i;
            }
        }
        return melhorIndice >= 0 ? new BigDecimal(melhorIndice + 1) : null;
    }
}
