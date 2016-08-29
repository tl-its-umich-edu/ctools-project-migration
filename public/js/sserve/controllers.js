'use strict';
/* global projectMigrationApp, document, angular, _, moment, $, transformMigrations, prepareReport, transformMigrated, addSiteStatus */

/* MIGRATIONS CONTROLLER */
projectMigrationApp.controller('projectMigrationController', ['Projects','ProjectsLite','Migration','Migrations','Migrated','PollingService','focus','$rootScope','$scope','$log','$q','$timeout','$window','$http',function(Projects, ProjectsLite, Migration, Migrations, Migrated, PollingService, focus, $rootScope, $scope, $log, $q, $timeout, $window, $http) {
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
    // whether the current user is a member of the admin group or n0t
    var checkIsAdminUserUrl = $rootScope.urls.checkIsAdminUser;
    Projects.checkIsAdminUser(checkIsAdminUserUrl).then(function(result) {
      $timeout(function() {
        if (result.data.isAdmin) {
          $scope.isAdminUser = true;
        } else {
          $scope.isAdminUser = false;
        }
        $log.info(' - - - - User is admin: ' + result.data);
      });
    });
    if($scope.migratingActive) {
      // whether the current user authorized app to Box or not
      var checkBoxAuthorizedUrl = $rootScope.urls.checkBoxAuthorizedUrl;
      Projects.checkBoxAuthorized(checkBoxAuthorizedUrl).then(function(result) {
        if (result.data === 'true') {
          $scope.boxAuthorized = true;
        } else {
          $scope.boxAuthorized = false;
        }
        // $scope.boxAuthorized ===
        // result.data;
        $log.info(' - - - - User authorized to Box: ' + result.data);
      });
    }
    // GET the project list
    var projectsUrl = $rootScope.urls.projectsUrl;
    Projects.getProjects(projectsUrl).then(function(result) {
      $scope.sourceProjects = result;
      // launch a decorator to query site status - only launched in sserve-lite view
      if (!$scope.migratingActive) {
        $scope.addSiteStatus();
      }
      $scope.loadingProjects = false;
      $log.info(moment().format('h:mm:ss') + ' - source projects loaded');
      $log.info(' - - - - GET /projects');
    });
    // GET the current migrations list
    var migratingUrl = $rootScope.urls.migratingUrl;
    if ($scope.migratingActive) {
      Migrations.getMigrations(migratingUrl).then(function(result) {
        if (result.status === 200) {
          $rootScope.status.migrations = moment().format('h:mm:ss');
          $log.info(moment().format('h:mm:ss') + ' - migrating projects loaded');
          $log.info(' - - - - GET /migrating');

          if (result.data.entity.length && result.status === 200) {
            $scope.migratingProjects = _.sortBy(transformMigrations(result).data.entity,'start_time').reverse();
            $log.info('Updating projects panel based on CURRENT migrations');
            updateProjectsPanel($scope.migratingProjects,'migrating');
          }
        } else {
            $log.warn('Got error on /migrations');
            $scope.migratingProjectsError = true;
        }
        poll('pollMigrations',migratingUrl,$rootScope.pollInterval,'migrations');
      });
    }
    // GET the migrations that have completed
    var migratedUrl = $rootScope.urls.migratedUrl;
    Migrated.getMigrated(migratedUrl).then(function(result) {
      if (result.status === 200) {
        _.each(result.data.entity,function(migrated) {
          migrated.status.data = prepareReport(migrated.status.data);
        });
        result = transformMigrated(result);
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
    $scope.getTools = function(projectId) {
      //  cannot refactor this one as it takes parameters
      var projectUrl = $rootScope.urls.projectUrl + projectId;
      Projects.getProject(projectUrl).then(function(result) {
        if(!$scope.migratingActive && result.data) {
          if(!$scope.migratingActive){
            result.data = $scope.toolStatus(result.data);
          }
        }
        var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {site_id: projectId}));
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
        $log.info(moment().format('h:mm:ss') + ' - tools requested for project ' + $scope.sourceProjects[targetProjPos].entityTitle + ' ( site ID: s' + projectId + ')');
        $log.info(' - - - - GET /projects/' + projectId);
      });
    };
    // handler for a request for the user's Box folders
    $scope.getBoxFolders = function(projectSiteId,projectToolId) {
      $scope.currentTool = projectToolId;
      $scope.currentSite = projectSiteId;
      // get the box folder info if it has not been
      // gotten yet
      if (!$scope.boxFolders) {
        $scope.loadingFolders = true;
        var boxUrl = '/box/folders';
        Projects.getBoxFolders(boxUrl).then(function(result) {
          //sessionStorage.setItem('boxFolders',JSON.stringify(result));
          $scope.loadingFolders = false;
          $scope.boxFolders = result.data;
          $log.info(moment().format('h:mm:ss') + ' - BOX folder info requested');
          $log.info(' - - - - GET /box/folders');
        });
      }
    };
    // handler for a request for user Box account authentication/authorization
    $scope.boxAuthorize = function() {
      $log.info('---- in boxAuthorize ');
      // get the box folder info if it has not been
      // gotten yet
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
    // remove user authentication information from server memory, user need to re-authenticate in the future to access their box account
    $scope.boxUnauthorize = function() {
      var boxUrl = '/box/unauthorize';
      Projects.boxUnauthorize(boxUrl).then(function(result) {
        $scope.boxAuthorized = false;
        // void the folders in scope
        $scope.boxFolders = false;
        // reset all things in all
        // projects that depend on
        // authorization
        _.each($scope.sourceProjects,function(sourceProject) {
          if (sourceProject !== null && sourceProject !== undefined) {
            sourceProject.boxFolder = false;
            sourceProject.stateSelectionExists = false;
            sourceProject.selectDestination = false;
            sourceProject.destinationTypeSelected = false;
            sourceProject.selectDestinationType = false;
            sourceProject.selected = false;
          }
        });
        $('#boxIFrame').remove();
        $('#boxIFrameContainer').append('<iframe class="boxIFrame" id="boxIFrame" src="/box/authorize" frameborder="0"></iframe>');
        // current user un-authorize
        // the app from accessing
        // Box
        $log.info(moment().format('h:mm:ss') + ' - unauthorize from Box account requested');
        $log.info(' - - - - GET /box/unauthorize');
      });
    };
    // handler for a user's selection of a particular Box folder as a destination of a migration
    $scope.boxFolderSelect = function(folder) {
      // decorate both the tool row and the parent
      // site row with the selected folder
      var toolRow = _.findWhere($scope.sourceProjects, {tool_id: $scope.currentTool});
      var parentRow = _.findWhere($scope.sourceProjects, {site_id: $scope.currentSite,tool_id: ''});
      parentRow.boxFolder = {
        'name': folder.name,
        'id': folder.ID
      };
      toolRow.boxFolder = {
        'name': folder.name,
        'id': folder.ID
      };
      $scope.selectBoxFolder = {
        'name': folder.name,
        'id': folder.ID
      };
      $log.info('BOX Folder "' + $scope.selectBoxFolder.name + '" (ID: ' + $scope.selectBoxFolder.id + ') selected');
    };
    // handler for a user's selection of export destination type: local download or export to Box as a destination of a migration
    $scope.destinationTypeSelect = function(site_id,tool_id, name, id) {
      if (name === 'Box') {
        $scope.$evalAsync(function() {
          focus('toolSelDestBox' + tool_id);
        });
      }
      // decorate both the tool row and the parent
      // site row
      var toolRow = _.findWhere($scope.sourceProjects, {tool_id: tool_id});
      toolRow.selectDestinationType = {
        'name': name,
        'id': id
      };
      var parentRow = _.findWhere($scope.sourceProjects, {site_id: site_id,tool_id: ''});
      parentRow.selectDestinationType = {
        'name': name,
        'id': id
      };
      if (parentRow.stateSelectionExists && (parentRow.selectDestinationType.name === 'zip' || parentRow.selectDestinationType.name === 'Box' && $scope.selectBoxFolder) && !parentRow.stateExportConfirm && !parentRow.migrating) {
        $scope.$evalAsync(function() {
          focus('export' + site_id);
        });
      }
    };
    // change handler for tool checkboxes that determines if at least one is checked - if so, the export button is revealed, if not it is hidden
    $scope.checkIfSelectionExists = function(projectId,toolId) {
      var allTargetProjs = _.where($scope.sourceProjects, {site_id: projectId});
      var targetSelections = _.where($scope.sourceProjects, {site_id: projectId,selected: true});

      if (targetSelections.length) {
        $scope.$evalAsync(function() {
          focus('toolSelDest' + toolId);
        });
        _.first(allTargetProjs).stateSelectionExists = true;
      } else {
        _.first(allTargetProjs).stateSelectionExists = false;
        _.first(allTargetProjs).stateExportConfirm = false;
      }
    };
    // handler for the Export button (user has selected tools, specified dependencies and clicked on the "Export" button)
    $scope.startMigrationConfirm = function(projectId) {
      var allTargetProjs = _.where($scope.sourceProjects, {site_id: projectId});
      var targetSelections = _.where($scope.sourceProjects, {site_id: projectId,selected: true});

      // pop confirmation panel
      if (targetSelections.length) {
        _.first(allTargetProjs).stateExportConfirm = true;
        $scope.$evalAsync(function() {
          focus('confirm' + projectId);
        });
      } else {
        _.first(allTargetProjs).stateExportConfirm = false;
      }
    };
    // handler for the Cancel migration button - all states are reset for that particular project site
    $scope.cancelStartMigrationConfirm = function(projectId) {
      var allTargetProjs = _.where($scope.sourceProjects, {site_id: projectId});

      $scope.$evalAsync(function() {
        focus('project' + projectId);
      });

      var targetSelections = _.where($scope.sourceProjects, {site_id: projectId,selected: true});

      // state management
      _.first(allTargetProjs).stateExportConfirm = false;
      _.first(allTargetProjs).stateSelectionExists = false;

      // reset all checkboxes
      _.each(targetSelections, function(tool) {
        tool.selected = false;
      });
    };
    // handler for showing the details of a migrated thing
    $scope.showDetails = function(index, site_title) {
      var reportDetails = $scope.migratedProjects[index].status;
      var destination = $scope.migratedProjects[index].destination_type;
      reportDetails.title = site_title;
      // on success - add site title to status
      if (reportDetails.status.indexOf('Finished upload site content for site') !== -1) {
        reportDetails.status = 'Finished upload site content for site ' + site_title;
      }
      if (destination === 'zip') {
        reportDetails.status = 'Finished creating zip file for ' + site_title;
      }
      $scope.reportDetails = reportDetails;
    };
    $scope.checkBoxAuth = function() {
      var checkBoxAuthorizedUrl = $rootScope.urls.checkBoxAuthorizedUrl;
      Projects.checkBoxAuthorized(checkBoxAuthorizedUrl).then(function(result) {
        if (result.data === 'true') {
          $scope.boxAuthorized = true;
        } else {
          $scope.boxAuthorized = false;
        }
        $log.info(' - - - - User authorized to Box: ' + result.data);
      });
    };
    // on dismiss box auth modal, launch a function to check if box auth
    $(document).on('hidden.bs.modal','#boxAuthModal',function() {
      var appElement = $('#boxAuthModal');
      var $scope = angular.element(appElement).scope();
      $scope.$apply(function() {
        appElement.scope().checkBoxAuth();
      });
    });
    $scope.startMigrationEmail = function(project, destinationType) {

      var migrationUrl ='/migrationMailArchiveZip?site_id=' + project.site_id  + '&tool_id=' + project.tool_id  + '&site_name=' + project.site_name + '&tool_name=' + project.tool_name + '&destination_type=mailarchivezip';
      $log.info('Posting request for ' + destinationType + ' with a POST to:  ' + migrationUrl);
      ProjectsLite.startMigrationEmail(migrationUrl).then(function(result) {
        $log.warn(result);
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
              _.each(result.data.entity,function(migrated) {
                migrated.status.data = prepareReport(migrated.status.data);
              });
              result = transformMigrated(result);
              $scope.migratedProjects = _.sortBy(result.data.entity,'site_name');
              // this poll has
              // different data
              // than the last one
              if (!angular.equals($scope.migratedProjects,$scope.migratedProjectsShadow)) {
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
        targetDoNotMoveData.push('siteId=' + target.site_id+ '&toolId=' + target.tool_id);
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
                var thisSite = _.findWhere($scope.sourceProjects, {site_id: targetSite.split('=')[1]});
                thisSite.deleteStatus = {
                  'userId':'You have',
                  'consentTime': moment().valueOf()
                };
              });
            }
          }
        );
      }
      // if do not migrated tool request has items as many posts as selected tools
      if(targetDoNotMoveData.length){
        _.each(targetDoNotMoveData, function(toolNotToMigr) {
          var donotMigrateUrl = '/doNotMigrateTool?' + toolNotToMigr;
          ProjectsLite.doNotMigrateTool(donotMigrateUrl).then(
            function(result) {
              if(result.data ==='site tool delete exempt choice saved.') {
                // find this tool and add doNotMigrateStatus object to let user know
                var thisTool = _.findWhere($scope.sourceProjects, {tool_id: toolNotToMigr.split('=')[2]});
                thisTool.doNotMigrateStatus = {
                  'userId':'You have',
                  'consentTime': moment().valueOf()
                };
              }
            }
          );
        });
      }
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
            migratedMatch.push({'migratedBy':migrated.migrated_by.split(',')[0],'migratedWhen':migrated.end_time,'migratedHow':migrated.destination_type});
          }
        });
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
