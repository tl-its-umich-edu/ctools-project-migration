'use strict';
/* global projectMigrationApp, angular, _, moment */

/* TERMS CONTROLLER */
projectMigrationApp.controller('projectMigrationController', ['Projects', 'Migration', 'Migrations', 'Migrated', 'PollingService', '$rootScope', '$scope', '$log', function(Projects, Migration, Migrations, Migrated, PollingService, $rootScope, $scope, $log) {

  $scope.sourceProjects = [];
  $scope.migratingProjects = [];
  $scope.migratedProjects = [];
  
  // GET the project list
  var projectsUrl = $rootScope.urls.projectsUrl;
  
  Projects.getProjects(projectsUrl).then(function(result) {
    $scope.sourceProjects = _.where(result.data.site_collection, {
      type: 'project'
    });
    $log.info(moment().format('h:mm:ss') + ' - source projects loaded');
    $log.info(' - - - - GET /projects');
  });

  // GET the current migrations list
  var migrationsUrl = $rootScope.urls.migrationsUrl;

  Migrations.getMigrations(migrationsUrl).then(function(result) {
    $scope.migratingProjects = result.data;
    $scope.migratingProjectsShadow = result.data;    
    $rootScope.status.migrations = moment().format('h:mm:ss');
    $log.info(moment().format('h:mm:ss') + ' - migrating projects loaded');
    $log.info(' - - - - GET /migrating');
    if (result.data.length) {
      $log.warn('page load got one or more current migrations - will have to poll it every ' + $rootScope.pollInterval/1000 + ' seconds');
      
      PollingService.startPolling('migrationsOnPageLoad', migrationsUrl, $rootScope.pollInterval, function(result) {
        $scope.migratingProjects = result.data;
        
        if(!angular.equals($scope.migratingProjects, $scope.migratingProjectsShadow)) {
          Migrated.getMigrated(migratedUrl).then(function(result) {
            $scope.migratedProjects = result.data;
            $rootScope.status.migrated = moment().format('h:mm:ss');
            $log.warn(moment().format('h:mm:ss') + ' - migrating panel changed - migrated projects reloaded');
            $log.info(' - - - - GET /migrated');
          });
        }
        $scope.migratingProjectsShadow = result.data;
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
    $scope.migratedProjects = result.data;
    $rootScope.status.migrated = moment().format('h:mm:ss');
    $log.info(moment().format('h:mm:ss') + ' - migrated projects loaded');
    $log.info(' - - - - GET /migrated');
  });

  $scope.getTools = function(projectId) {
    // cannot refactor this one as it takes parameters
    var projectUrl = $rootScope.urls.projectUrl + projectId;

    Projects.getProject(projectUrl).then(function(result) {
      var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {
        entityId: projectId
      }));
      $scope.sourceProjects[targetProjPos].tools = result.data;

      // state management
      $scope.sourceProjects[targetProjPos].stateHasTools = true;

      $log.info(moment().format('h:mm:ss') + ' - tools requested for project ' + $scope.sourceProjects[targetProjPos].entityTitle + ' ( site ID: s' + projectId + ')');
      $log.info(' - - - - GET /projects/' + projectId);
    });
  };
  
  $scope.getBoxFolders = function(projectId) {
    // get the box folder info if it has not been gotten yet
    if (!$scope.boxFolders) {
      var boxUrl = 'data/box.json';
      //var boxUrl = '/box/folders';
      Projects.getBoxFolders(boxUrl).then(function(result) {
        $scope.boxFolders = result.data;
        $log.info(moment().format('h:mm:ss') + ' - box folder info requested')
        $log.info(' - - - - GET /box/folders');
      });
    }  
  };

  $scope.boxFolderSelect = function(folder) {
    $scope.selectBoxFolder = {'name':folder.name,'id':folder.ID};
    $log.info($scope.selectBoxFolder);
  }  


  $scope.checkIfSelectionExists = function(projectId) {
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {
      entityId: projectId
    }));
    $scope.sourceProjects[targetProjPos].selectionExists = false;
    _.each($scope.sourceProjects[targetProjPos].tools, function(tool) {
      if (tool.selected) {
        // state management
        $scope.sourceProjects[targetProjPos].stateSelectionExists = true;
      }
    });
  };

  $scope.startMigrationConfirm = function(projectId) {
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {
      entityId: projectId
    }));
    // pop confirmation panel
    // state management
    $scope.sourceProjects[targetProjPos].stateExportConfirm = true;
  };

  $scope.cancelStartMigrationConfirm = function(projectId) {
    // close confirmation panel and reset things
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {
      entityId: projectId
    }));
    // reset all checkboxes
    _.each($scope.sourceProjects[targetProjPos].tools, function(tool) {
      tool.selected = false;
    });
    // state management
    $scope.sourceProjects[targetProjPos].stateExportConfirm = false;
    $scope.sourceProjects[targetProjPos].stateSelectionExists = false;
  };

  $scope.startMigration = function(siteId, siteName, toolId, toolName, destinationType) {

    
	// Get the base post url
	var migrationUrl = $rootScope.urls.migrationUrl;
	// attach variables to it
	migrationUrl = migrationUrl + "?site_id=" + siteId + "&site_name=" + siteName + "&tool_id=" + toolId + "&tool_name=" + toolName + "&destination_type=" + destinationType;
	$log.warn(moment().format('h:mm:ss') + ' - project migration started for ' + migrationUrl);
	Migration.postMigration(migrationUrl).then(function(result) {
        $log.info(' - - - - POST ' + migrationUrl);
        $log.info('result ' + result.data)
        $log.warn(' - - - - after POST we start polling for /migrations every ' + $rootScope.pollInterval/1000 + ' seconds');
        $log.info(' - - - - stop all (if any) /migrations polls');
        PollingService.stopPolling('migrationsAfterPageLoad');
        PollingService.stopPolling('migrationsOnPageLoad');
      });

    // TODO: need factory
    // 2. adjust UI for this this project
    // state management
	var migrationsUrl = $rootScope.urls.migrationsUrl;
	var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {
		entityId: siteId
	}));
    $scope.sourceProjects[targetProjPos].stateMigrating = {
      status: 'Migration in progress',
      dateStarted: moment().format('MM/D/YY, h:mm:ss a')
    };

    // 3. POST above will return a migration ID - store this so that when it the
	// related projectId dissapears from the
      // current migrations list the UI for the projects and the current
		// migrations lists can be updated
    // 4. poll /migrations - this would be in a timer
    
    PollingService.startPolling('migrationsAfterPageLoad', migrationsUrl, $rootScope.pollInterval, function(result) {
      if (result.data.length === 0) {
        $log.warn('Nothing being migrated, polling /migrations one last time, reloading /migrated and then stopping polling');
        PollingService.stopPolling('migrationsAfterPageLoad');
      }
      $log.info(moment().format('h:mm:ss') + ' - projects being migrated polled after request for: ' + siteId);
      $log.info(' - - - - GET /migrations/');
      $rootScope.status.migrations = moment().format('h:mm:ss');

      $scope.migratingProjects = result.data;
      
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
}]);
