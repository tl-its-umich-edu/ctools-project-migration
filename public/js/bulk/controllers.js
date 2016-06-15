'use strict';
/* global projectMigrationApp, validateBulkRequest, $*/

projectMigrationApp.controller('projectMigrationBatchController', ['$rootScope', '$scope', '$log', '$q', '$window', '$timeout', 'BulkUpload',
  function($rootScope, $scope, $log, $q, $window, $timeout, BulkUpload) {

    $scope.bulkUpload = function() {
      //$log.info($scope.upload.name);
      if(!$scope.bulkUploadFile || !$scope.upload.name){
        $('#bulkUploadFileContainer').find('.form-group').addClass("has-error");
      }
      else {
        var file = $scope.bulkUploadFile;
        var name = $scope.upload.name;
        $scope.bulkUploadInProcess = true;
        var bulkUploadUrl = $rootScope.urls.bulkUploadPostUrl;
        BulkUpload.bulkUpload(file, name, bulkUploadUrl).then(function(response) {
          $scope.bulkUploadInProcess = false;
          $log.info('hhh after ' + bulkUploadUrl);
          // Reset form
          $scope.upload.name ='';
          $scope.bulkUploadFile ='';
          $('#upload')[0].reset();
          //notify user

          $scope.uploadStarted = true;
          $scope.uploadStartedMessage=response;
          $timeout(function() {
            $scope.uploadStarted = false;
          }, 3000);

        });
      }
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

    $scope.getBatchReport = function(batchId, $index) {
      $scope.concluded[$index].batchReportLoading = true;
      var bulkUploadListUrl = $rootScope.urls.bulkUploadPostUrl + '/' + batchId;
      BulkUpload.getList(bulkUploadListUrl).then(function(resultList) {
        $log.info('Getting of sites in a batch process batches with  ' + bulkUploadListUrl);
        $scope.concluded[$index].list = resultList.data.entity.sites;
        $scope.concluded[$index].batchReportLoading = false;
      });
      return null;
    };

    $scope.getSiteReport = function(batchId, siteId) {
      $scope.siteReportLoading = true;
      $scope.siteReport='';
      var bulkUploadListUrl = $rootScope.urls.bulkUploadPostUrl + '/' + batchId + '/' + siteId;
      BulkUpload.getList(bulkUploadListUrl).then(function(resultList) {
        $log.info('Getting site report for batch id:' + batchId + 'and siteID: ' + siteId);
        $scope.siteReport = resultList.data.entity;
        $scope.siteReportLoading = false;
      });
      return null;
    };

    $scope.getOngoingList();
  }
]);
