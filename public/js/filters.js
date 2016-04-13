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
}).filter('fixMyWspId', function() {
  return function( id) {
    return id.replace('~','');
  }
}).filter('linkToSite', function() {
  return function( url) {
    return url.replace('ctqasearch','ctqa').replace('ctdevsearch','ctdev').replace('ctsearch.vip.itd','ctools').replace('/direct','/portal')
  }
});