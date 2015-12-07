'use strict';
/* global projectMigrationApp, errorDisplay */

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
				// filter out everything except project sites
				var sourceProjects = _.where(result.data.site_collection, {
					type : 'project'
				});
				result.data.site_collection = sourceProjects
				// use a transform to make project data mirror data in
				// migrations and migrated
				return transformProjects(result);
			}, function error(result) {
				errorDisplay(url, result.status, 'Unable to get projects');
				result.errors.failure = true;
				return result;
			});
		},
		getProject : function(url) {
			return $http.get(url, {
				cache : false
			}).then(function success(result) {
				// forward the data - let the controller deal with it
				return transformProject(result);
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
				// forward the data - let the controller deal with it
				// console.log()
				return result;
			}, function error(result) {
				errorDisplay(url, result.status, 'Unable to get projects');
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
				// forward the data - let the controller deal with it
				return result;
			}, function error(result) {
				errorDisplay(url, result.status, 'Unable to get projects');
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
