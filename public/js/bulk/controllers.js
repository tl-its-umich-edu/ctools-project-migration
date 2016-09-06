'use strict';
/* global projectMigrationApp, validateBulkRequest, $*/

projectMigrationApp.controller('projectMigrationBatchController', ['$rootScope', '$scope', '$log', '$q', '$window', '$timeout', 'BulkUpload',
  function($rootScope, $scope, $log, $q, $window, $timeout, BulkUpload) {

    $scope.bulkUpload = function() {
      $('.has-error').removeClass('has-error');
      if(!$scope.bulkUploadFile || !$scope.upload.name || !$scope.uploadSource){
        if(!$scope.bulkUploadFile) {
          $('.bulkUploadFile').addClass('has-error');
        }
        if(!$scope.upload){
          $('.bulkUploadName').addClass('has-error');
        }
        if(!$scope.uploadSource){
          $('.uploadSource').addClass('has-error');
        }
      }
      else {
          var file = $scope.bulkUploadFile;
          var name = $scope.upload.name;
          var source = $scope.uploadSource;
          $scope.bulkUploadInProcess = true;
          var bulkUploadUrl = $rootScope.urls.bulkUploadPostUrl;
          $log.info('POST: ' + bulkUploadUrl + ' called: ' + name + ' Source: ' + source);
          $log.info(file, name, bulkUploadUrl, source);
          BulkUpload.bulkUpload(file, name, bulkUploadUrl, source).then(function(response) {
            $scope.bulkUploadInProcess = false;
            // Reset form
            $scope.upload.name ='';
            $scope.bulkUploadFile ='';
            $('#upload')[0].reset();
            $timeout(function() {
              $scope.uploadStarted = false;
              $('a[href="#ongoing"]').trigger('click');
              $scope.getOngoingList();
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
        $scope.ongoing[$index].list = resultList.data.entity.sites;
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
