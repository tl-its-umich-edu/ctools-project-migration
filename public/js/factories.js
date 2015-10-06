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
//PROJECTS FACTORY - does the request for the migrations controller
projectMigrationApp.factory('Migrations', function($http) {
  return {
    getMigrations: function(url) {
      return $http.get(url, {
        cache: false
      }).then(
        function success(result) {
          //forward the data - let the controller deal with it
          return categorizeBySite(result);
        },
        function error(result) {
          errorDisplay(url, result.status, 'Unable to get projects');
          result.errors.failure = true;
          return result;
        }
      );
    },
  };
});

//PROJECTS FACTORY - does the request for the migrated controller
projectMigrationApp.factory('Migrated', function($http) {
  return {
    getMigrated: function(url) {
      return $http.get(url, {
        cache: false
      }).then(
        function success(result) {
          //forward the data - let the controller deal with it
          return categorizeBySite(result);
        },
        function error(result) {
          errorDisplay(url, result.status, 'Unable to get projects');
          result.errors.failure = true;
          return result;
        }
      );
    },
  };
});

/**
* GENERIC POLLING SERVICE, used by Migrations panel to poll /migrations
* on page load and on new migration requests
*/

projectMigrationApp.factory('PollingService', ['$http', function($http) {
  var defaultPollingTime = 10000;
  var polls = {};

  return {
    startPolling: function(name, url, pollingTime, callback) {
      if (!polls[name]) {
        var poller = function() {
          $http.get(url, {cache: false}).then(callback);
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
