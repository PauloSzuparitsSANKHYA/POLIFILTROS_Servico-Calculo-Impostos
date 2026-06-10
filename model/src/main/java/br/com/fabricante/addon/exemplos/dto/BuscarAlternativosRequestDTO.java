package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BuscarAlternativosRequestDTO {

    private BigDecimal nuNota;
    private BigDecimal codProd;

}
