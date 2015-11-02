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
	});
	data.data = siteCollTools;
	return data;
};


var transformProjects = function (data){
  var projectsColl = [];

  $.each(data.data.site_collection, function(i, item){
    var newObj = {};
      newObj.migration_id= '',
      newObj.site_id= item.entityId,
      newObj.site_name= item.entityTitle,
      newObj.tool_name= '',
      newObj.tool_id= '',
      newObj.migrated_by= '',
      newObj.start_time= '',
      newObj.end_time='',
      newObj.destination_type= '',
      newObj.destination_url= ''
    projectsColl.push(newObj);
  });
  data.data = projectsColl;
  return data;
}

var transformProject = function (data){
  var toolColl = [];

  $.each(data.data, function(i, item){
    var siteId = item.siteId;

    var siteName = $('#' + siteId).text();
    // need to make this tool filtering more visible & maintainable
    // maybe put it in app.js
    if (item.tools.length ===1 && (item.tools[0].toolId === 'sakai.resources')) {
      var newObj = {};
      newObj.migration_id= '',
      newObj.site_id= siteId,
      newObj.site_name= siteName,
      newObj.tool_name= 'Resources',
      newObj.tool_id= item.tools[0].id,
      newObj.migrated_by= '',
      newObj.start_time= '',
      newObj.end_time='',
      newObj.destination_type= '',
      newObj.destination_url= ''
      toolColl.push(newObj);
    }
  });
  data.data = toolColl;
  return data;
}
