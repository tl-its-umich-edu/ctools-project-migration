'use strict';
/* global angular */

var projectMigrationApp = angular.module('projectMigrationApp', ['ngAnimate']);

projectMigrationApp.run(function($rootScope) {
  //for any init values needed
  $rootScope.server = "http://localhost:8080";
  $rootScope.user = {};
  $rootScope.status = {"projects":"", "migrations":"", "migrated":""}
  $rootScope.stubs = false;
  if($rootScope.stubs){
  	$rootScope.urls = {"projectsUrl":"data/projects.json","projectUrl":"data/project_id.json","migrationsUrl":"data/migrations.json","migratedUrl":"data/migrated.json"}
  }
  else {
  	$rootScope.urls = {"projectsUrl":"data/projects.json","projectUrl":"data/project_id.json","migrationsUrl":$rootScope.server + "/migrations","migratedUrl":$rootScope.server + "/migrated"}
  }
  $rootScope.addProjectSites = [];

});
