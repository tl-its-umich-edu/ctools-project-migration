'use strict';
/* global projectMigrationApp, angular, _, moment, $ */

/* TERMS CONTROLLER */
projectMigrationApp.controller('projectMigrationController', ['Projects', 'Migration', 'Migrations', 'Migrated', 'PollingService', '$rootScope', '$scope', '$log', function(Projects, Migration, Migrations, Migrated, PollingService, $rootScope, $scope, $log) {

  $scope.sourceProjects = [];
  $scope.migratingProjects = [];
  $scope.migratedProjects = [];
  
  $scope.loadingProjects = true;
  // GET the project list
  var projectsUrl = $rootScope.urls.projectsUrl;
  $scope.loadingProjects = false;
  Projects.getProjects(projectsUrl).then(function(result) {
    $scope.sourceProjects = result.data;
    $log.info(moment().format('h:mm:ss') + ' - source projects loaded');
    $log.info(' - - - - GET /projects');
  });

  // GET the current migrations list
  var migrationsUrl = $rootScope.urls.migrationsUrl;

  Migrations.getMigrations(migrationsUrl).then(function(result) {
    $scope.migratingProjects = _.sortBy(result.data.entity, 'site_id');
    
    $rootScope.status.migrations = moment().format('h:mm:ss');
    $log.info(moment().format('h:mm:ss') + ' - migrating projects loaded');
    $log.info(' - - - - GET /migrating');
    if (result.data.entity.length) {
      $log.warn('page load got one or more current migrations - will have to poll it every ' + $rootScope.pollInterval/1000 + ' seconds');
      
      PollingService.startPolling('migrationsOnPageLoad', migrationsUrl, $rootScope.pollInterval, function(result) {
        $scope.migratingProjects = _.sortBy(result.data.entity, 'site_id');
        
        if(!angular.equals($scope.migratingProjects, $scope.migratingProjectsShadow)) {
          Migrated.getMigrated(migratedUrl).then(function(result) {
            $scope.migratedProjects = _.sortBy(result.data, 'site_id');
            $rootScope.status.migrated = moment().format('h:mm:ss');
            $log.warn(moment().format('h:mm:ss') + ' - migrating panel changed - migrated projects reloaded');
            $log.info(' - - - - GET /migrated');
          });
        }
        $scope.migratingProjectsShadow = $scope.migratingProjects;    
        if (result.data.length === 0){
          PollingService.stopPolling('migrationsOnPageLoad');
        }
        
        $log.info(moment().format('h:mm:ss') + ' - projects being migrated polled  ON PAGE LOAD');
        $log.info(' - - - - GET /migrations/');
        $rootScope.status.migrations = moment().format('h:mm:ss');
      });      
    }
  });

  // GET the migrations that have completed
  var migratedUrl = $rootScope.urls.migratedUrl;

  Migrated.getMigrated(migratedUrl).then(function(result) {
    $scope.migratedProjects = _.sortBy(result.data, 'site_id');
    $rootScope.status.migrated = moment().format('h:mm:ss');
    $log.info(moment().format('h:mm:ss') + ' - migrated projects loaded');
    $log.info(' - - - - GET /migrated');
  });

  $scope.getTools = function(projectId) {
    // cannot refactor this one as it takes parameters
    var projectUrl = $rootScope.urls.projectUrl + projectId;

    Projects.getProject(projectUrl).then(function(result) {
      var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {
        site_id: projectId
      }));
      //add the tools after the project object
      $scope.sourceProjects.splice.apply($scope.sourceProjects, [targetProjPos + 1, 0].concat(result.data));

      // state management
      $scope.sourceProjects[targetProjPos].stateHasTools = true;

      $log.info(moment().format('h:mm:ss') + ' - tools requested for project ' + $scope.sourceProjects[targetProjPos].entityTitle + ' ( site ID: s' + projectId + ')');
      $log.info(' - - - - GET /projects/' + projectId);
    });
  };
  
  $scope.getBoxFolders = function() {
    // get the box folder info if it has not been gotten yet
    if (!$scope.boxFolders) {
      var boxUrl = 'data/box.json';
      //var boxUrl = '/box/folders';
      Projects.getBoxFolders(boxUrl).then(function(result) {
        $scope.boxFolders = result.data;
        $log.info(moment().format('h:mm:ss') + ' - BOX folder info requested');
        $log.info(' - - - - GET /box/folders');
      });
    }  
  };

  $scope.boxFolderSelect = function(folder) {
    $scope.selectBoxFolder = {'name':folder.name,'id':folder.ID};
    $log.info('BOX Folder "' + $scope.selectBoxFolder.name + '" (ID: ' + $scope.selectBoxFolder.id + ') selected');
  };

  $scope.checkIfSelectionExists = function(projectId) {
    var allTargetProjs = _.where($scope.sourceProjects, {
      site_id: projectId
    });
    var targetSelections = _.where($scope.sourceProjects, {
      site_id: projectId,
      selected:true
    });

    if(targetSelections.length) {
      _.first(allTargetProjs).stateSelectionExists = true;
    }
    else {
      _.first(allTargetProjs).stateSelectionExists = false;
      _.first(allTargetProjs).stateExportConfirm = false;
    }
  };

  $scope.startMigrationConfirm = function(projectId) {
    
    var allTargetProjs = _.where($scope.sourceProjects, {
      site_id: projectId
    });
    
    var targetSelections = _.where($scope.sourceProjects, {
      site_id: projectId,
      selected:true
    });

    // pop confirmation panel
    if(targetSelections.length) {
      _.first(allTargetProjs).stateExportConfirm = true;
    }
    else {
      _.first(allTargetProjs).stateExportConfirm = false;
    }
  };

  $scope.cancelStartMigrationConfirm = function(projectId) {
    var allTargetProjs = _.where($scope.sourceProjects, {
      site_id: projectId
    });
    
    var targetSelections = _.where($scope.sourceProjects, {
      site_id: projectId,
      selected:true
    });

    // state management
    _.first(allTargetProjs).stateExportConfirm = false;
    _.first(allTargetProjs).stateSelectionExists = false;

    // reset all checkboxes
    _.each(targetSelections, function(tool) {
      tool.selected =  false;
    });
  };

  $scope.startMigration = function(projectId, siteName) {
    var targetSelections = _.where($scope.sourceProjects, {
      site_id: projectId,
      selected:true
    });

    $.each(targetSelections, function( key, value ) {
      // Get the base post url
      var migrationUrl = $rootScope.urls.migrationUrl;
      // attach variables to it
      migrationUrl = migrationUrl + '?site_id=' + projectId + '&site_name=' + siteName + '&tool_id=' + value.tool_id + '&tool_name=' + value.tool_name + '&destination_type=' + 'Box';
      $log.info(migrationUrl);


      $log.warn(moment().format('h:mm:ss') + ' - project migration started for ' + migrationUrl);
      Migration.postMigration(migrationUrl).then(function(result) {
        $log.info(' - - - - POST ' + migrationUrl);
        $log.warn(' - - - - after POST we start polling for /migrations every ' + $rootScope.pollInterval/1000 + ' seconds');
      });
    });

    $log.info(' - - - - stop all (if any) /migrations polls');
    PollingService.stopPolling('migrationsAfterPageLoad');
    PollingService.stopPolling('migrationsOnPageLoad');

    PollingService.startPolling('migrationsAfterPageLoad', migrationsUrl, $rootScope.pollInterval, function(result) {
      if (result.data.length === 0) {
        $log.warn('Nothing being migrated, polling /migrations one last time, reloading /migrated and then stopping polling');
        PollingService.stopPolling('migrationsAfterPageLoad');
      }
      $log.info(moment().format('h:mm:ss') + ' - projects being migrated polled after  migration request');
      $log.info(' - - - - GET /migrations/');
      $rootScope.status.migrations = moment().format('h:mm:ss');

      $scope.migratingProjects = _.sortBy(result.data.entity, 'site_id');
      
      if(!angular.equals($scope.migratingProjects, $scope.migratingProjectsShadow)){
        $log.warn(moment().format('h:mm:ss') + ' - migrating panel changed - migrated projects reloaded');
        $log.info(' - - - - GET /migrated');
        Migrated.getMigrated(migratedUrl).then(function(result) {
          $scope.migratedProjects = result.data;
          $rootScope.status.migrated = moment().format('h:mm:ss');
        });
      }
      $scope.migratingProjectsShadow = $scope.migratingProjects;
    });
  };

  $scope.showDetails = function(index){
    $scope.details = index;
  };
}]);
