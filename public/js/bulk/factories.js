'use strict';
/* global projectMigrationApp, angular, errorDisplay, errorDisplayBulk */

//Bulk upload FACTORY - upload to bulk process
projectMigrationApp.factory('BulkUpload', function($http, $log) {
  return {
    bulkUpload: function(file, name, uploadUrl, source) {
      $log.info("uploadUrl" + uploadUrl);
      var fd = new FormData();
      fd.append('file', file);
      fd.append('name', name);
      fd.append('source', source);
      $log.info('upload name=' + name);
      return $http.post(uploadUrl, fd, {
        transformRequest: angular.identity,
        headers: { 'Content-Type': undefined }
      }).then(
        function success(result) {
          // forward the data - let the controller deal with it
          return result;
        },
        function error(result) {
          errorDisplay(result.status,'Unable to post new migration');
          result.errors.failure = true;
          result.data.custom_message = 'Unable to post new batch migration';
          errorDisplayBulk(result);
          result.errors = true;
          return result;
        });
    },
    getList : function(url) {
      return $http.get(url, {
        cache : false
      }).then(function success(result) {
        // endpoint will return a 200, but the payload may be an error message with a status flag
        if(result.data.status ===200){
          return result;
        } else {
          result.status = result.data.status;
          return result;
        }
      }, function error(result) {
        errorDisplay(url, result.status, 'Unable to get current migrations');
        result.errors.failure = true;
        return result;
      });
    }
  };
});

projectMigrationApp.factory('Projects', function($http) {
	return {
		boxAuthorize: function(url) {
			return $http.get(url, {
				cache : false
			}).then(
					function success(result) {
						// forward the data - let the controller deal with it
						return result;
					},
					function error(result) {
						errorDisplay(url, result.status,
								'Unable to authorize into Box');
						result.errors.failure = true;
						return result;
					});
		},
		boxUnauthorize : function(url) {
			return $http.get(url, {
				cache : false
			}).then(
					function success(result) {
						// forward the data - let the controller deal with it
						return result;
					},
					function error(result) {
						errorDisplay(url, result.status,
								'Unable to unauthorize user from Box.');
						result.errors.failure = true;
						return result;
					});
		},
		checkBoxAuthorized : function(url) {
			return $http.get(url, {
				cache : false
			}).then(
					function success(result) {
						// forward the data - let the controller deal with it
						return result;
					},
					function error(result) {
						errorDisplay(url, result.status,
								'Unable to check user authentication info with Box.');
						result.errors.failure = true;
						return result;
					});
		},
    pingDependency : function(url) {
      return $http.get(url, {
        cache : false
      }).then(
          function success(result) {
            // forward the data - let the controller deal with it
            return result;
          },
          function error(result) {
            errorDisplay(url, result.status,
                'Unable to check ' + url);
            return result;
          });
    },
    checkIsAdminUser : function(url) {
      return $http.get(url, {
        cache : false
      }).then(
          function success(result) {
            // forward the data - let the controller deal with it
            return result;
          },
          function error(result) {
            errorDisplay(url, result.status,
                'Unable to check if user is an admin.');
            return result;
          });
    }


	};
});
