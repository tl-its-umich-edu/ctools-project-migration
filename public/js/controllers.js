'use strict';
/* global projectMigrationApp, angular, _, moment, $ */

/* MIGRATIONS CONTROLLER */
projectMigrationApp.controller('projectMigrationController', ['Projects', 'Migration', 'Migrations', 'Migrated', 'PollingService', 'focus', '$rootScope', '$scope', '$log', '$q', '$timeout', '$window', '$http', function(Projects, Migration, Migrations, Migrated, PollingService, focus, $rootScope, $scope, $log, $q, $timeout, $window, $http) {
  $scope.loadingProjects = true;
  $scope.sourceProjects = [];
  $scope.migratingProjects = [];
  $scope.migratedProjects = [];
  $scope.boxAuthorized = false;
  // whether the current user authorized app to Box or not
  var checkBoxAuthorizedUrl = $rootScope.urls.checkBoxAuthorizedUrl;
  Projects.checkBoxAuthorized(checkBoxAuthorizedUrl).then(function(result) {
	  if(result.data === 'true'){
      $scope.boxAuthorized = true;  
    }
    else {
     $scope.boxAuthorized = false;   
    }
    //$scope.boxAuthorized === result.data;
	  $log.info(' - - - - User authorized to Box: ' + result.data);
	});
  
  // GET the project list
  var projectsUrl = $rootScope.urls.projectsUrl;

  Projects.getProjects(projectsUrl).then(function(result) {
    $scope.sourceProjects = result;
    $scope.loadingProjects = false;
    $log.info(moment().format('h:mm:ss') + ' - source projects loaded');
    $log.info(' - - - - GET /projects');
  });

  // GET the current migrations list
  var migratingUrl = $rootScope.urls.migratingUrl;

  Migrations.getMigrations(migratingUrl).then(function(result) {

    if (result.status ===200) {
      $rootScope.status.migrations = moment().format('h:mm:ss');
      $log.info(moment().format('h:mm:ss') + ' - migrating projects loaded');
      $log.info(' - - - - GET /migrating');
      
      if (result.data.entity.length && result.status ===200) {
        $scope.migratingProjects = _.sortBy(transformMigrations(result).data.entity, 'site_id');
        $log.info('Updating projects panel based on CURRENT migrations');
        updateProjectsPanel($scope.migratingProjects, 'migrating');
      }

    } else {
      $log.warn('Got error on /migrations');
      $scope.migratingProjectsError = true;
    }
    poll('pollMigrations', migratingUrl, $rootScope.pollInterval, 'migrations');
  });

  // GET the migrations that have completed
  var migratedUrl = $rootScope.urls.migratedUrl;

  Migrated.getMigrated(migratedUrl).then(function(result) {
    if (result.status ===200) {
      $scope.migratedProjects = _.sortBy(result.data.entity, 'site_name');
      $rootScope.status.migrated = moment().format('h:mm:ss');
      $log.info(moment().format('h:mm:ss') + ' - migrated projects loaded');
      $log.info(' - - - - GET /migrated');
      updateProjectsPanel($scope.migratedProjects, 'migrated');
      $log.info('Updating projects panel based on COMPLETED migrations');
    } else {
      $log.warn('Got error on /migrated');
      $scope.migratedProjectsError = true;
    }
    poll('pollMigrated', migratedUrl, $rootScope.pollInterval, 'migrated');
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
      // get a handle on the first tool
      var firstToolId = 'toolSel' + result.data[0].tool_id;
      // use the handle to pass focus to the first tool
      $scope.$evalAsync(function() { 
        focus(firstToolId);
      })
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
      $scope.loadingFolders = true;
      var boxUrl = '/box/folders';
      Projects.getBoxFolders(boxUrl).then(function(result) {
        sessionStorage.setItem('boxFolders', JSON.stringify(result));
        $scope.loadingFolders = false;
        $scope.boxFolders = result.data;
        $log.info(moment().format('h:mm:ss') + ' - BOX folder info requested');
        $log.info(' - - - - GET /box/folders');
      });
    }  
  };
  
  //handler for a request for user Box account authentication/authorization
  $scope.boxAuthorize = function() {
	$log.info('---- in boxAuthorize ');
    // get the box folder info if it has not been gotten yet
    if (!$scope.boxAuthorized) {
      $log.info(' - - - - GET /box/authorize');
      var boxUrl = '/box/authorize';
      Projects.boxAuthorize(boxUrl).then(function(result) {
      $scope.boxAuthorizeHtml = result.data;
        $log.info(moment().format('h:mm:ss') + ' - BOX folder info requested');
        $log.info(' - - - - GET /box/authorize');
      });
    }  
  };

  // remove user authentication information from server memory 
  // user need to re-authenticate in the future to access their box account 
  $scope.boxUnauthorize = function() {
      var boxUrl = '/box/unauthorize';
      Projects.boxUnauthorize(boxUrl).then(function(result) {
        $scope.boxAuthorized = false;
    	// current user un-authorize the app from accessing Box
        $log.info(moment().format('h:mm:ss') + ' - unauthorize from Box account requested');
        $log.info(' - - - - GET /box/unauthorize');
      });
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
    if(name ==='Box'){
      $scope.$evalAsync(function() { 
        focus('toolSelDestBox' + tool_id);
      })
    }
    // decorate both the tool row and the parent site row
    var toolRow = _.findWhere($scope.sourceProjects, {tool_id: tool_id});
    toolRow.selectDestinationType = {'name':name,'id':id};
    var parentRow = _.findWhere($scope.sourceProjects, {site_id: site_id, tool_id:''});
    parentRow.selectDestinationType = {'name':name,'id':id};
    if(parentRow.stateSelectionExists && (parentRow.selectDestinationType.name==='zip' || parentRow.selectDestinationType.name==='Box' && $scope.selectBoxFolder) && !parentRow.stateExportConfirm && !parentRow.migrating){
      $scope.$evalAsync(function() { 
        focus('export' + site_id);
      })
    }
  };

  /*
  change handler for tool checkboxes that determines if at least one is checked
  - if so, the export button is revealed, if not it is hidden
  */
  $scope.checkIfSelectionExists = function(projectId, toolId) {
    var allTargetProjs = _.where($scope.sourceProjects, {
      site_id: projectId
    });
    var targetSelections = _.where($scope.sourceProjects, {
      site_id: projectId,
      selected:true
    });

    if(targetSelections.length) {
      $scope.$evalAsync(function() { 
        focus('toolSelDest' + toolId);
      });
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
      $scope.$evalAsync(function() { 
        focus('confirm' + projectId);
      });


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

    $scope.$evalAsync(function() { 
      focus('project' + projectId);
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
  $scope.showDetails = function(index, site_title){
    var reportDetails = $scope.migratedProjects[index].status;
    reportDetails.title = site_title;
    sessionStorage.setItem('proj_migr_report', JSON.stringify(reportDetails));
    var reportWin = window.open('/report.html', 'ReportWindow', 'toolbar=yes, status=no, menubar=yes, resizable=yes, scrollbars=yes, width=670, height=800');
    reportWin.focus();
  };

  $scope.checkBoxAuth = function(){
    var checkBoxAuthorizedUrl = $rootScope.urls.checkBoxAuthorizedUrl;
    Projects.checkBoxAuthorized(checkBoxAuthorizedUrl).then(function(result) {
      if(result.data ==='true'){
        $scope.boxAuthorized = true;  
      }
      else {
        $scope.boxAuthorized = false;  
      }
      $log.info(' - - - - User authorized to Box: ' + result.data);  
    });
  }

  // on dismiss box auth modal, launch a function to check if box auth
$(document).on('hidden.bs.modal', '#boxAuthModal', function(){
  var appElement = $('#boxAuthModal');
  var $scope = angular.element(appElement).scope();
  $scope.$apply(function() {
    appElement.scope().checkBoxAuth();
  });
});



  /*
  handler for the "Proceed" button (tools are selected, dependencies addressed, confirmation displayed)
  */
  $scope.startMigration = function(projectId, siteName, destinationType) {
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {
      site_id: projectId
    }));

    var targetProjChildPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {
      site_id: projectId, tool: true
    }));

    $scope.sourceProjects[targetProjPos].migrating=true;
    $scope.sourceProjects[targetProjChildPos].migrating=true;
    $scope.sourceProjects[targetProjPos].stateExportConfirm = false;

    $scope.$evalAsync(function() { 
      focus('project' + projectId);
    });
    
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
      if (destinationType =='Box')
      {
    	  // migrate to Box
    	  migrationUrl = migrationBoxUrl + '?site_id=' + projectId + '&site_name=' + siteName + '&tool_id=' + value.tool_id + '&tool_name=' + value.tool_name + '&destination_type=' + 'Box&box_folder_id=' + $scope.selectBoxFolder.id;
         
          $log.info("box " + migrationUrl);
    	  // use promise factory to execute the post
          Migration.postMigrationBox(migrationUrl).then(function(result) {
            var thisMigration = (_.last(result.headers('location').split('/')));
            $scope.sourceProjects[targetProjPos].migration_id=thisMigration;
            $scope.sourceProjects[targetProjChildPos].migration_id=thisMigration;
            $scope.migratingProjects.push($scope.sourceProjects[targetProjChildPos])
            $log.info(' - - - - POST ' + migrationUrl);
            $log.warn(' - - - - after POST we start polling for /migrations every ' + $rootScope.pollInterval/1000 + ' seconds');
          });
      }
      else
      {
    	  // download locally
    	  migrationUrl = migrationZipUrl + '?site_id=' + projectId + '&site_name=' + siteName + '&tool_id=' + value.tool_id + '&tool_name=' + value.tool_name + '&destination_type=' + destinationType;

          $log.info("zip " + migrationUrl);
    	  // use promise factory to execute the post
          Migration.getMigrationZip(migrationUrl).then(function(result) {
            $scope.migratingProjects.push($scope.sourceProjects[targetProjChildPos])
            $log.info(' - - - - POST ' + migrationUrl);
            $log.warn(' - - - - after POST we start polling for /migrations every ' + $rootScope.pollInterval/1000 + ' seconds');
          });
      }
      $log.warn(moment().format('h:mm:ss') + ' - project migration started for ' + migrationUrl);
    });
  };

var poll = function (pollName, url, interval, targetPanel){
  PollingService.startPolling(pollName, url, $rootScope.pollInterval, function(result) {
    $log.info(moment().format('h:mm:ss') + ' polled: ' + pollName + ' for ' + url);

    if(targetPanel === 'migrations'){
      if(result.data.status ===200) {
        if($scope.migratingProjects.length > 0){
          $rootScope.activeMigrations = true;
        }
        //update time stamp displayed in /migrations panel
        $rootScope.status.migrations = moment().format('h:mm:ss');
        $scope.migratingProjects = _.sortBy(transformMigrations(result).data.entity, 'site_id');
        // this poll has different data than the last one
        if(!angular.equals($scope.migratingProjects, $scope.migratingProjectsShadow)){
          $log.info('Updating projects panel based on CURRENT migrations poll');
          updateProjectsPanel($scope.migratingProjects, 'migrating');
        }
        $scope.migratingProjectsShadow = _.sortBy(transformMigrations(result).data.entity, 'site_id');
        //clear error condition if any
        $scope.migratingProjectsError = false;
      }
      else {
        //declare error and show error message in UI
        $scope.migratingProjectsError = true;
      }
    } else {
      //update time stamp displayed in /migrated  panel
      $rootScope.status.migrated = moment().format('h:mm:ss');
      if(result.data.status ===200) {
        $scope.migratedProjects = _.sortBy(result.data.entity, 'site_name');
        // this poll has different data than the last one
        if(!angular.equals($scope.migratedProjects, $scope.migratedProjectsShadow)){
          $log.info('migrated has changed - call a function to update projects panel');
          // update project panel based on new data
          $log.info('Updating projects panel based on COMPLETED migrations poll');
          updateProjectsPanel($scope.migratedProjects, 'migrated')
        }
        $scope.migratedProjectsShadow = _.sortBy(result.data.entity, 'site_name');
        //clear error condition if any
        $scope.migratedProjectsError = false; 
      } else {
        //declare error and show error message in UI
        $scope.migratedProjectsError = true; 
      }

    }
  });
}

var updateProjectsPanel = function(result, source){
  $log.info('updating projects panel because of changes to /' + source);
  if(source==='migrating'){
    $log.warn('updateProjectsPanel for ' + source )
    if(result.length) {
      //there are /migrating items
      // for each source project, if it is represented in /migrating, lock it, if not uunlock it
      _.each($scope.sourceProjects, function(sourceProject) {
        if(sourceProject !==null && sourceProject !==undefined){
          var projectMigrating = _.findWhere(result, {site_id: sourceProject.site_id});
          if (projectMigrating) {
            $log.warn('locking ' + sourceProject.site_name)
            sourceProject.migrating = true;
          }
        }
      });
    }
    else {
      // there are no /migrating items, so loop through all the source projects and reset their state
      $log.warn('unlocking all');
      _.each($scope.sourceProjects, function(sourceProject) {
        if(sourceProject) {
          sourceProject.migrating = false;
        }
     });   
    }
  }
  else {
    // add the latest migrated date for each project by
    // sorting /migrated and then finding for each project the first correlate /migrated
    var sortedMigrated =  _.sortBy(result, 'end_time').reverse();
    _.each($scope.sourceProjects, function(sourceProject) {
      if(sourceProject !==null && sourceProject !==undefined){
        var projectMigrated = _.findWhere(sortedMigrated, {site_id: sourceProject.site_id});
        if (projectMigrated) {
          sourceProject.last_migrated = projectMigrated.end_time;
        }
      }
      // if the project has a migration_id, see if there is a /migrated item
      // with the same id, if so, unlock it
      if(sourceProject){
        if(sourceProject.migration_id){
          var migrationDone = _.findWhere(sortedMigrated, {migration_id: sourceProject.migration_id});
          if(migrationDone) {
            sourceProject.migrating = false;
            sourceProject.stateSelectionExists =false;
            sourceProject.selectDestinationType = {};
            sourceProject.stateHasTools = false;
            if (sourceProject.tool_id !=='') {
              var index = $scope.sourceProjects.indexOf(sourceProject);
              $scope.sourceProjects.splice(index, 1); 
            }

          }
        }
      }
    });  
  }
}

}]);

projectMigrationApp.controller('reportController', ['$rootScope', '$scope', '$log', '$q', '$window', function($rootScope, $scope, $log, $q, $window) {
  $scope.reportDetails = JSON.parse(sessionStorage.getItem('proj_migr_report'));
}]);

projectMigrationApp.controller('projectMigrationControllerStatus', ['Status', '$rootScope', '$scope', '$log', '$q', '$window', function(Status, $rootScope, $scope, $log, $q, $window) {

  $scope.getStatus = function(){
    Status.getStatus('/status').then(function(result) {
      $rootScope.server_status = result.data;
    });  
  }
}]);