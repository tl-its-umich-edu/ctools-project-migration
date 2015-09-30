'use strict';
/* global angular */

var projectMigrationApp = angular.module('projectMigrationApp', ['ngAnimate']);

projectMigrationApp.run(function($rootScope) {
  //for any init values needed
  $rootScope.server = '';
  $rootScope.user = {};
  $rootScope.status = {"projects":"", "migrations":"", "migrated":""}
  $rootScope.stubs = true;
  if($rootScope.stubs){
  	$rootScope.urls = {"projectsUrl":"data/projects.json","migrationsUrl":"data/migrations.json","migratedUrl":"data/migrated.json"}
  }
  else {
  	$rootScope.urls = {"projectsUrl":"/projects.json","migrationsUrl":"/migrations","migratedUrl":"/migrated"}
  }
  $rootScope.addProjectSites = [];

});
