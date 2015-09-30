'use strict';
/* global  projectMigrationApp, errorDisplay */

//PROJECTS FACTORY - does the request for the projects controller
projectMigrationApp.factory('Projects', function($http) {
  return {
    getProjects: function(url) {
      return $http.get(url, {
        cache: false
      }).then(
        function success(result) {
          //forward the data - let the controller deal with it
          return result;
        },
        function error(result) {
          errorDisplay(url, result.status, 'Unable to get projects');
          result.errors.failure = true;
          return result;
        }
      );
    },
    getProject: function(url) {
      return $http.get(url, {
        cache: false
      }).then(
        function success(result) {
          //forward the data - let the controller deal with it
          return result;
        },
        function error(result) {
          errorDisplay(url, result.status, 'Unable to get projects');
          result.errors.failure = true;
          return result;
        }
      );
    }

  };
});

projectMigrationApp.factory('PollingService', ['$http', function($http) {
  var defaultPollingTime = 10000;
  var polls = {};

  return {
    startPolling: function(name, url, pollingTime, callback) {
      // Check to make sure poller doesn't already exist
      if (!polls[name]) {
        var poller = function() {
          $http.get(url).then(callback);
        };
        poller();
        polls[name] = setInterval(poller, pollingTime || defaultPollingTime);
      }
    },
    stopPolling: function(name) {
      clearInterval(polls[name]);
      delete polls[name];
    }
  };
}]);
