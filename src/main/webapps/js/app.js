'use strict';
/* global angular */

var projectMigrationApp = angular.module('projectMigrationApp', ['ngAnimate']);

projectMigrationApp.run(function ($rootScope) {
	//for any init values needed
	$rootScope.server = '';
	$rootScope.user = {};
	$rootScope.addProjectSites = [];
});

