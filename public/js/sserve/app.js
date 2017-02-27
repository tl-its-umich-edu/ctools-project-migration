'use strict';
/* global angular */

var projectMigrationApp = angular.module('projectMigrationApp', ['projectMigrationFilters','ngAnimate']);

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
      'migratingUrl': 'data/migrations.json',
      'projectUrl': 'data/project_id.json',
      'checkBoxAuthorizedUrl': '',
      'checkIsAdminUser':'',
      'bulkUploadUrl':'',
      'pingCtools':'data/ping-ctools.json',
      'pingBox':'data/ping-box.json'
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
      'migratingUrl': '/migrating',
      'projectUrl': '/projects/',
      'checkBoxAuthorizedUrl': '/box/checkAuthorized',
      'checkIsAdminUser':'/isAdmin',
      'bulkUploadUrl' : '/bulkUpload',
      'pingCtools':'status/dependencies/ctools',
      'pingBox':'status/dependencies/box'

    };
  }
  $rootScope.addProjectSites = [];

});
