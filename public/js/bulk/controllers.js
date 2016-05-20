'use strict';
/* global projectMigrationApp*/

projectMigrationApp.controller('projectMigrationBatchController', ['$rootScope', '$scope', '$log', '$q', '$window', 'BulkUpload',
  function($rootScope, $scope, $log, $q, $window, BulkUpload) {
    $scope.bulkUpload = function() {
      var file = $scope.bulkUploadFile;
      var bulkUploadUrl = $rootScope.urls.bulkUploadPostUrl;
      BulkUpload.bulkUpload(file, bulkUploadUrl).then(function() {
        $log.info('hhh after ' + bulkUploadUrl);
        // TODO
      });
    };
  }
]);
