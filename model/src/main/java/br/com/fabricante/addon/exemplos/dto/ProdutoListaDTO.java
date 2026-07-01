package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProdutoListaDTO {

    private BigDecimal codProd;
    private String     referencia;
    private String     descrProd;
    private String     codVol;
    private String     dtProxEntrega;

}
