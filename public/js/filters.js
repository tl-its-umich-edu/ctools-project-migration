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
  };
}).filter('linkToSite', function() {
  return function( url) {
    return url.replace('ctqasearch','ctqa').replace('ctdevsearch','ctdev').replace('ctsearch.vip.itd','ctools').replace('/direct','/portal');
  };
}).filter('destinationLinkName', function() {
    return function( url) {
      if(url){
        return _.last(url.split('/'));
      }
    };
}).filter('whatStatus', function() {
    return function(status) {
      if(status){
        if(status.indexOf('success') !==-1 || status.indexOf0('created') !==-1){
            return "glyphicon glyphicon-ok";
        }
        else {
            return "glyphicon glyphicon-fire";
        }
      }
    };

}).filter('extractMember', function() {
    return function(member) {
      if(member){
        var realMember = member.split(' ')[0] + ' '  +member.split(' ')[1];
        return realMember;
      }

    };
}).filter('filterMemberError', function() {
    return function(input) {
      if(input){
        var extract = input.split('role=')[1].split('to Box folder id')[0];
        return extract;
      }
    };
}).filter('readableExportType', function() {
  return function(type) {
    return ({
      'google': 'Migration to Google Groups',
      'mailarchivezip':'Zip Download of CTools Email',
      'mailarchivembox':'Zipped .mbox format download of CTools Email',
      'resource zip':'Zipped download of CTools Resources',
      'box':'Migration to Box'
    }[String(type)] || 'Unknown Migration Type');
  };
}).filter('readableExportTypeSmall', function() {
  return function(type) {
    return ({
      'google': 'to Google Groups',
      'mailarchivezip':'as a Zip file',
      'mailarchivembox':'as a Zip file in .mbox format',
      'resource zip':'as a Zip file',
      'zip':'as a Zip file',
      'box':'to Box'
    }[String(type)] || 'Unknown Migration Type');
  };
});
