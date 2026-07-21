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
import br.com.fabricante.addon.exemplos.util.FaixaPrecoImpostoHelper;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.JapeSessionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.PrePersistEntityState;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.comercial.BarramentoRegra;
import br.com.sankhya.modelcore.comercial.CentralFaturamento;
import br.com.sankhya.modelcore.comercial.ConfirmacaoNotaHelper;
import br.com.sankhya.modelcore.comercial.LiberacaoSolicitada;
import br.com.sankhya.modelcore.comercial.centrais.CACHelper;
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.studio.annotations.Service;
import br.com.sankhya.ws.ServiceContext;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Logger;

@Service(serviceName = "CalculaImpostoSP")
public class CalculaImpostoController {

    private static final Logger logger = Logger.getLogger(CalculaImpostoController.class.getName());

    private final CACHelper cacHelper = new CACHelper();

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
        "        (NVL(CUS.CUSGER, 0) * (1 - NVL(DES.PERCENTUAL, 0) / 100.0)) / NULLIF(1 - ((NVL(PMK.PERCMARKUP, 0) + NVL((SELECT SUM(PERCDESP) FROM AD_DESPOPER WHERE ATIVO = 'S' AND CODEMP = :CODEMP), 0)) / 100.0), 0) AS PRECO_CALCULADO," +
        "        NVL(DES.PERCENTUAL, 0) AS PERCDESC," +
        "        NVL(EST.ESTOQUE, 0) AS ESTOQUE," +
        "        NVL(EST.ESTOQUE, 0) - NVL(EST.RESERVADO, 0) AS SALDO," +
        "        NVL(EST.RESERVADO, 0) AS RESERVADO," +
        "        COALESCE((SELECT SUM(I.QTDNEG) FROM TGFITE I JOIN TGFCAB C ON I.NUNOTA = C.NUNOTA WHERE I.CODPROD = PRO.CODPROD AND I.PENDENTE = 'S' AND C.TIPMOV = 'O' AND C.STATUSNOTA = 'L'), 0) AS COMPRAPENDENTE," +
        "        (SELECT TO_CHAR(MIN(ITE2.AD_DTPREVENTGER), 'DD/MM/YYYY') FROM TGFITE ITE2 JOIN TGFCAB CAB2 ON CAB2.NUNOTA = ITE2.NUNOTA WHERE ITE2.CODPROD = PRO.CODPROD AND ITE2.CODEMP = :CODEMP AND CAB2.STATUSNOTA = 'L' AND CAB2.PENDENTE = 'S' AND ITE2.AD_DTPREVENTGER IS NOT NULL) AS DTPROXENTREGA," +
        "        (SELECT MAX(ITE.VLRUNIT) FROM TGFCAB CAB JOIN TGFITE ITE ON ITE.NUNOTA = CAB.NUNOTA WHERE ITE.CODPROD = PRO.CODPROD AND CAB.CODPARC = :CODPARC AND CAB.CODTIPOPER = 1000 AND CAB.STATUSNOTA = 'L' AND CAB.DTNEG = (SELECT MAX(DTNEG) FROM TGFCAB WHERE CODTIPOPER = 1000 AND STATUSNOTA = 'L')) AS VLRULTIMACOTACAO," +
        "        (SELECT MAX(ITE.VLRUNIT) FROM TGFCAB CAB JOIN TGFITE ITE ON ITE.NUNOTA = CAB.NUNOTA WHERE ITE.CODPROD = PRO.CODPROD AND CAB.CODPARC = :CODPARC AND CAB.TIPMOV = 'V' AND CAB.STATUSNOTA = 'L' AND CAB.DTNEG = (SELECT MAX(DTNEG) FROM TGFCAB WHERE CAB.TIPMOV = 'V' AND STATUSNOTA = 'L')) AS VLRULTIMAVENDA" +
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
        "    LEFT JOIN TGFEST EST ON PRO.CODPROD = EST.CODPROD AND EST.CODEMP = :CODEMP" +
        "        AND ((:IS_TOP1057 = 1 AND EST.CODLOCAL = 201) OR (:IS_TOP1057 = 0 AND EST.CODLOCAL = 101))" +
        "        AND EST.CODPARC = 0" +
        "        AND EST.CONTROLE = ' '" +
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

