'use strict';
/* global  projectMigrationApp, _*/

/* TERMS CONTROLLER */
projectMigrationApp.controller('projectMigrationController', ['Projects', '$rootScope', '$scope', '$log', function (Projects, $rootScope, $scope, $log) {

  $scope.sourceProjects = []; 
  $scope.migratingProjects = []; 
  $scope.migratedProjects = [];
  $rootScope.logs = [];

  //server url
  //var projectsUrl ='/direct/site/withPerm/.json?permission=site.upd';
  // test data
  var projectsUrl ='data/projects.json';
  Projects.getProjects(projectsUrl).then(function (result) {
    $scope.sourceProjects = _.where(result.data.site_collection, {type: 'project'});
    $rootScope.logs.push(moment().format('h:mm:ss') + ' - source projects loaded');
    $rootScope.logs.push(' - - - - - - GET /projects');
    $log.info(moment().format('h:mm:ss') + ' - source projects loaded');
    $log.info(' - - - - GET /projects');
  });

  //server url
  //var migrationsUrl ='';
  // test data
  var migrationsUrl ='data/migrations.json';
  Projects.getProjects(migrationsUrl).then(function (result) {
    $scope.migratingProjects = result.data;
    $rootScope.logs.push(moment().format('h:mm:ss') + ' - migrating projects loaded');
    $rootScope.logs.push(' - - - - - - GET /migrating');
    $log.info(moment().format('h:mm:ss') + ' - migrating projects loaded');
    $log.info(' - - - - GET /migrating');

  });

  //server url
  //var migratedUrl ='';
  // test data
  var migratedUrl ='data/migrated.json';
  Projects.getProjects(migratedUrl).then(function (result) {
    $scope.migratedProjects = result.data;
    $rootScope.logs.push(moment().format('h:mm:ss') + ' - migrated projects loaded')
    $rootScope.logs.push(' - - - - - - GET /migrated');
    $log.info(moment().format('h:mm:ss') + ' - migrated projects loaded')
    $log.info(' - - - - GET /migrated');

  });

  $scope.getTools = function(projectId) {
    // server url
    //var projectUrl = '/direct/site/' + siteId + '/pages.json';
    // test data
    var projectUrl = 'data/project_id.json';
    
    Projects.getProject(projectUrl).then(function (result) {
      var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {entityId: projectId}));
      $scope.sourceProjects[targetProjPos].tools = result.data;
      $rootScope.logs.push(moment().format('h:mm:ss') + ' - tools requested for project ' + $scope.sourceProjects[targetProjPos].entityTitle  + ' ( site ID: s' + projectId + ')');
      $rootScope.logs.push(' - - - - - - GET /projects/' + projectId);
      $log.info(moment().format('h:mm:ss') + ' - tools requested for project ' + $scope.sourceProjects[targetProjPos].entityTitle  + ' ( site ID: s' + projectId + ')');
      $log.info(' - - - - GET /projects/' + projectId);
    });
  };

  $scope.checkIfSelectionExists = function(projectId) {
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {entityId: projectId}));
    $scope.sourceProjects[targetProjPos].selectionExists = false;
    _.each($scope.sourceProjects[targetProjPos].tools, function(tool){
      if(tool.selected) {
        $scope.sourceProjects[targetProjPos].selectionExists = true;
      }
    });
  };

  $scope.startMigrationConfirm = function(projectId) {
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {entityId: projectId}));
      $scope.sourceProjects[targetProjPos].exportConfirm = true;
    // pop confirmation panel
  };

  $scope.cancelStartMigrationConfirm = function(projectId) {
    // close confirmation panel and reset things
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {entityId: projectId}));
    $scope.sourceProjects[targetProjPos].exportConfirm = false;
    _.each($scope.sourceProjects[targetProjPos].tools, function(tool){
      tool.selected = false;
    });
    $scope.sourceProjects[targetProjPos].selectionExists = false;
  };
 
  $scope.startMigration = function(projectId) {
    $rootScope.addProjectSites.push(projectId);
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {entityId: projectId}));
    $rootScope.logs.push(moment().format('h:mm:ss') + ' - project migration started for ' + $scope.sourceProjects[targetProjPos].entityTitle  + ' ( site ID: s' + projectId + ')');
    $rootScope.logs.push(' - - - - - - POST /migrate/' + projectId);
    $log.info(moment().format('h:mm:ss') + ' - project migration started for ' + $scope.sourceProjects[targetProjPos].entityTitle  + ' ( site ID: s' + projectId + ')');
    $log.info(' - - - - POST /migrate/' + projectId);
    //1. POST to /migration/projectId
    // TODO: need factory
    //2. adjust UI for this this project
    $scope.sourceProjects[targetProjPos].migrated = {status:'Migration in progress', dateStarted: moment().format('MM/D/YY, h:mm:ss a')};
    //3. poll /migrations
    var migrationsUrl ='data/migrations.json';
    Projects.getProjects(migrationsUrl).then(function (result) {
      $scope.migratingProjects = result.data;
      $rootScope.logs.push(moment().format('h:mm:ss') + ' - request current migrations to update UI');
      $rootScope.logs.push(' - - - - - - GET /migrating');
      $log.info(moment().format('h:mm:ss') + ' - request current migrations to update UI');
      $log.info(' - - - - GET /migrating');

    });

  };


}]);
