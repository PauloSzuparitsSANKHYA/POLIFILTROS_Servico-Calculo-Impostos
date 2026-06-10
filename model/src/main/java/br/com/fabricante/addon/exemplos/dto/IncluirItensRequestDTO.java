package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class IncluirItensRequestDTO {

    private BigDecimal nuNota;
    private List<ItemCarrinhoDTO> itens;

}
