'use strict';
/* global projectMigrationApp, angular, _, moment, $ */

/* TERMS CONTROLLER */
projectMigrationApp.controller('projectMigrationController', ['Projects', 'Migration', 'Migrations', 'Migrated', 'PollingService', '$rootScope', '$scope', '$log', '$q', '$timeout', '$window', '$http', function(Projects, Migration, Migrations, Migrated, PollingService, $rootScope, $scope, $log, $q, $timeout, $window, $http) {

  $scope.sourceProjects = [];
  $scope.migratingProjects = [];
  $scope.migratedProjects = [];

  $scope.loadingProjects = true;
  // GET the project list
  var projectsUrl = $rootScope.urls.projectsUrl;
  $scope.loadingProjects = false;
  Projects.getProjects(projectsUrl).then(function(result) {
    $scope.sourceProjects = result.data;
    //compare projects list with migrations list, lock the project if it has 
    //a current migration
    _.each($scope.sourceProjects, function(project) {
      if(_.findWhere($scope.migratingProjects, {site_id:project.site_id})) {
        project.migrating = true;
      }
    });
        
    $log.info(moment().format('h:mm:ss') + ' - source projects loaded');
    $log.info(' - - - - GET /projects');
  });

  // GET the current migrations list
  var migratingUrl = $rootScope.urls.migratingUrl;

  Migrations.getMigrations(migratingUrl).then(function(result) {

    if (result.status ===200) {
      $scope.migratingProjects = _.sortBy(result.data.entity, 'site_id');

      $rootScope.status.migrations = moment().format('h:mm:ss');
      $log.info(moment().format('h:mm:ss') + ' - migrating projects loaded');
      $log.info(' - - - - GET /migrating');
      
      if (result.data.entity.length && result.status ===200) {
        $log.warn('page load got one or more current migrations - will have to poll it every ' + $rootScope.pollInterval/1000 + ' seconds');
        
        PollingService.startPolling('migrationsOnPageLoad', migratingUrl, $rootScope.pollInterval, function(result) {
          
          $scope.migratingProjects = _.sortBy(transformMigrations(result).data.entity, 'site_id');
          _.each($scope.sourceProjects, function(project) {
            if(project.migrating) {
              if(_.findWhere($scope.migratingProjects, {site_id:project.site_id})) {
                project.migrating = true;
              } else {
                project.migrating = false;
                project.stateSelectionExists = false;
                project.stateExportConfirm = false;
                project.selected = false;
                project.stateHasTools = false;
                project.hideTools = true;
              }
            }
          });

          if(!angular.equals($scope.migratingProjects, $scope.migratingProjectsShadow)) {
            Migrated.getMigrated(migratedUrl).then(function(result) {
              if (result.status ===200) {
                $scope.migratedProjects = _.sortBy(result.data.entity, 'site_id');
                $rootScope.status.migrated = moment().format('h:mm:ss');
                $log.warn(moment().format('h:mm:ss') + ' - migrating panel changed - migrated projects reloaded');
                $log.info(' - - - - GET /migrated');
              } else {
                $log.warn('Got error on /migrated');
                $scope.migratedProjectsError = true;
              }
            });
          }
          $scope.migratingProjectsShadow = $scope.migratingProjects;

          if (result.data.entity.length === 0){
            $log.info('No more migrations on PAGE LOAD - stopping ON PAGE LOAD polling');
            PollingService.stopPolling('migrationsOnPageLoad');
          } else {
            $log.info(moment().format('h:mm:ss') + ' - projects being migrated polled  ON PAGE LOAD');
            $log.info(' - - - - GET /migrations/');
          }
          $rootScope.status.migrations = moment().format('h:mm:ss');
        });      
      }

    } else {
      $log.warn('Got error on /migrations');
      $scope.migratingProjectsError = true;
    }
  });

  // GET the migrations that have completed
  var migratedUrl = $rootScope.urls.migratedUrl;

  Migrated.getMigrated(migratedUrl).then(function(result) {
    if (result.status ===200) {
      $scope.migratedProjects = _.sortBy(result.data.entity, 'site_id');
      $rootScope.status.migrated = moment().format('h:mm:ss');
      $log.info(moment().format('h:mm:ss') + ' - migrated projects loaded');
      $log.info(' - - - - GET /migrated');
    } else {
      $log.warn('Got error on /migrated');
      $scope.migratedProjectsError = true;
    }
  });

  //handler for a request for the tools of a given project site
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
  
  //handler for a request for the user's Box folders
  $scope.getBoxFolders = function() {
    // get the box folder info if it has not been gotten yet
    if (!$scope.boxFolders) {
      var boxUrl = '/box/folders';
      Projects.getBoxFolders(boxUrl).then(function(result) {
        $scope.boxFolders = result.data;
        $log.info(moment().format('h:mm:ss') + ' - BOX folder info requested');
        $log.info(' - - - - GET /box/folders');
      });
    }  
  };

  //handler for a user's selection of a particular Box folder 
  //as a destination of a migration
  $scope.boxFolderSelect = function(folder) {
    $scope.selectBoxFolder = {'name':folder.name,'id':folder.ID};
    $log.info('BOX Folder "' + $scope.selectBoxFolder.name + '" (ID: ' + $scope.selectBoxFolder.id + ') selected');
  };
  
  //handler for a user's selection of export destination type: local download or export to Box 
  //as a destination of a migration
  $scope.destinationTypeSelect = function(site_id, tool_id, name, id) {
    // decorate both the tool row and the parent site row
    var toolRow = _.findWhere($scope.sourceProjects, {tool_id: tool_id});
    toolRow.selectDestinationType = {'name':name,'id':id};
    var parentRow = _.findWhere($scope.sourceProjects, {site_id: site_id, tool_id:''});
    parentRow.selectDestinationType = {'name':name,'id':id};
  };

  /*
  change handler for tool checkboxes that determines if at least one is checked
  - if so, the export button is revealed, if not it is hidden
  */
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

  // handler for the Export button (user has selected tools, specified dependencies and clicked on the "Export" button)
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

  // handler for the Cancel migration button - all states are reset for that particular project site
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

  //handler for showing the details of a migrated thing
  $scope.showDetails = function(index){
    $scope.details = index;
  };

  /*
  handler for the "Proceed" button (tools are selected, dependencies addressed, confirmation displayed)
  */
  $scope.startMigration = function(projectId, siteName, destinationType) {
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {
      site_id: projectId
    }));

    $scope.sourceProjects[targetProjPos].migrating=true;
    $scope.sourceProjects[targetProjPos].stateExportConfirm = false;
    
    var targetSelections = _.where($scope.sourceProjects, {
      site_id: projectId,
      selected:true
    });

    // for each tool selected
    $.each(targetSelections, function( key, value ) {
      // Get the base post url
      var migrationZipUrl = $rootScope.urls.migrationZipUrl;
      var migrationBoxUrl = $rootScope.urls.migrationBoxUrl;
      var migrationUrl='';
      // attach variables to it
      if (destinationType =='box')
      {
    	  // migrate to Box
    	  migrationUrl = migrationBoxUrl + '?site_id=' + projectId + '&site_name=' + siteName + '&tool_id=' + value.tool_id + '&tool_name=' + value.tool_name + '&destination_type=' + 'box&box_folder_id=' + $scope.selectBoxFolder.id;
         

          $log.info("box " + migrationUrl);
    	  // use promise factory to execute the post
          Migration.postMigrationBox(migrationUrl).then(function(result) {
            $log.info(' - - - - POST ' + migrationUrl);
            $log.warn(' - - - - after POST we start polling for /migrations every ' + $rootScope.pollInterval/1000 + ' seconds');
            pollMigrations(migratingUrl,$rootScope.pollInterval);
          });
      }
      else
      {
    	  // download locally
    	  migrationUrl = migrationZipUrl + '?site_id=' + projectId + '&site_name=' + siteName + '&tool_id=' + value.tool_id + '&tool_name=' + value.tool_name + '&destination_type=' + destinationType;

          $log.info("zip " + migrationUrl);
    	  // use promise factory to execute the post
          Migration.getMigrationZip(migrationUrl).then(function(result) {
            $log.info(' - - - - POST ' + migrationUrl);
            $log.warn(' - - - - after POST we start polling for /migrations every ' + $rootScope.pollInterval/1000 + ' seconds');
            pollMigrations(migratingUrl,$rootScope.pollInterval);
          });
      }
      $log.warn(moment().format('h:mm:ss') + ' - project migration started for ' + migrationUrl);
    });
  };

  var pollMigrations = function (migratingUrl,interval){
    // stop all existing polling of /migrations end point if any
      $log.info(' - - - - stop all (if any) /migrations polls');
      PollingService.stopPolling('migrationsAfterPageLoad');
      PollingService.stopPolling('migrationsOnPageLoad');
      // start a new polling of /migrations endpoint
      PollingService.startPolling('migrationsAfterPageLoad', migratingUrl, $rootScope.pollInterval, function(result) {
        if (result.data.entity.length === 0) {
          $log.warn('Nothing being migrated: reloading /migrated and then stopping polling');
          PollingService.stopPolling('migrationsAfterPageLoad');
        }
        else {
          $log.info(moment().format('h:mm:ss') + ' - projects being migrated polled after  migration request');
          $log.info(' - - - - GET /migrations/');
        }  
        //update time stamp displayed in the /migrations panel
        $rootScope.status.migrations = moment().format('h:mm:ss');
        $scope.migratingProjects = _.sortBy(transformMigrations(result).data.entity, 'site_id');
        
        // if the data from last poll is different from this poll
        if(!angular.equals($scope.migratingProjects, $scope.migratingProjectsShadow)){
          $log.warn(moment().format('h:mm:ss') + ' - migrating panel changed - migrated projects reloaded');
          $log.info(' - - - - GET /migrated');
          // request the /migrated data and reload the panel
          Migrated.getMigrated(migratedUrl).then(function(result) {
            if (result.status ===200) {
              $scope.migratedProjects = result.data.entity;
              $rootScope.status.migrated = moment().format('h:mm:ss');
            } else {
              $log.warn('Got error on /migrated');
              $scope.migratedProjectsError = true;
            }
          });
        }
        //TODO: this only really makes sense by comparing /projects and /migrated
        $.each($scope.sourceProjects, function(index, project ) {
          if(_.findWhere($scope.migratingProjects, {tool_site_id:project.tool_site_id})) {
            project.migrating = true;
          } else {
            project.migrating = false;
            project.stateSelectionExists = false;
            project.stateExportConfirm = false;
            project.selected = false;
            project.stateHasTools = false;
            project.hideTools = true;
          }
          if(_.findWhere($scope.migratingProjects, {site_id:project.site_id})) {
            project.migrating = true;
          } else {
            project.migrating = false;
          }  
        });
        // copy the current data for /migrations to the shadow
        $scope.migratingProjectsShadow = $scope.migratingProjects;
      });
  }
}]);
