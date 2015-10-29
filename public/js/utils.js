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