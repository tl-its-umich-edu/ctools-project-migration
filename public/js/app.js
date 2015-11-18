'use strict';
/* global angular */

var projectMigrationApp = angular.module('projectMigrationApp', ['ngAnimate']);

projectMigrationApp.run(function($rootScope) {
  //for any init values needed
  $rootScope.server = '';
  $rootScope.user = {};
  $rootScope.pollInterval = 15000;
  $rootScope.status = {
    'projects': '',
    'migrations': '',
    'migrated': ''
  };
  $rootScope.stubs = false;
  if ($rootScope.stubs) {
    $rootScope.urls = {
      'projectsUrl': 'data/projects.json',
      'migrationsUrl': 'data/migrations.json',
      'migrationUrl': '',
      'migrationZipUrl': '',
      'migrationBoxUrl': '',
      'migratedUrl': 'data/migrated.json',
      'projectUrl': 'data/project_id.json'
    };
  } else {
    $rootScope.urls = {
      // TODO: store "ctools-project-migration" as configuration variable
      'projectsUrl': '/projects',
      'migrationsUrl': '/migrations',
      'migrationUrl': '/migration',
      'migrationZipUrl': '/migrationZip',
      'migrationBoxUrl': '/migrationBox',
      'migratedUrl': '/migrated',
      'projectUrl': '/projects/'
    };
  }
  $rootScope.addProjectSites = [];

});
