'use strict';
/* global projectMigrationApp, angular, errorDisplay */

//Bulk upload FACTORY - upload to bulk process
projectMigrationApp.factory('BulkUpload', function($http, $log) {
  return {
    bulkUpload: function(file, uploadUrl) {
      $log.info("uploadUrl" + uploadUrl);
      var fd = new FormData();
      fd.append('file', file);
      return $http.post(uploadUrl, fd, {
        transformRequest: angular.identity,
        headers: { 'Content-Type': undefined }
      }).then(
        function success(result) {
          // forward the data - let the controller deal with it
          return result;
        },
        function error(result) {
          errorDisplay(result.status,
            'Unable to post new migration');
          result.errors.failure = true;
          return result;
        });
    }
  };
});
