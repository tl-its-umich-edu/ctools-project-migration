'use strict';
/* global angular */

var projectMigrationApp = angular.module('projectMigrationApp', ['projectMigrationFilters']);

projectMigrationApp.run(function($rootScope) {
  //for any init values needed
  $rootScope.server = '';
  $rootScope.user = {};
  $rootScope.pollInterval = 15000;
  $rootScope.stubs = false ;
  if ($rootScope.stubs) {
    $rootScope.urls = {
      'isAdminCheckUrl': 'data/isAdmin.json',
      'bulkUploadPostUrl': 'data/bulkUpload', // not needed
      'listBulkUploadAllUrl': 'data/bulkUploadAll.json',
      'listBulkUploadOngoingUrl': 'data/bulkUploadOngoing.json',
      'listBulkUploadConcludedUrl': 'data/bulkUploadConcluded.json',
      // only needed to mock gettin a list and a site
      'bulkUploadList': '/bulkUploadList.json',
      'bulkUploadSite': '/bulkUploadSite.json',

    };
  } else {
    $rootScope.urls = {
      'isAdminCheckUrl': '/isAdmin',
      'bulkUploadPostUrl': '/bulkUpload',
      'listBulkUploadAllUrl': '/bulkUpload/all',
      'listBulkUploadOngoingUrl': '/bulkUpload/ongoing',
      'listBulkUploadConcludedUrl': '/bulkUpload/concluded',
    };
  }
});
