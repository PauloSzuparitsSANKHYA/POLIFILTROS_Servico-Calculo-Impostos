package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;
import java.util.List;

@Data
public class BuscarAlternativosResponseDTO {

    private List<ProdutoAlternativoDTO> produtos;
    private boolean temMais;

}
