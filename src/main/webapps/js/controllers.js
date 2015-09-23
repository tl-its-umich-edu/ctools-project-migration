'use strict';
/* global  projectMigrationApp, _*/

/* TERMS CONTROLLER */
projectMigrationApp.controller('sourceProjectsController', ['Projects', '$rootScope', '$scope', '$http', '$log', function (Projects, $rootScope, $scope, $http, $log) {

  $scope.selectedProjects = []; 
  $scope.completedProjects = [];


  //$log.info(localStorage.getItem('PMCompleted'))

  
  if (localStorage.getItem('PMCompleted')){
    $scope.completedProjects = eval(localStorage.getItem('PMCompleted'));
  }
  else {
   $scope.completedProjects = []; 
  }


  //server url
  var projectsUrl ='/direct/site/withPerm/.json?permission=site.upd';
  // test data
  //var projectsUrl ='data/all-sites.json';
    Projects.getProjects(projectsUrl).then(function (result) {
      $scope.sourceProjects = _.where(result.data.site_collection, {type: 'project'});
      $http({
          method : 'GET',
          url : 'ProjectMigration'
	  }).success(function(data, status, headers, config) {
	          $scope.folders = data;
	  }).error(function(data, status, headers, config) {
	          // called asynchronously if an error occurs
	          // or server returns response with an error status.\
	  });
  });

  $scope.getTools = function(siteId) {
    // server url
     var projectUrl = '/direct/site/' + siteId + '/pages.json';
    // test data
    var projectUrl = 'data/project.json';
    
    Projects.getProject(projectUrl).then(function (result) {
      var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {entityId: siteId}));
      $scope.sourceProjects[targetProjPos].tools = result.data;
    });
  };

  $scope.checkIfSelectionExists = function(siteId) {
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {entityId: siteId}));
    $scope.sourceProjects[targetProjPos].selectionExists = false;
    _.each($scope.sourceProjects[targetProjPos].tools, function(tool, index){
      if(tool.selected) {
        $scope.sourceProjects[targetProjPos].selectionExists = true;
      }
    });
  };

  $scope.addToMigrationPanel = function(projectId) {
    $rootScope.addProjectSites.push(projectId);
    var targetProjPos = $scope.sourceProjects.indexOf(_.findWhere($scope.sourceProjects, {entityId: projectId}));
    $scope.sourceProjects[targetProjPos].migrated = {status:'Migration in progress', dateStarted: moment().format('MM/D/YY, h:mm:ss a')};
    //var thisProject = _.findWhere($scope.projects,  {entityId: projectId});
    //$log.info(thisProject.tools)
    
    $scope.selectedProjects.push($scope.sourceProjects[targetProjPos]);
    localStorage.setItem('PMCompleted', JSON.stringify($scope.completedProjects.concat($scope.sourceProjects[targetProjPos]))); 
  }


}]);
