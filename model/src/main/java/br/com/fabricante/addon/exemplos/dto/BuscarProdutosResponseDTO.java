package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;
import java.util.List;

@Data
public class BuscarProdutosResponseDTO {

    private List<ProdutoListaDTO> produtos;
    private boolean               temMais;

}
