'use strict';
/* global  $, _, console */

var closeBoxAuthModal = function(){
  $('#boxAuthModal').modal('hide');
};

/*
/projects returns a Sakai like /direct feed
utility below turns it into an array of objects with the same structure
as the CPM feeds of /migrating and /migrations for ease of comparing the three
*/
var transformProjects = function (siteList){
  var projectsColl = [];
  $.each(siteList, function(i, item){
    var projObj = {};
    projObj.migration_id= '',
    projObj.site_id= item.entityId,
    projObj.site_name= item.entityTitle,
    projObj.tool_name= '',
    projObj.tool_id= '',
    projObj.migrated_by= '',
    projObj.url= item.entityURL,
    projObj.start_time= '',
    projObj.end_time='',
    projObj.destination_type= '',
    projObj.destination_url= '',
    projObj.tool_site_id= item.entityId;
    projObj.type= item.type;
    projObj.type_code = getTypeCode(item.type);
    projectsColl.push(projObj);
  });
    return projectsColl;
}

var getTypeCode = function(type){
  if (type==='myworkspace'){
    return 1;
  }
  else if (type==='GradToolsRackham' || 'GradToolsDepartment'){
    return 2;
  } else {
    return 3;
  }
}

/*
user has requested the tools of a given project. Returned json is a Sakai like /direct feed
utility below turns it into an array of objects with the same structure
as the CPM feeds of /migrating and /migrations for ease of comparing the three

*/var transformProject = function (data){
  var toolColl = [];
    var siteId = data.data[0].tools[0].siteId;
    var siteName = $('#' + siteId.replace('~','')).text();
    //console.log(siteName)

  $.each(data.data, function(i, item){
    /*need to make this tool filtering more visible & maintainable
    maybe put it in app.js*/
    var toolObj = {};

    var tool_name;
      if(item.tools[0].toolId === 'sakai.resources'){
        tool_name ='Resources';
      }
     else {
       tool_name ='Email Archive';
     }


    if (item.tools.length ===1 && (item.tools[0].toolId === 'sakai.resources' || item.tools[0].toolId === 'sakai.mailbox' )) {
      toolObj.migration_id= '',
      toolObj.site_id= siteId,
      toolObj.site_name= siteName,
      toolObj.tool_name= tool_name,
      toolObj.tool_type= item.tools[0].toolId,
      toolObj.tool_id= item.tools[0].id,
      toolObj.migrated_by= '',
      toolObj.start_time= '',
      toolObj.end_time='',
      toolObj.destination_type= '',
      toolObj.destination_url= '',
      toolObj.tool= true,
      toolObj.tool_site_id= siteId + item.tools[0].id,
      toolObj.hasContentItem = item.hasContentItem,
      toolColl.push(toolObj);
    }

  });
/*
  a request for the tools of a project might return no
  exportable tools. This adds a dummy object to the empty array
  so that we can display a helpful message
  */
  if (!toolColl.length){
    var notoolObj = {};
    notoolObj.migration_id= '',
    notoolObj.site_id= siteId,
    notoolObj.site_name= siteName,
    notoolObj.tool_name= 'No exportable tools found.',
    notoolObj.tool_id= 'notools',
    notoolObj.migrated_by= '',
    notoolObj.start_time= '',
    notoolObj.end_time='',
    notoolObj.destination_type= '',
    notoolObj.destination_url= ''
    toolColl.push(notoolObj);
  }
  data.data = toolColl;
  return data;
}
/*
/projects returns a Sakai like /direct feed
utility below turns it into an array of objects with the same structure
as the CPM feeds of /migrating and /migrations for ease of comparing the three
*/
var transformMigrations = function (data){
  $.each(data.data.entity, function(i, item){
    item.tool_site_id= item.site_id + item.tool_id;
  });
  return data;
}

var prepareReport = function (data){
  var fails = [];
  var succs = [];
  // remove the site folder
  var data = data.slice(1, data.length)
  $.each(data, function(i, item){
    if(item.status.indexOf('success') === -1 && item.status.indexOf('created') === -1 && item.status !=='') {
      item.status_code="fail";
      fails.push('fail');
    }
    else {
      item.status_code="success";
      succs.push('suc');
    }
  });
  return {'counts': {'successes': succs.length, 'failures':fails.length}, 'items': data};
};

var transformMigrated = function(result) {
  _.each(result.data.entity, function(migrated){
    migrated.migrated_by = migrated.migrated_by.split(',')[0];
    if (migrated.destination_url){
      var destination_urlArr = migrated.destination_url.split('/');
      if(destination_urlArr.length > 7) {
        migrated.destination_folder = _.last(destination_urlArr);
        migrated.destination_url = _.initial(destination_urlArr).join('/');
      }
      else {
        migrated.destination_folder =null;
      }
    }
  });
  return result;
};

var errorDisplay = function(url, status, message){
  alert('Asked for: ' + url + '\n\nGot a: ' + status +'\n\nSo: ' + message);
};
