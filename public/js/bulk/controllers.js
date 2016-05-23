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

    $scope.getOngoingList = function() {
      var listBulkUploadOngoingUrl = $rootScope.urls.listBulkUploadOngoingUrl;
      BulkUpload.getList(listBulkUploadOngoingUrl).then(function(resultOngoing) {
        $log.info('Getting ongoing batches with  ' + listBulkUploadOngoingUrl);
        $scope.ongoing =resultOngoing.data.entity;
      });
    };
    $scope.getConcludedList = function() {
      var listBulkUploadConcludedUrl = $rootScope.urls.listBulkUploadConcludedUrl;
      BulkUpload.getList(listBulkUploadConcludedUrl).then(function(resultConcluded) {
        $log.info('Getting concluded batches with  ' + listBulkUploadConcludedUrl);
        $scope.concluded =resultConcluded.data.entity;
      });
    };
    $scope.getUploadList = function(batchId, $index) {
      // non stub version
      var bulkUploadListUrl = $rootScope.urls.bulkUploadPostUrl + '/' + batchId;
      if ($rootScope.stubs){
        // stub version
        bulkUploadListUrl = $rootScope.urls.bulkUploadList;
      }
      BulkUpload.getList(bulkUploadListUrl).then(function(resultList) {
        $log.info('Getting list of sites in a batch process  batches with  ' + bulkUploadListUrl);
        $scope.ongoing[$index].list = resultList.data.entity;
      });
      return null;
    };
    $scope.getOngoingList();
  }
]);
