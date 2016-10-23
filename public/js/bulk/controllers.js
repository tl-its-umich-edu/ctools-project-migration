'use strict';
/* global projectMigrationApp, validateBulkRequest, $, moment, document*/

projectMigrationApp.controller('projectMigrationBatchController', ['$rootScope', '$scope', '$log', '$q', '$window', '$timeout', 'BulkUpload', 'Projects',

  function($rootScope, $scope, $log, $q, $window, $timeout, BulkUpload, Projects) {
    $scope.boxAuthorized = false;

    // whether the current user authorized app to Box or not
    var checkBoxAuthorizedUrl = $rootScope.urls.checkBoxAuthorizedUrl;
    Projects.checkBoxAuthorized(checkBoxAuthorizedUrl).then(function(result) {
      if (result.data === 'true') {
        $scope.boxAuthorized = true;
      } else {
        $scope.boxAuthorized = false;
      }
      $log.info(' - - - - User authorized to Box: ' + result.data);
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



    $scope.bulkUpload = function() {
      $('.has-error').removeClass('has-error');
      if(!$scope.bulkUploadFile || !$scope.upload || !$scope.uploadSource){
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
        //$scope.siteReport = resultList.data.entity;
        // BOX partial
        // $scope.reportDetails = {"counts":{"successes":293,"errors":2},"type":"box","items":[{"item_id":"Drop Spine Box.pdf","item_Status":"There is already a file with name Drop Spine Box.pdf - file was not added to Box\n"},{"item_id":"OnePageWonders_0_Instructions.pdf","item_Status":"There is already a file with name OnePageWonders_0_Instructions.pdf - file was not added to Box\n"}],"status":"PARTIAL"};
        // BOX successes
        // $scope.reportDetails = {"counts":{"successes":119,"errors":0},"type":"box","items":[],"status":"OK"};
        // GOOGLE global failure
        // $scope.reportDetails =  {"counts":{"successes":0,"partial_successes":0,"errors":0},"details":{"add_members":{"counts":{"successes":0,"partial_successes":0,"errors":0},"items":[],"status":"ERROR"},"message":"Google Groups creation failed for siteId f8345fdf-500f-4c5b-bfcb-e8602cb40d3b with status code 409 and due to Conflict"},"type":"google","items":[],"status":"ERROR"};
        // GOOGLE member failure
        // $scope.reportDetails =  {"counts":{"successes":3,"partial_successes":0,"errors":0},"details":{"add_members":{"counts":{"successes":6,"partial_successes":0,"errors":3},"items":[{"item_id":"slonn@umich.edu MANAGER kitchensink@discussions-dev.its.umich.edu","item_Status":"ERROR","message":"Bad Request"},{"item_id":"kentfitz@umich.edu MANAGER kitchensink@discussions-dev.its.umich.edu","item_Status":"ERROR","message":"Bad Request"},{"item_id":"cousinea@umich.edu MANAGER kitchensink@discussions-dev.its.umich.edu","item_Status":"ERROR","message":"Bad Request"}],"status":"PARTIAL"},"message":"Some site members were not able to be added to the destination Google Group"},"type":"google","items":[],"status":"OK"};
        // GOOGLE OK
        // $scope.reportDetails = {"counts":{"successes":2,"partial_successes":0,"errors":0},"details":{"add_members":{"counts":{"successes":1,"partial_successes":0,"errors":0},"items":[],"status":"OK"},"message":"OK"},"type":"google","items":[],"status":"OK"};
        // GOOGLE ITEM PARTIAL FAILURE
        // $scope.reportDetails = {"counts":{"successes":3,"partial_successes":1,"errors":0},"details":{"add_members":{"counts":{"successes":1,"partial_successes":0,"errors":0},"items":[],"status":"OK"},"message":"OK"},"type":"google","items":[{"item_id":"Wed, 21 Sep 2016 09:43:31 -0400 attachment with 16 mb","item_Status":"PARTIAL","message":"Google Groups message migration successful, but message size exceeded the expected limit. Attachments [Cal_dimention.twbx, Bar-1.twbx] are omitted"}],"status":"PARTIAL"};
        // GOOGLE OK
        // $scope.reportDetails = {"counts":{"successes":4,"partial_successes":0,"errors":0},"details":{"add_members":{"counts":{"successes":3,"partial_successes":0,"errors":0},"items":[],"status":"OK"},"message":"OK"},"type":"google","items":[],"status":"OK"};
        // GOOGLE ITEM ATTACH FAILURE
        // $scope.reportDetails = {"counts":{"successes":2,"partial_successes":2,"errors":0},"details":{"add_members":{"counts":{"successes":1,"partial_successes":0,"errors":0},"items":[],"status":"OK"},"message":"OK"},"type":"google","items":[{"item_id":"Wed, 7 Sep 2016 14:47:36 -0400 ATTACHMENT","item_Status":"PARTIAL","message":"Google Groups message migration successful, but 1/1 attachments [email_msg.txt] failed to be exported and they are missing from message"},{"item_id":"Wed, 7 Sep 2016 14:48:22 -0400 RUN RUNE","item_Status":"PARTIAL","message":"Google Groups message migration successful, but 1/1 attachments [standup.txt] failed to be exported and they are missing from message"}],"status":"PARTIAL"};
        // GOOGLE GROUP CREATE FAILURE
        // $scope.reportDetails = {"counts":{"successes":0,"partial_successes":0,"errors":0},"details":{"add_members":{"counts":{"successes":0,"partial_successes":0,"errors":0},"items":[],"status":"ERROR"},"message":"Google Groups creation failed for siteId 69785b14-a7df-4a28-a0bc-7615ee9b8d10 with status code 667 and due to runPost: IOException for post request :url/body data for: url: [null/groups/mbox-3@discussions-dev.its.umich.edu] body: [{\"name\":\"Mbox_finally\",\"description\":\"DUMMY DESCRIPTION FROM formatDescription\",\"email\":\"mbox-3@discussions-dev.its.umich.edu\"}] cause: org.apache.http.ProtocolException: Target host is not specified message: null"},"type":"google","items":[],"status":"ERROR"};
        // GOOGLE MESSAGE FAILURE
        // $scope.reportDetails = {"counts":{"successes":0,"partial_successes":0,"errors":4},"details":{"add_members":{"counts":{"successes":1,"partial_successes":0,"errors":0},"items":[],"status":"OK"},"message":"OK"},"type":"google","items":[{"item_id":"Wed, 7 Sep 2016 14:48:51 -0400 SUOER","item_Status":"ERROR","message":"Failure to migrate message to Google Groups"},{"item_id":"Wed, 7 Sep 2016 14:47:36 -0400 ATTACHMENT","item_Status":"ERROR","message":"Failure to migrate message to Google Groups due to Gateway Time-out failed with status code 504"},{"item_id":"Wed, 7 Sep 2016 14:46:53 -0400 This is a test","item_Status":"ERROR","message":"Failure to migrate message to Google Groups"},{"item_id":"Wed, 7 Sep 2016 14:48:22 -0400 RUN RUNE","item_Status":"ERROR","message":"Failure to migrate message to Google Groups"}],"status":"ERROR"};
        // GOOGLE MEMBER GROUP FAILURE
        // $scope.reportDetails = {"counts":{"successes":0,"partial_successes":0,"errors":0},"details":{"add_members":{"counts":{"successes":0,"partial_successes":0,"errors":0},"items":[],"status":"ERROR"},"message":"Mail migration to Google Groups for the site 22b5d237-0a22-4995-a4b1-d5022dd90a86 failed, couldn't get site members from ctools"},"type":"google","items":[],"status":"ERROR"};

        $scope.siteReportLoading = false;
      });
      return null;
    };

    $scope.getOngoingList();
  }
]);
