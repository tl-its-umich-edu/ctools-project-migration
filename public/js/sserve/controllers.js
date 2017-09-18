'use strict';
/* global projectMigrationApp, document, angular, _, moment, $, transformMigrations, prepareReport, transformMigrated, addSiteStatus, window */

/* MIGRATIONS CONTROLLER */
projectMigrationApp.controller('projectMigrationController', ['Projects','ProjectsLite','Migration','Migrated','PollingService','focus','$rootScope','$scope','$log','$q','$timeout','$window','$http',function(Projects, ProjectsLite, Migration, Migrated, PollingService, focus, $rootScope, $scope, $log, $q, $timeout, $window, $http) {
    $scope.loadingProjects = true;
    $scope.sourceProjects = [];
    $scope.migratingProjects = [];
    $scope.migratedProjects = [];
    $scope.boxAuthorized = false;
    $scope.selectionIsMade = false;

    if ($('#sserve-lite').length) {
      $scope.migratingActive = false;
    } else {
      $scope.migratingActive = true;
    }

    var pingCToolsUrl = $rootScope.urls.pingCtools;
    Projects.pingDependency(pingCToolsUrl).then(function(result) {
      if(result.data.status ==='DOWN'){
        $scope.ctoolsDown = true;
      }
    });
    // GET the project list
    var projectsUrl = $rootScope.urls.projectsUrl;
     //adding the window.location.search to getProjects call as well since both isAdmin() and getProject() call
     // are async and anyone can happen first so we want the parameters to be sent to backend as expected
    Projects.getProjects(projectsUrl + window.location.search).then(function(result) {
      $scope.sourceProjects = result;
      // launch a decorator to query site status - only launched in sserve-lite view
      if (!$scope.migratingActive) {
        $scope.addSiteStatus();
      }
      $scope.loadingProjects = false;
      $log.info(moment().format('h:mm:ss') + ' - source projects loaded');
      $log.info(' - - - - GET /projects');
    });
    // GET the migrations that have completed
    var migratedUrl = $rootScope.urls.migratedUrl;
    Migrated.getMigrated(migratedUrl).then(function(result) {
      if (result.status === 200) {
        result = transformMigrated(result);
        $scope.recentlyMigratedProjects = _.last(_.sortBy(result.data.entity,'end_time'), 5).reverse();
        $scope.migratedProjects = _.sortBy(result.data.entity,'site_name');
        $rootScope.status.migrated = moment().format('h:mm:ss');
        $log.info(moment().format('h:mm:ss') + ' - migrated projects loaded');
        $log.info(' - - - - GET /migrated');
        updateProjectsPanel($scope.migratedProjects,'migrated');
        $log.info('Updating projects panel based on COMPLETED migrations');
      } else {
        $log.warn('Got error on /migrated');
        $scope.migratedProjectsError = true;
      }
      poll('pollMigrated',migratedUrl,$rootScope.pollInterval,'migrated');
    });
    // handler for a request for the tools of a given project site
    $scope.getTools = function(projectId, deleteStatus) {
      //  cannot refactor this one as it takes parameters
      var projectUrl = $rootScope.urls.projectUrl + projectId;
      var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {site_id: projectId}));
      $scope.gettingTools = 'Getting migration options for site' + $scope.sourceProjects[targetProjPos].site_name;
      $scope.sourceProjects[targetProjPos].loadingTools = true;
      Projects.getProject(projectUrl, deleteStatus).then(function(result) {
        $scope.gettingTools = 'Migration options for site ' + $scope.sourceProjects[targetProjPos].site_name + ' added';
        if(!$scope.migratingActive && result.data) {
          if(!$scope.migratingActive){
            result.data = $scope.toolStatus(result.data);
          }
        }
        // add the tools after theproject object
        $scope.sourceProjects.splice.apply($scope.sourceProjects, [targetProjPos + 1,0].concat(result.data));
        // get a handle on the first
        // tool
        var firstToolId = 'toolSel' + result.data[0].site_id + result.data[0].tool_id;
        // use the handle to pass
        // focus to the first tool
        $scope.$evalAsync(function() {
          focus(firstToolId);
        });

        // state management
        $scope.sourceProjects[targetProjPos].stateHasTools = true;
        $scope.sourceProjects[targetProjPos].loadingTools = false;
        $log.info(moment().format('h:mm:ss') + ' - tools requested for project ' + $scope.sourceProjects[targetProjPos].entityTitle + ' ( site ID: s' + projectId + ')');
        $log.info(' - - - - GET /projects/' + projectId);
      });
    };
    $scope.displayMask = function(){
      $('#maskModal').modal('show');
    };
    // handler for showing the details of a migrated thing
    $scope.showDetails = function(migration_id, site_title) {
      var targetMigrationPos = $scope.migratedProjects.indexOf(_.findWhere($scope.migratedProjects, {migration_id: migration_id}));
      var reportDetails = $scope.migratedProjects[targetMigrationPos].status;
      var destination = $scope.migratedProjects[targetMigrationPos].destination_type;
      reportDetails.title = site_title;
      $scope.reportDetails = reportDetails;
    };

    $scope.startMigrationEmail = function(project, destinationType) {
      //add a mask with a modal to not allow more than one download at a time
      $('#maskModal').modal('show');
      project.processing = true;

      $timeout(function() {
        project.processing = false;
      }, 10000);

      var migrationUrl ='/migrationMailArchiveZip?site_id=' + project.site_id  + '&tool_id=' + project.tool_id  + '&tool_name=' + project.tool_name + '&destination_type=' + destinationType;
      $log.info('Posting request for ' + destinationType + ' with a POST to:  ' + migrationUrl);
       ProjectsLite.startMigrationEmail(migrationUrl).then(function(result) {
         project.makeDisabled = false;
       });
    };


    // handler for the "Proceed" button (tools are selected, dependencies addressed, confirmation displayed)
    $scope.startMigration = function(projectId,siteName, destinationType) {
      var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {site_id: projectId}));
      var targetProjChildPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {site_id: projectId,tool: true}));
      $scope.sourceProjects[targetProjPos].migrating = true;
      $scope.sourceProjects[targetProjChildPos].migrating = true;
      $scope.sourceProjects[targetProjPos].stateExportConfirm = false;

      $scope.$evalAsync(function() {
        focus('project' + projectId);
      });

      var targetSelections = _.where(
        $scope.sourceProjects, {
          site_id: projectId,
          selected: true
        });

      // for each tool selected
      $.each(targetSelections,function(key, value) {
        // Get the base post url
        var migrationZipUrl = $rootScope.urls.migrationZipUrl;
        var migrationBoxUrl = $rootScope.urls.migrationBoxUrl;
        var migrationUrl = '';
        // attach variables to it
        if (destinationType == 'Box') {
          // migrate to Box
          migrationUrl = migrationBoxUrl + '?site_id=' + projectId + '&tool_id=' + value.tool_id + '&tool_name=' + value.tool_name + '&destination_type=' + 'Box&box_folder_id=' + $scope.sourceProjects[targetProjPos].boxFolder.id + '&box_folder_name=' + $scope.sourceProjects[targetProjPos].boxFolder.name;
          $log.info("box " + migrationUrl);
          // use promise factory
          // to execute the post
          Migration.postMigrationBox(migrationUrl).then(function(result) {
            var thisMigration = (_.last(result.headers('location').split('/')));
            $scope.sourceProjects[targetProjPos].migration_id = thisMigration;
            $scope.sourceProjects[targetProjChildPos].migration_id = thisMigration;
            $scope.migratingProjects.push($scope.sourceProjects[targetProjChildPos]);
            $log.info(' - - - - POST ' + migrationUrl);
            $log.warn(' - - - - after POST we start polling for /migrations every ' + $rootScope.pollInterval / 1000 + ' seconds');
          });
        } else {
          // download locally
          migrationUrl = migrationZipUrl + '?site_id=' + projectId + '&tool_id=' + value.tool_id + '&tool_name=' + value.tool_name + '&destination_type=' + destinationType;
          $log.info("zip " + migrationUrl);
          // use promise factory
          // to execute the post
          Migration.getMigrationZip(migrationUrl).then(function(result) {
            // add a mask with a modal to not allow more that on zip request at the time
            $('#maskModal').modal('show');
            $scope.sourceProjects[targetProjChildPos].makeDisabled=false;
            $scope.migratingProjects.push($scope.sourceProjects[targetProjChildPos]);
            $log.info(' - - - - POST ' + migrationUrl);
            $log.warn(' - - - - after POST we start polling for /migrations every ' + $rootScope.pollInterval / 1000 + ' seconds');
          });
        }
        $log.warn(moment().format('h:mm:ss') + ' - project migration started for ' + migrationUrl);
      });
    };
    var poll = function(pollName, url, interval,targetPanel) {
      PollingService.startPolling(pollName,url,$rootScope.pollInterval,function(result) {
        $log.info(moment().format('h:mm:ss') + ' polled: ' + pollName + ' for ' + url);

        if (targetPanel === 'migrations') {
          if (result.data.status === 200) {
            if ($scope.migratingProjects.length > 0) {
              $rootScope.activeMigrations = true;
            }
            // update time stamp
            // displayed in
            // /migrations panel
            $rootScope.status.migrations = moment().format('h:mm:ss');
            $scope.migratingProjects = _.sortBy(transformMigrations(result).data.entity,'start_time').reverse();
            // this poll has
            // different data
            // than the last one
            if (!angular.equals($scope.migratingProjects,$scope.migratingProjectsShadow)) {
              $log.info('Updating projects panel based on CURRENT migrations poll');
              updateProjectsPanel($scope.migratingProjects,'migrating');
            }
            $scope.migratingProjectsShadow = _.sortBy(transformMigrations(result).data.entity,'start_time').reverse();
            // clear error
            // condition if any
            $scope.migratingProjectsError = false;
          } else {
              // declare error and
              // show error
              // message in UI
              $scope.migratingProjectsError = true;
            }
        } else {
              // update time stamp
            // displayed in
            // /migrated panel
            $rootScope.status.migrated = moment().format('h:mm:ss');
            if (result.data.status === 200) {
              result = transformMigrated(result);
              $scope.migratedProjects = _.sortBy(result.data.entity,'site_name');
              $scope.recentlyMigratedProjects = _.last(_.sortBy(result.data.entity,'end_time'), 5).reverse();
              // this poll has
              // different data
              // than the last one
              if (!angular.equals($scope.migratedProjects,$scope.migratedProjectsShadow)) {
                // remove the modal mask
                $('#maskModal').modal('hide');
                $log.info('migrated has changed - call a function to update projects panel');
                // update
                // project panel
                // based on new
                // data
                $log.info('Updating projects panel based on COMPLETED migrations poll');
                updateProjectsPanel($scope.migratedProjects,'migrated');
              }
              $scope.migratedProjectsShadow = _.sortBy(result.data.entity,'site_name');
              // clear error
              // condition if any
              $scope.migratedProjectsError = false;
        } else {
          // declare error and
          // show error
          // message in UI
          $scope.migratedProjectsError = true;
        }
        }
      });
    };
    var updateProjectsPanel = function(result, source) {
      $log.info('updating projects panel because of changes to /' + source);
      if (source === 'migrating') {
        $log.warn('updateProjectsPanel for ' + source);
        if (result.length) {
          // there are /migrating items
          // for each source project, if it is
          // represented in /migrating, lock it,
          // if not uunlock it
          _.each($scope.sourceProjects,function(sourceProject) {
            if (sourceProject !== null && sourceProject !== undefined) {
              var projectMigrating = _.findWhere(result, {site_id: sourceProject.site_id});
              if (projectMigrating) {
                $log.warn('locking ' + sourceProject.site_name);
                sourceProject.migrating = true;
              }
            }
          });
        } else {
          // there are no /migrating items, so
          // loop through all the source projects
          // and reset their state
          $log.warn('unlocking all');
          _.each($scope.sourceProjects,function(sourceProject) {
            if (sourceProject) {
              sourceProject.migrating = false;
            }
          });
        }
      } else {
        // add the latest migrated date for each
        // project by
        // sorting /migrated and then finding for
        // each project the first correlate
        // /migrated
        var sortedMigrated = _.sortBy(result,'end_time').reverse();
        _.each($scope.sourceProjects,function(sourceProject) {
          if (sourceProject !== null && sourceProject !== undefined) {
            var projectMigrated = _.findWhere(sortedMigrated, {site_id: sourceProject.site_id});
            if (projectMigrated) {
              sourceProject.last_migrated = projectMigrated.end_time;
            }
          }
          // if the project has a
          // migration_id, see if
          // there is a /migrated
          // item
          // with the same id, if
          // so, unlock it
          if (sourceProject) {
            if (sourceProject.migration_id) {
              var migrationDone = _.findWhere(sortedMigrated, {migration_id: sourceProject.migration_id});
              if (migrationDone) {
                sourceProject.migrating = false;
                sourceProject.stateSelectionExists = false;
                sourceProject.selectDestinationType = {};
                sourceProject.stateHasTools = false;
                if (sourceProject.tool_id !== '') {
                  var index = $scope.sourceProjects.indexOf(sourceProject);
                  $scope.sourceProjects.splice(index,1);
                }
              }
            }
          }
        });
      }
    };

    // handlers for posting 1) user acceptance that a site may be deleted and 2) user requests to not have certain tools migrated
    $scope.updateProjectListSettings = function() {
      // get the sites that the user has
      // marked as ok to be deleted
      var targetDelete = _.where($scope.sourceProjects, {deleteProject: true});
      // initialize empty array
      var targetDeleteData = [];
      var targetDeleteConfirm = [];
      // populate the array
      _.each(targetDelete, function(target) {
        targetDeleteData.push('siteId=' + target.site_id);
        targetDeleteConfirm.push(target.site_name);
      });
      var targetDeleteDisplay = targetDeleteConfirm.join('\n');
      // if delete array has items
      // post acceptance of deletion (params are a joined targetDeleteData array)
      if(targetDeleteData.length){
        if (window.confirm('Click OK to add the site(s) to the processing queue for deletion.\n\n' + targetDeleteDisplay)) {
          var siteDeleteURL = '/deleteSite?' + targetDeleteData.join('&');
          ProjectsLite.postDeleteSiteRequest(siteDeleteURL).then(
            function(result) {
              $scope.selectionIsMade = false;
              if(result.data === 'Delete site choices saved.'){
                // find this site and add deleteStatus object to let user know
                _.each(targetDeleteData, function(targetSite){
                  var targetSiteId = targetSite.split('=');
                  var thisSiteTheseTools = _.where($scope.sourceProjects, {site_id: targetSite.split('=')[1]});
                  _.each(thisSiteTheseTools, function(thisSiteOrTool){
                    thisSiteOrTool.deleteStatus = {
                      'userId':'You have',
                      'consentTime': moment().valueOf()
                    };
                  });
                });
              }
            }
          );
        }
      }
    };
    // launched after sourceProjects has been added to the scope it decorates sourceProjects with the delete consent status
    $scope.addSiteStatus = function(){
      $scope.checkingDeleteFlags = true;
      // request status from /isSiteToBeDeleted  endpoint for each site
      _.each($scope.sourceProjects, function(site, index){
        var getSiteInfoUrl = '/isSiteToBeDeleted?siteId=' + site.site_id;
        ProjectsLite.isSiteToBeDeleted(getSiteInfoUrl).then(
          function(result) {
             if (result.data.entity) {
               site.deleteStatus = result.data.entity;
             }
          }
        );
        if(index + 1 === $scope.sourceProjects.length){
            $scope.unlockOptions = true;
            $scope.checkingDeleteFlags = false;
        }
      });
    };
    // for eachtool returned - query if it has been flagged for no migration - and also
    // peek into completed migrations to see if it has been migrtated and decorate tool with last
    // migration status
    $scope.toolStatus = function(data){
      _.each(data, function(tool){
        var migratedMatch =[];
        var boxMigratedMatch=[];
        // disabled (TLCPM-710)
        // var siteToolNotMigrateUrl = '/siteToolNotMigrate?siteId=' + tool.site_id + '&toolId=' + tool.tool_id;
        // ProjectsLite.siteToolNotMigrate(siteToolNotMigrateUrl).then(
        //   function(result) {
        //      if (result.data.entity) {
        //        tool.doNotMigrateStatus = result.data.entity[0];
        //      }
        //   }
        // );
        _.each($scope.migratedProjects, function(migrated){
          if(migrated.tool_id === tool.tool_id){
            if(migrated.destination_type ==='box'){
                //disabled (TLCPM-710)
                // tool.boxMigration = true;
                // boxMigratedMatch.push({'migratedBy':migrated.migrated_by.split(',')[0],'migratedWhen':migrated.end_time,'migratedHow':migrated.destination_type,'migratedTo':migrated.destination_url});
            } else {
              migratedMatch.push({'migratedBy':migrated.migrated_by.split(',')[0],'migratedWhen':migrated.end_time,'migratedHow':migrated.destination_type});
            }


          }
        });
        if(boxMigratedMatch.length){
          tool.boxMigrationInfo = _.sortBy(boxMigratedMatch, 'migratedWhen').reverse()[0];
        }
        if(migratedMatch.length){
          tool.lastMigratedStatus = _.sortBy(migratedMatch, 'migratedWhen').reverse()[0];
        }

      });
      return data;
    };
    // handler for finding out what status a site has.
    $scope.getSiteInfo = function (projectId) {
      var getSiteInfoUrl = '/isSiteToBeDeleted?siteId=' + projectId;
      ProjectsLite.isSiteToBeDeleted(getSiteInfoUrl).then(
        function(result) {
          $log.info(result);
        }
      );
    };
    // on selection - if there is a selection enable button to post selections
    $scope.updateProjectListCheck = function() {
      var targetDoNotMove = _.where($scope.sourceProjects, {selectedDoNotMove: true});
      var targetDelete = _.where($scope.sourceProjects, {deleteProject: true});
      if (targetDelete.length || targetDoNotMove.length) {
        $scope.selectionIsMade = true;
      } else {
        $scope.selectionIsMade = false;
      }
    };
    $scope.exportSiteMembership = function(site_id, site_name) {
      var membershipUrl = 'siteMembership/' + site_id + '?format=long';
      Projects.getMembership(membershipUrl).then(function(result) {
        $scope.membership = {
	         'metadata': {
		           'site_name': site_name,
               'status':result.status,
               'statusType':result.statusType
	          },
	           "data": result.data
           };
        $log.info(moment().format('h:mm:ss') + ' - membership for site ' + site_id + ' retrieved');
      });
    };
  }
]);



projectMigrationApp.controller('projectMigrationControllerStatus', ['Status',
  '$rootScope', '$scope', '$log', '$q', '$window',
  function(Status, $rootScope, $scope, $log, $q, $window) {
    $scope.getStatus = function() {
      Status.getStatus('/status').then(function(result) {
        $rootScope.server_status = result.data;
      });
    };
  }
]);

projectMigrationApp.controller('mappingsController', ['Status',
  '$rootScope', '$scope', '$log', '$q', '$window',
  function(Status, $rootScope, $scope, $log, $q, $window) {

    $scope.getMappings = function() {
      Status.getStatus('/mappings.json').then(function(result) {
        $scope.mappingsList = result.data;
      });
    };
  }
]);
