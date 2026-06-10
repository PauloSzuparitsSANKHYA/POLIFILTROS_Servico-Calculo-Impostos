angular.module('TesteAlternativosApp', ['snk'])
    .controller('TesteAlternativosController', TesteAlternativosController);

TesteAlternativosController.$inject = ['ServiceProxy', 'MessageUtils'];

function TesteAlternativosController(ServiceProxy, MessageUtils) {
    var vm = this;

    vm.nuNota    = null;
    vm.codProd   = null;
    vm.produtos  = [];
    vm.carregando = false;
    vm.buscaFeita = false;
    vm.erro      = null;

    vm.init               = init;
    vm.buscarAlternativos = buscarAlternativos;
    vm.fmt                = fmt;

    function init() {}

    function buscarAlternativos() {
        if (!vm.nuNota || !vm.codProd) {
            MessageUtils.showErrorMsg('Preencha o NUNOTA e o CODPROD antes de buscar.');
            return;
        }

        vm.carregando = true;
        vm.buscaFeita = false;
        vm.produtos   = [];
        vm.erro       = null;

        ServiceProxy.callService(
            'servico-calcula-imposto@CalculaImpostoSP.buscarAlternativos',
            { request: { nuNota: Number(vm.nuNota), codProd: Number(vm.codProd) } }
        )
        .then(function(response) {
            vm.carregando = false;
            vm.buscaFeita = true;
            vm.produtos = (response.responseBody && response.responseBody.body && response.responseBody.body.produtos)
                          ? response.responseBody.body.produtos : [];
        })
        .catch(function(error) {
            vm.carregando = false;
            vm.buscaFeita = true;
            vm.erro = error && error.statusMessage ? error.statusMessage : JSON.stringify(error);
            MessageUtils.showErrorMsg('Erro: ' + vm.erro);
        });
    }

    function fmt(val) {
        if (val == null || val === 0) return '-';
        return Number(val).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
}
