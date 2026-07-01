package br.com.fabricante.addon.exemplos.dto;

import lombok.Data;

import java.util.List;

@Data
public class IncluirItensResponseDTO {

    private String mensagem;
    private int qtdItensInseridos;
    private List<String> liberacoesPendentes;

}
