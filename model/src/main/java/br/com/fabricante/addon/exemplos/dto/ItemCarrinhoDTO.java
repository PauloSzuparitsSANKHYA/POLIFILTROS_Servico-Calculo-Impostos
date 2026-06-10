package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ItemCarrinhoDTO {

    private BigDecimal codProd;
    private BigDecimal vlrUnit;
    private BigDecimal qtd;
    private BigDecimal codProdOrigem;
    private BigDecimal faixa1;
    private BigDecimal faixa2;
    private BigDecimal faixa3;
    private BigDecimal faixa4;
    private BigDecimal faixa5;

}
