'use strict';
/* global  projectMigrationApp */

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