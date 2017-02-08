'use strict';
/* global projectMigrationApp, document, angular, _, moment, $, transformMigrations, prepareReport, transformMigrated, addSiteStatus, window */

/* MIGRATIONS CONTROLLER */
projectMigrationApp.controller('projectMigrationController', ['Projects','ProjectsLite','Migration','Migrated','PollingService','focus','$rootScope','$scope','$log','$q','$timeout','$window','$http',function(Projects, ProjectsLite, Migration, Migrated, PollingService, focus, $rootScope, $scope, $log, $q, $timeout, $window, $http) {
    $scope.loadingProjects = true;
    $scope.sourceProjects = [];
    $scope.migratingProjects = [];
    $scope.migratedProjects = [];
    $scope.boxAuthorized = false;
    $scope.isAdminUser = false;
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

    // whether the current user is a member of the admin group or n0t
    var checkIsAdminUserUrl = $rootScope.urls.checkIsAdminUser;
    Projects.checkIsAdminUser(checkIsAdminUserUrl + window.location.search).then(function(result) {
      $timeout(function() {
        if (result.data.isAdmin) {
          $scope.isAdminUser = true;
        } else {
          $scope.isAdminUser = false;
        }
        $log.info(' - - - - User is admin: ' + result.data);
      });
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
      $scope.sourceProjects[targetProjPos].loadingTools = true;
      Projects.getProject(projectUrl, deleteStatus).then(function(result) {
        if(!$scope.migratingActive && result.data) {
          if(!$scope.migratingActive){
            result.data = $scope.toolStatus(result.data);
          }
        }

        // add the tools after theproject object
        $scope.sourceProjects.splice.apply($scope.sourceProjects, [targetProjPos + 1,0].concat(result.data));
        // get a handle on the first
        // tool
        var firstToolId = 'toolSel' + result.data[0].tool_id;
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

      var migrationUrl ='/migrationMailArchiveZip?site_id=' + project.site_id  + '&tool_id=' + project.tool_id  + '&site_name=' + project.site_name + '&tool_name=' + project.tool_name + '&destination_type=' + destinationType;
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
          migrationUrl = migrationBoxUrl + '?site_id=' + projectId + '&site_name=' + siteName + '&tool_id=' + value.tool_id + '&tool_name=' + value.tool_name + '&destination_type=' + 'Box&box_folder_id=' + $scope.sourceProjects[targetProjPos].boxFolder.id + '&box_folder_name=' + $scope.sourceProjects[targetProjPos].boxFolder.name;
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
          migrationUrl = migrationZipUrl + '?site_id=' + projectId + '&site_name=' + siteName + '&tool_id=' + value.tool_id + '&tool_name=' + value.tool_name + '&destination_type=' + destinationType;
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

    $scope.unFlagSiteDeletion = function(project){
      $log.info('Unflagging project site deletion for ' + project.site_name);
      var unFlagSiteDeleteURL = 'deleteSite?siteId='  + project.site_id +'&reset=true';
      ProjectsLite.unFlagSiteDeletion(unFlagSiteDeleteURL).then(
        function(result) {
          if(result.data === 'Delete site choices saved.'){
            // find this site and remove deleteStatus object to let user know
            var thisSite = _.findWhere($scope.sourceProjects, {site_id: project.site_id});
            thisSite.deleteStatus = null;
            var thisSiteTheseTools = _.where($scope.sourceProjects,  {site_id: project.site_id});
            _.each(thisSiteTheseTools, function(thisSiteOrTool){
              thisSiteOrTool.deleteStatus = null;
              thisSiteOrTool.deleteProject = false;
            });
          }
        }
      );
    };

     // handler for removing a flag that tool not be migrated
     $scope.unFlagDoNotMigrate = function(project){
       $log.info('Unflagging request to not have tool migrated for ' + project.site_name);
       var unFlagDoNotMigrateURL = 'doNotMigrateTool?siteId=' + project.site_id + '&toolId=' + project.tool_id + '&toolType=' + project.tool_type + '&reset=true';
       ProjectsLite.unFlagDoNotMigrate(unFlagDoNotMigrateURL).then(
         function(result) {
           if(result.data === 'site tool delete exempt choice saved.'){
             var thisTool = _.findWhere($scope.sourceProjects, {tool_id: project.tool_id});
             thisTool.doNotMigrateStatus = null;
             thisTool.selectedDoNotMove = false;
           }
         }
       );
     };

    // handlers for posting 1) user acceptance that a site may be deleted and 2) user requests to not have certain tools migrated
    $scope.updateProjectListSettings = function() {
      // get the sites that the user has
      // marked as ok to be deleted
      var targetDelete = _.where($scope.sourceProjects, {deleteProject: true});
      // get the tools that have been
      // selected for no migration
      var targetDoNotMove = _.where($scope.sourceProjects, {selectedDoNotMove: true});

      // initialize empty arrays
      var targetDeleteData = [];
      var targetDoNotMoveData = [];
      // populate the arrays
      _.each(targetDelete, function(target) {
        targetDeleteData.push('siteId=' + target.site_id);
      });
      _.each(targetDoNotMove, function(target) {
        targetDoNotMoveData.push(
          {
            'url':'siteId=' + target.site_id+ '&toolId=' + target.tool_id + '&toolType=' + target.tool_type,
            'row':target.tool_site_id
        });
      });
      // if delete array has items
      // post acceptance of deletion (params are a joined targetDeleteData array)
      if(targetDeleteData.length){
        var siteDeleteURL = '/deleteSite?' + targetDeleteData.join('&');
        ProjectsLite.postDeleteSiteRequest(siteDeleteURL).then(
          function(result) {
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
      // if do not migrated tool request has items as many posts as selected tools
      if(targetDoNotMoveData.length){
        _.each(targetDoNotMoveData, function(toolNotToMigr) {
          var donotMigrateUrl = '/doNotMigrateTool?' + toolNotToMigr.url;
          ProjectsLite.doNotMigrateTool(donotMigrateUrl).then(
            function(result) {
              if(result.data ==='site tool delete exempt choice saved.') {
                // find this tool and add doNotMigrateStatus object to let user know
            	// "TOOLID&toolType=TYPE"
            	var toolId = donotMigrateUrl.split('=')[2];
            	// another splite will return the actual tool id
            	toolId = toolId.split('&')[0];
            	var thisTool = _.findWhere($scope.sourceProjects, {tool_id: toolId});
              thisTool.selectedDoNotMove = false;
            	thisTool.doNotMigrateStatus = {
                  'userId':'You have',
                  'consentTime': moment().valueOf()
                };
              }
            }
          );
        });
      }
      $scope.selectionIsMade = false;
    };
    // launched after sourceProjects has been added to the scope it decorates sourceProjects with the delete consent status
    $scope.addSiteStatus = function(){
      // request status from /isSiteToBeDeleted  endpoint for each site
      _.each($scope.sourceProjects, function(site){
        var getSiteInfoUrl = '/isSiteToBeDeleted?siteId=' + site.site_id;
        ProjectsLite.isSiteToBeDeleted(getSiteInfoUrl).then(
          function(result) {
             if (result.data.entity) {
               site.deleteStatus = result.data.entity;
             }
          }
        );
      });
    };
    // for eachtool returned - query if it has been flagged for no migration - and also
    // peek into completed migrations to see if it has been migrtated and decorate tool with last
    // migration status
    $scope.toolStatus = function(data){
      _.each(data, function(tool){
        var migratedMatch =[];
        var boxMigratedMatch=[];
        var siteToolNotMigrateUrl = '/siteToolNotMigrate?siteId=' + tool.site_id + '&toolId=' + tool.tool_id;
        ProjectsLite.siteToolNotMigrate(siteToolNotMigrateUrl).then(
          function(result) {
             if (result.data.entity) {
               tool.doNotMigrateStatus = result.data.entity[0];
             }
          }
        );
        _.each($scope.migratedProjects, function(migrated){
          if(migrated.tool_id === tool.tool_id){
            if(migrated.destination_type ==='box'){
                tool.boxMigration = true;
                boxMigratedMatch.push({'migratedBy':migrated.migrated_by.split(',')[0],'migratedWhen':migrated.end_time,'migratedHow':migrated.destination_type,'migratedTo':migrated.destination_url});
            } else {
              migratedMatch.push({'migratedBy':migrated.migrated_by.split(',')[0],'migratedWhen':migrated.end_time,'migratedHow':migrated.destination_type});
            }


          }
        });
        if(boxMigratedMatch.length){
          tool.boxMigrationInfo = _.sortBy(migratedMatch, 'migratedWhen').reverse()[0];
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
      var membershipUrl = 'siteMembership/' + site_id;
      Projects.getMembership(membershipUrl).then(function(result) {
        $scope.membership = {
	         'metadata': {
		           'site_name': site_name,
               'status':result.status,
               'statusType':result.statusType
	          },
	           "data": result.data
           };
          $log.info(JSON.stringify($scope.membership));
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
