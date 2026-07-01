package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;

@Data
public class BuscarProdutosRequestDTO {

    private int    offset = 0;
    private int    limit  = 20;
    private String filtro;

}
