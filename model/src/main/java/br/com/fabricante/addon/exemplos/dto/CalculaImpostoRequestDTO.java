package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CalculaImpostoRequestDTO {

    private BigDecimal codProd;
    private BigDecimal codTipOper;
    private BigDecimal codEmp;
    private BigDecimal codParc;

}
