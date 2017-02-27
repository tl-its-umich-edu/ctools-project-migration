'use strict';
/* global projectMigrationApp, validateBulkRequest, $, moment, document*/

projectMigrationApp.controller('projectMigrationBatchController', ['$rootScope', '$scope', '$log', '$q', '$window', '$timeout', 'BulkUpload', 'Projects',
  function($rootScope, $scope, $log, $q, $window, $timeout, BulkUpload, Projects) {
    $scope.boxAuthorized = false;
    // set default to resources and hide the radio group
    $scope.uploadSource = 'resources';

    var pingCToolsUrl =  $rootScope.urls.pingCtools;
    Projects.pingDependency(pingCToolsUrl).then(function(result) {
      if(result.data.status ==='DOWN'){
        $scope.ctoolsDown = true;
      }
    });
    var pingBoxUrl = $rootScope.urls.pingBox;
    Projects.pingDependency(pingBoxUrl).then(function(result) {
      if(result.data.status ==='DOWN'){
        $scope.boxDown = true;
      }
    });

    // whether the current user is a member of the admin group or n0t
    var checkIsAdminUserUrl = $rootScope.urls.isAdminCheckUrl;
    $log.info("admin url "  + checkIsAdminUserUrl );
    Projects.checkIsAdminUser(checkIsAdminUserUrl).then(function(result) {
      $timeout(function() {
        if (result.data.isAdmin) {
          $rootScope.isAdminUser = true;
        } else {
          $rootScope.isAdminUser = false;
        }
        $log.info(' - - - - User is admin: ' + result.data.isAdmin + ' and value is ' + (result.data.isAdmin === true));

        if($rootScope.isAdminUser ===true){
          // whether the current user authorized app to Box or not
          var checkBoxAuthorizedUrl = $rootScope.urls.checkBoxAuthorizedUrl;
          Projects.checkBoxAuthorized(checkBoxAuthorizedUrl).then(function(result) {
            if (result.data === 'true') {
              $scope.boxAuthorized = true;
            } else {
              $scope.boxAuthorized = false;
            }
            $log.info(' - - - - User authorized to Box: ' + result.data);
            $scope.getOngoingList();
          });
        }
      });
    });
      // handler for a request for user Box account authentication/authorization
      $scope.boxAuthorize = function() {
        $log.info('---- in boxAuthorize ');
        // get the box folder info if it has not been
        // gotten yet
        if (!$scope.boxAuthorized) {
          $log.info(' - - - - GET /box/authorize');
          var boxUrl = '/box/authorize';
          Projects.boxAuthorize(boxUrl).then(function(result) {
            $scope.boxAuthorizeHtml = result.data;
            $log.info(moment().format('h:mm:ss') + ' - BOX folder info requested');
            $log.info(' - - - - GET /box/authorize');
          });
        }
      };

    // on dismiss box auth modal, launch a function to check if box auth
    $(document).on('hidden.bs.modal','#boxAuthModal',function() {
      $('body').removeClass('modal-open');
      $('.modal-backdrop').remove();
      var checkBoxAuthorizedUrl = $rootScope.urls.checkBoxAuthorizedUrl;
      Projects.checkBoxAuthorized(checkBoxAuthorizedUrl).then(function(result) {
        if (result.data === 'true') {
          $scope.boxAuthorized = true;
        } else {
          $scope.boxAuthorized = false;
        }
        // $scope.boxAuthorized ===
        // result.data;
        $log.info(' - - - - User authorized to Box: ' + result.data);
      });
    });


    // remove user authentication information from server memory, user need to re-authenticate in the future to access their box account
    $('#boxUnauthorize').click(function() {
      var boxUrl = '/box/unauthorize';
      Projects.boxUnauthorize(boxUrl).then(function(result) {
        $scope.boxAuthorized = false;
        $('#boxIFrame').remove();
        $('#boxIFrameContainer').append('<iframe class="boxIFrame" id="boxIFrame" src="/box/authorize" frameborder="0"></iframe>');
        // current user un-authorize
        // the app from accessing
        // Box
        $log.info(moment().format('h:mm:ss') + ' - unauthorize from Box account requested');
        $log.info(' - - - - GET /box/unauthorize');
      });
    });

    $scope.resetBulkUpload = function(){
      $scope.bulkUploadFile = undefined;
      $scope.upload=undefined;
    };

    $scope.bulkUpload = function() {
      $('.has-error').removeClass('has-error');
      if(!$scope.bulkUploadFile || !$scope.upload || !$scope.uploadSource || !$scope.upload.name || $scope.upload.name===''){
        if(!$scope.upload || $scope.upload.name ===''){
          $('.bulkUploadName').addClass('has-error');
        }
        if(!$scope.bulkUploadFile) {
          $('.bulkUploadFile').addClass('has-error');
        }
        if(!$scope.uploadSource){
          $('.uploadSource').addClass('has-error');
        }
      }
      else {
        var checkBoxAuthorizedUrl = $rootScope.urls.checkBoxAuthorizedUrl;
        Projects.checkBoxAuthorized(checkBoxAuthorizedUrl).then(function(result) {
          if (result.data === 'true') {
            $scope.boxAuthorized = true;
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
          } else {
            $scope.boxAuthorized = false;
            alert('Sorry. Your Box session has timed out - please authorize again');
          }
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
        $scope.concluded =transformConcluded(resultConcluded.data.entity);
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

    $scope.getBatchReport = function(batchId) {
      var targetBatchPos = $scope.concluded.indexOf(_.findWhere($scope.concluded, {id: batchId}));

      $scope.concluded[targetBatchPos].batchReportLoading = true;
      var bulkUploadListUrl = $rootScope.urls.bulkUploadPostUrl + '/' + batchId;
      BulkUpload.getList(bulkUploadListUrl).then(function(resultList) {
        $log.info('Getting of sites in a batch process batches with  ' + bulkUploadListUrl);
        if(resultList.status ===200){
          $scope.concluded[targetBatchPos].list = resultList.data.entity.sites;
          $scope.concluded[targetBatchPos].batchReportLoading = false;
        } else {
          alert(resultList.data.statusType + '\n\n' + resultList.data.entity);
        }
      });
      return null;
    };

    $scope.getSiteReport = function(batchId, siteId) {
      $scope.siteReportLoading = true;
      $scope.siteReport='';
      var bulkUploadListUrl = $rootScope.urls.bulkUploadPostUrl + '/' + batchId + '/' + siteId;
      BulkUpload.getList(bulkUploadListUrl).then(function(resultList) {
        $log.info('Getting site report for batch id:' + batchId + 'and siteID: ' + siteId);
        $scope.reportDetails = resultList.data.entity.status;
        $scope.siteReportLoading = false;
      });
      return null;
    };
  }
]);
