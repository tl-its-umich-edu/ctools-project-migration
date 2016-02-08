'use strict';
/* jshint  strict: true*/
/* global angular, _ */

//mot used - but keep for reference
angular.module('projectMigrationFilters', []).filter('getExtension', function() {
   return function( filename) {
    var extension = '';
    if(filename.indexOf('.') !== -1){
      return _.last(filename.split('.'));
    } else {
      return '';
    }
  };
});