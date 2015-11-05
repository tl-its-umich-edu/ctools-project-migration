'use strict';
/* global  $, _, console */


var categorizeBySite = function (data){
	var siteColl = [];
	$.each(data.data, function(i, item){
  	siteColl.push(item.site_id);
	});
	siteColl = _.uniq(siteColl);
	var siteCollTools =[];

	$.each(siteColl, function(i, item){

  	var thisColl = _.where(data.data, {site_id: item});
    if(thisColl.length){ 
    	var newObj = {};
    	newObj.site_id = item;
  		newObj.site_name = thisColl[0].site_name;
  		newObj.tools = [];
    	$.each(thisColl, function(i, item){  	
    		var thisTool = {};
    		thisTool.tool_id = item.tool_id;
    		thisTool.tool_name= item.tool_name;
        thisTool.migration_id = item.migration_id;
        thisTool.start = item.start;
        thisTool.end = item.end;
        thisTool.migrated_by = item.migrated_by;
        thisTool.destination_type = item.destination_type;
        thisTool.destination_url = item.destination_url;
        newObj.tools.push(thisTool);
    	});		
    	siteCollTools.push(newObj);
    }  
	});
	data.data = siteCollTools;
	return data;
};


var transformProjects = function (data){
  var projectsColl = [];

  $.each(data.data.site_collection, function(i, item){
    var projObj = {};
    projObj.migration_id= '',
    projObj.site_id= item.entityId,
    projObj.site_name= item.entityTitle,
    projObj.tool_name= '',
    projObj.tool_id= '',
    projObj.migrated_by= '',
    projObj.start_time= '',
    projObj.end_time='',
    projObj.destination_type= '',
    projObj.destination_url= ''
    projectsColl.push(projObj);
  });
  data.data = projectsColl;
  return data;
}

var transformProject = function (data){
  var toolColl = [];
    var siteId = data.data[0].tools[0].siteId;
    var siteName = $('#' + siteId).text();


  $.each(data.data, function(i, item){
    // need to make this tool filtering more visible & maintainable
    // maybe put it in app.js
    var toolObj = {};
    
    if (item.tools.length ===1 && (item.tools[0].toolId === 'sakai.resources')) {
      toolObj.migration_id= '',
      toolObj.site_id= siteId,
      toolObj.site_name= siteName,
      toolObj.tool_name= 'Resources',
      toolObj.tool_type= item.tools[0].toolId,
      toolObj.tool_id= item.tools[0].id,
      toolObj.migrated_by= '',
      toolObj.start_time= '',
      toolObj.end_time='',
      toolObj.destination_type= '',
      toolObj.destination_url= ''
      toolColl.push(toolObj);
    }

  });

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
