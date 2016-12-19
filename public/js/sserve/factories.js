'use strict';
/* global projectMigrationApp, errorDisplay */

projectMigrationApp.factory('ProjectsLite', function($q, $timeout, $window, $http) {
	return {
		postDeleteSiteRequest : function(url) {
			return $http.post(url, {
				cache : false
			}).then(function success(result) {
				return result;
			}, function error(result) {
				errorDisplay(url, result.status, 'Unable to request that a site be deleted.');
				result.errors.failure = true;
				return result;
			});
		},
    doNotMigrateTool : function(url) {
			return $http.post(url, {
				cache : false
			}).then(function success(result) {
				return result;
			}, function error(result) {
				errorDisplay(url, result.status, 'Unable to request that a tool not be migrated.');
				result.errors.failure = true;
				return result;
			});
		},
    isSiteToBeDeleted : function(url) {
      return $http.get(url, {
        cache : false
      }).then(function success(result) {
        // filter everything course sites
        // returned presorted by site type (by code - mwsp=1, gt=2, p=3) and then alphanum
        return result;
      }, function error(result) {
        errorDisplay(url, result.status, 'Unable to query if a site has a delete request associated with it. ');
        result.errors.failure = true;
        return result;
      });
    },
    siteToolNotMigrate : function(url) {
      return $http.get(url, {
        cache : false
      }).then(function success(result) {
        // filter everything course sites
        // returned presorted by site type (by code - mwsp=1, gt=2, p=3) and then alphanum
        return result;
      }, function error(result) {
        errorDisplay(url, result.status, 'Unable to query if a tool has a do not migrate request associated with it.');
        result.errors.failure = true;
        return result;
      });
    },
    unFlagSiteDeletion: function(url){
      return $http.post(url, {
        cache : false
      }).then(function success(result) {
        return result;
      }, function error(result) {
        errorDisplay(url, result.status, 'Unable to remove a flag that a site be deleted.');
        result.errors.failure = true;
        return result;
      });
    },
    unFlagDoNotMigrate: function(url){
      return $http.post(url, {
        cache : false
      }).then(function success(result) {
        return result;
      }, function error(result) {
        errorDisplay(url, result.status, 'Unable to remove a flag that a tool not be migrated.');
        result.errors.failure = true;
        return result;
      });
    },
    startMigrationEmail: function(url) {
      var defer = $q.defer();

			$timeout(function() {
				$window.location = url;
			}, 1000).then(function() {
				defer.resolve('success');
			}, function() {
				defer.reject('error');
			});
			return defer.promise;
		//}


      // return $http.post(url, {
      //   cache : false
      // }).then(function success(result) {
      //   return result;
      // }, function error(result) {
      //   errorDisplay(url, result.status, 'Unable to download an email archive');
      //   result.errors.failure = true;
      //   return result;
      // });
    }
  };
});



/*
 * PROJECTS FACTORY - does the requests for the projects controller getProjects:
 * gets the projects the user has a specific role in getProject: for a given
 * project, get the tools getBoxFolders: get the list of Box folders in user's
 * account so the user can pick one
 */
projectMigrationApp.factory('Projects', function($http) {
	return {
		getProjects : function(url) {
			return $http.get(url, {
				cache : false
			}).then(function success(result) {
				// filter everything course sites

				var sourceProjects = result.data.site_collection;
				// use a transform to make project data mirror data in
				// migrations and migrated
				var siteList = transformProjects(sourceProjects);
				// returned presorted by site type (by code - mwsp=1, gt=2, p=3) and then alphanum
				return _.chain(siteList).sortBy('title').sortBy('type_code').value();
			}, function error(result) {
				errorDisplay(url, result.status, 'Unable to get projects');
				result.errors.failure = true;
				return result;
			});
		},
		getProject : function(url, deleteStatus) {
			return $http.get(url, {
				cache : false
			}).then(function success(result) {
				// forward the data - let the controller deal with it
				return transformProject(result, deleteStatus);
			}, function error(result) {
				errorDisplay(url, result.status, 'Unable to get project');
				result.errors.failure = true;
				return result;
			});
		},
		getBoxFolders : function(url) {
			return $http.get(url, {
				cache : false
			}).then(
					function success(result) {
						// forward the data - let the controller deal with it
						return result;
					},
					function error(result) {
						errorDisplay(url, result.status,
								'Unable to get box folder info');
						result.errors.failure = true;
						return result;
					});
		},
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

/*
 * PROJECTS FACTORY - does the request for the migration controller user has
 * asked that a tool/tools be migrated
 */
projectMigrationApp.factory('Migration', function($q, $timeout, $window, $http) {
	return {
		getMigrationZip : function(url) {
			var defer = $q.defer();

			$timeout(function() {
				$window.location = url;
			}, 1000).then(function() {
				defer.resolve('success');
			}, function() {
				defer.reject('error');
			});
			return defer.promise;
		},
		postMigrationBox : function(url) {
			return $http.post(url, {
				cache : false
			}).then(
					function success(result) {
						// forward the data - let the controller deal with it
						return result;
					},
					function error(result) {
						errorDisplay(url, result.status,
								'Unable to post new migration');
						result.errors.failure = true;
						return result;
					});
		}
	};
});

// PROJECTS FACTORY - does the request for the migrations controller
projectMigrationApp.factory('Migrations', function($http) {
	return {
		getMigrations : function(url) {
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
		},
	};
});

// PROJECTS FACTORY - does the request for the migrated controller
projectMigrationApp.factory('Migrated', function($http) {
	return {
		getMigrated : function(url) {
			return $http.get(url, {
				cache : false
			}).then(function success(result) {
				// endpoint will return a 200, but the payload may be an error message with a status flag
				if(result.data.status ===200){
					return transformMigrated(result);
				}
				else {
					result.status = result.data.status;
					return result;
				}
			}, function error(result) {
				errorDisplay(url, result.status, 'Unable to get migrated projects');
				result.errors.failure = true;
				return result;
			});
		},
	};
});

/**
 * GENERIC POLLING SERVICE, used by Migrations panel to poll /migrations on page
 * load and on new migration requests
 */

projectMigrationApp.factory('PollingService', [
		'$http',
		function($http) {
			var defaultPollingTime = 10000;
			var polls = {};

			return {
				startPolling : function(name, url, pollingTime, callback) {
					if (!polls[name]) {
						var poller = function() {
							$http.get(url, {
								cache : false
							}).then(callback);
						};
						poller();
						polls[name] = setInterval(poller, pollingTime
								|| defaultPollingTime);
					}
				},
				stopPolling : function(name) {
					clearInterval(polls[name]);
					delete polls[name];
				}
			};
		} ]);


// PROJECTS FACTORY - does the request for the migrated controller
projectMigrationApp.factory('Status', function($http) {
	return {
		getStatus : function(url) {
			return $http.get(url, {
				cache : false
			}).then(function success(result) {
				// endpoint will return a 200, but the payload may be an error message with a status flag
				if(result.data.status ===200){
					return result;
				}
				else {
					result.status = result.data.status;
					return result;
				}
			}, function error(result) {

				result.errors.failure = true;
				return result;
			});
		},
	};
});

projectMigrationApp.factory('focus', function($timeout, $window) {
  return function(id) {
    $timeout(function() {
      var element = $window.document.getElementById(id);
      if(element)
        element.focus();
    });
  };
});
