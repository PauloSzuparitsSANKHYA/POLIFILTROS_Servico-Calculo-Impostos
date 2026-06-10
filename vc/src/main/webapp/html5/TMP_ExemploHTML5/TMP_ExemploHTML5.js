angular.module('TMP_ExemploHTML5App', ['snk'])
    .controller('TMP_ExemploHTML5Controller', ['$scope',
        function ($scope) {
            let self = this;

            self.init = init;


            function init() {
                    alert("Você pode utilizar javascript...");
            }

            function readPermissions() {
                var authData = SkApplication.instance().getAuthorizationData();
            }
        }]);


