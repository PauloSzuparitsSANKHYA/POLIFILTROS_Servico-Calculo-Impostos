package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BuscarAlternativosRequestDTO {

    private BigDecimal nuNota;
    private BigDecimal codProd;
    private int offset = 0;
    private int limit  = 10;

}