    private static final String SQL_ZERAR_DESCONTO_ITEM =
        "UPDATE TGFITE SET PERCDESC = 0, VLRDESC = 0" +
        " WHERE NUNOTA = :NUNOTA AND CODPROD IN (%s)";

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
                dto.setEstoque(rs.getBigDecimal("ESTOQUE"));
                dto.setSaldo(rs.getBigDecimal("SALDO"));
                dto.setReservado(rs.getBigDecimal("RESERVADO"));
                dto.setCompraPendente(rs.getBigDecimal("COMPRAPENDENTE"));
                dto.setDtProxEntrega(rs.getString("DTPROXENTREGA"));
                dto.setVlrUltimaCotacao(rs.getBigDecimal("VLRULTIMACOTACAO"));
                dto.setVlrUltimaVenda(rs.getBigDecimal("VLRULTIMAVENDA"));
                dto.setFaixa1(rs.getBigDecimal("FAIXA1"));
                dto.setFaixa2(rs.getBigDecimal("FAIXA2"));
                dto.setFaixa3(rs.getBigDecimal("FAIXA3"));
                dto.setFaixa4(rs.getBigDecimal("FAIXA4"));
                dto.setFaixa5(rs.getBigDecimal("FAIXA5"));
                dto.setPercDesc(rs.getBigDecimal("PERCDESC"));
                produtos.add(dto);
            }
            logger.info("buscarAlternativos - produtos encontrados: " + produtos.size() + " para CODPROD=" + codProd);

            // 3. Calcular denominador de impostos por item: (1-ALIQICMS/100) * (1-(ALIQPIS+ALIQCOFINS)/100)
            if (!produtos.isEmpty()) {
                EntityFacade entityFacade = EntityFacadeFactory.getDWFFacade();

                for (ProdutoAlternativoDTO p : produtos) {
                    BigDecimal codProdItem = p.getCodProd();
                    if (!temCusto(p)) {
                        impostosMap.put(codProdItem, BigDecimal.ONE);
                        continue;
                    }
                    try {
                        BigDecimal denominador = FaixaPrecoImpostoHelper.calcularDenominadorImpostos(
                                entityFacade, nuNota, codEmp, codProdItem, codParc, codTipOper);
                        impostosMap.put(codProdItem, denominador);
                    } catch (Exception ex) {
                        logger.warning("buscarAlternativos - CODPROD=" + codProdItem + " ignorado: " + ex.getMessage());
                        failedProducts.add(codProdItem);
                    }
                }
                logger.info("buscarAlternativos - impostos calculados para " + impostosMap.size() + " produto(s)");
            }

            // 4. Aplicar grossup de impostos: preco_final_faixa = numerador / denominador
            // (o desconto promocional já foi aplicado ao CUSGER dentro do SQL_ALTERNATIVOS)
            for (ProdutoAlternativoDTO p : produtos) {
                if (failedProducts.contains(p.getCodProd())) {
                    p.setFaixa1(null);
                    p.setFaixa2(null);
                    p.setFaixa3(null);
                    p.setFaixa4(null);
                    p.setFaixa5(null);
                    continue;
                }
                BigDecimal denominador = impostosMap.getOrDefault(p.getCodProd(), BigDecimal.ONE);
                logger.info("[FORMULA] CODPROD=" + p.getCodProd()
                        + " | faixas brutas: F1=" + p.getFaixa1() + " F2=" + p.getFaixa2()
                        + " F3=" + p.getFaixa3() + " F4=" + p.getFaixa4() + " F5=" + p.getFaixa5()
                        + " | denominador=" + denominador);
                if (denominador.compareTo(BigDecimal.ZERO) > 0) {
                    p.setFaixa1(FaixaPrecoImpostoHelper.aplicarDenominador(p.getFaixa1(), denominador));
                    p.setFaixa2(FaixaPrecoImpostoHelper.aplicarDenominador(p.getFaixa2(), denominador));
                    p.setFaixa3(FaixaPrecoImpostoHelper.aplicarDenominador(p.getFaixa3(), denominador));
                    p.setFaixa4(FaixaPrecoImpostoHelper.aplicarDenominador(p.getFaixa4(), denominador));
                    p.setFaixa5(FaixaPrecoImpostoHelper.aplicarDenominador(p.getFaixa5(), denominador));
                }
                logger.info("[FORMULA] CODPROD=" + p.getCodProd()
                        + " | faixas finais: F1=" + p.getFaixa1() + " F2=" + p.getFaixa2()
                        + " F3=" + p.getFaixa3() + " F4=" + p.getFaixa4() + " F5=" + p.getFaixa5()
                        + " | percDesc=" + p.getPercDesc() + "%");
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

            final EntityFacade dwf = EntityFacadeFactory.getDWFFacade();

            NativeSql sqlAltNfConf = new NativeSql(dwf.getJdbcWrapper());
            sqlAltNfConf.setNamedParameter("NUNOTA", nuNota);
            ResultSet rsAltNfConf = sqlAltNfConf.executeQuery(
                "SELECT TOP.ALTNFCONF " +
                "FROM TGFCAB CAB " +
                "JOIN TGFTOP TOP ON TOP.CODTIPOPER = CAB.CODTIPOPER AND TOP.DHALTER = CAB.DHTIPOPER " +
                "WHERE CAB.NUNOTA = :NUNOTA"
            );
            boolean altNfConf = rsAltNfConf.next() && "S".equals(rsAltNfConf.getString("ALTNFCONF"));
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
            Map<BigDecimal, Object[]> produtoDataMap = carregarDadosProdutosComReferencia(dwf, codProdParaCarregar);

            final ServiceContext serviceContext = ServiceContext.getCurrent();

            hnd.execEnsuringTX(new JapeSession.TXBlock() {
                public void doWithTx() throws Exception {
                    JapeSessionContext.putProperty("br.com.sankhya.com.CentralCompraVenda", Boolean.TRUE);
                    JapeSessionContext.putProperty("ItemNota.incluindo.alterando.pela.central", Boolean.TRUE);

                    Collection<PrePersistEntityState> itensNotaStates = new ArrayList<>();

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

                        DynamicVO itemVO = (DynamicVO) dwf.getDefaultValueObjectInstance(DynamicEntityNames.ITEM_NOTA);
                        itemVO.setProperty("NUNOTA",   nuNota);
                        itemVO.setProperty("CODPROD",  codProd);
                        itemVO.setProperty("QTDNEG",   qtd);
                        itemVO.setProperty("CODVOL",   codVol);
                        itemVO.setProperty("VLRUNIT",  vlrUnit);
                        itemVO.setProperty("PERCDESC", BigDecimal.ZERO);
                        itemVO.setProperty("VLRDESC", BigDecimal.ZERO);
                        itemVO.setProperty("VLRTOT",   vlrUnit.multiply(qtd));
                        if (codLocal.compareTo(BigDecimal.ZERO) > 0) {
                            itemVO.setProperty("CODLOCALORIG", codLocal);
                        }
                        itemVO.setProperty("BASEICMS", BigDecimal.ZERO);
                        itemVO.setProperty("VLRICMS",  BigDecimal.ZERO);
                        itemVO.setProperty("ALIQICMS", BigDecimal.ZERO);
                        itemVO.setProperty("BASEIPI",  BigDecimal.ZERO);
                        itemVO.setProperty("VLRIPI",   BigDecimal.ZERO);
                        itemVO.setProperty("ALIQIPI",  BigDecimal.ZERO);
                        if (item.getCodProdOrigem() != null) {
                            Object[] origemData = produtoDataMap.get(item.getCodProdOrigem());
                            String referencia = origemData != null ? (String) origemData[2] : null;
                            itemVO.setProperty("AD_REFSOLICITADA", referencia);
                        }
                        BigDecimal faixaPreco = calcularFaixaPrecoItem(vlrUnit,
                            item.getFaixa1(), item.getFaixa2(), item.getFaixa3(),
                            item.getFaixa4(), item.getFaixa5());

                        itemVO.setProperty("AD_FAIXAPRECO", faixaPreco);
                        itemVO.setProperty("NUPROMOCAO",  null);


                        PrePersistEntityState itePreState = PrePersistEntityState.build(dwf, DynamicEntityNames.ITEM_NOTA, itemVO);
                        itePreState.getNewVO();
                        itensNotaStates.add(itePreState);

                        logger.info("incluirItens - CODPROD=" + codProd + " QTD=" + qtd + " VLR=" + vlrUnit + " NUNOTA=" + nuNota);
                        inseridos[0]++;
                    }

                        cacHelper.incluirAlterarItem(nuNota, serviceContext, itensNotaStates, false);

                        StringBuilder codProdsInseridos = new StringBuilder();
                        for (ItemCarrinhoDTO item : itens) {
                            if (codProdsInseridos.length() > 0) codProdsInseridos.append(",");
                            codProdsInseridos.append(item.getCodProd().toPlainString());
                        }
                        NativeSql nativeSqlZeraDesc = new NativeSql(dwf.getJdbcWrapper());
                        nativeSqlZeraDesc.setNamedParameter("NUNOTA", nuNota);
                        nativeSqlZeraDesc.executeUpdate(String.format(SQL_ZERAR_DESCONTO_ITEM, codProdsInseridos));
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
        return FaixaPrecoImpostoHelper.temCusto(
                p.getFaixa1(), p.getFaixa2(), p.getFaixa3(), p.getFaixa4(), p.getFaixa5());
    }

}
