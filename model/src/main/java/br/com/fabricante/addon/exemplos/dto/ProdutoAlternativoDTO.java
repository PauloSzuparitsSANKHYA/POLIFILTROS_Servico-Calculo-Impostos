package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProdutoAlternativoDTO {

    private int ordemHierarquia;
    private BigDecimal codProd;
    private String referencia;
    private String descrProd;
    private String comercializa;
    private String marca;
    private BigDecimal codGrupoProd;
    private String descrGrupoProd;
    private BigDecimal codGruMarkup;
    private String descrGruMarkup;
    private BigDecimal prioridade;
    private BigDecimal estoque;
    private BigDecimal saldo;
    private BigDecimal reservado;
    private BigDecimal compraPendente;
    private BigDecimal faixa1;
    private BigDecimal faixa2;
    private BigDecimal faixa3;
    private BigDecimal faixa4;
    private BigDecimal faixa5;
    private String dtProxEntrega;
    private BigDecimal vlrUltimaCotacao;
    private BigDecimal vlrUltimaVenda;
    private BigDecimal percDesc;

}
