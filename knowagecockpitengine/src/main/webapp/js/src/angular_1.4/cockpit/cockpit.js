/*
Knowage, Open Source Business Intelligence suite
Copyright (C) 2016 Engineering Ingegneria Informatica S.p.A.

Knowage is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

Knowage is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

var scripts = document.getElementsByTagName("script");
var baseScriptPath  = scripts[scripts.length - 1].src;
baseScriptPath =baseScriptPath .substring(0, baseScriptPath .lastIndexOf('/'));

(function() {
	agGrid.initialiseAgGridWithAngular1(angular);
var cockpitApp= angular.module("cockpitModule",[
	'ngMaterial',
	'ngSanitize',
	'cometd',
	'sbiModule',
	'knModule',
	'gridster',
	'file_upload',
	'ngWYSIWYG',
	'angular_table',
	'cockpit_angular_table',
	'color.picker',
	'dndLists',
	'chartRendererModule',
	'accessible_angular_table',
	'cockpitTable',
	'jsonFormatter',
	'ui.codemirror',
	'knModule',
	'agGrid'
	]);
cockpitApp.config(['$mdThemingProvider','$mdGestureProvider','$compileProvider', function($mdThemingProvider,$mdGestureProvider,$compileProvider) {
    $mdThemingProvider.theme('knowage')
    $mdThemingProvider.setDefaultTheme('knowage');
    $mdGestureProvider.skipClickHijack();
    $compileProvider.debugInfoEnabled(false);
}]);


cockpitApp.controller("cockpitMasterControllerWrapper",
		['$scope',
		'$controller',
		'sbiModule_i18n',
		'cockpitModule_widgetServices',
		'cockpitModule_template',
		'cockpitModule_datasetServices',
		'cockpitModule_documentServices',
		'cockpitModule_crossServices',
		'cockpitModule_nearRealtimeServices',
		'cockpitModule_realtimeServices',
		'cockpitModule_properties',
		'cockpitModule_templateServices',
		'$rootScope',
		'$q',
		'sbiModule_device',
		'accessibility_preferences',
		cockpitMasterControllerWrapper]);
function cockpitMasterControllerWrapper(
		$scope,
		$controller,
		sbiModule_i18n,
		cockpitModule_widgetServices,
		cockpitModule_template,
		cockpitModule_datasetServices,
		cockpitModule_documentServices,
		cockpitModule_crossServices,
		cockpitModule_nearRealtimeServices,
		cockpitModule_realtimeServices,
		cockpitModule_properties,
		cockpitModule_templateServices,
		$rootScope,
		$q,
		sbiModule_device,
		accessibility_preferences){
	// when sbiModule_i18n is initialized (i.e. i18n messages are loaded), the cockpitMasterController can start
	sbiModule_i18n.loadI18nMap().then(function() {
      $controller('cockpitMasterController', {
        $scope: $scope, //passing the same scope on through
        cockpitModule_widgetServices: cockpitModule_widgetServices,
        cockpitModule_template: cockpitModule_template,
		cockpitModule_datasetServices: cockpitModule_datasetServices,
		cockpitModule_documentServices: cockpitModule_documentServices,
		cockpitModule_crossServices: cockpitModule_crossServices,
		cockpitModule_nearRealtimeServices: cockpitModule_nearRealtimeServices,
		cockpitModule_realtimeServices: cockpitModule_realtimeServices,
		cockpitModule_properties: cockpitModule_properties,
		cockpitModule_templateServices: cockpitModule_templateServices,
		$rootScope: $rootScope,
		$q: $q,
		sbiModule_device: sbiModule_device,
		accessibility_preferences: accessibility_preferences
      });
    });
}




cockpitApp.controller("cockpitMasterController",['$scope','cockpitModule_widgetServices','cockpitModule_template','cockpitModule_datasetServices','cockpitModule_documentServices','cockpitModule_crossServices','cockpitModule_nearRealtimeServices','cockpitModule_realtimeServices','cockpitModule_properties','cockpitModule_templateServices','$rootScope','$q','sbiModule_device','accessibility_preferences',cockpitMasterControllerFunction]);
function cockpitMasterControllerFunction($scope,cockpitModule_widgetServices,cockpitModule_template,cockpitModule_datasetServices,cockpitModule_documentServices,cockpitModule_crossServices,cockpitModule_nearRealtimeServices,cockpitModule_realtimeServices,cockpitModule_properties,cockpitModule_templateServices,$rootScope,$q,sbiModule_device,accessibility_preferences){
	$scope.cockpitModule_widgetServices=cockpitModule_widgetServices;
	$scope.imageBackgroundUrl=cockpitModule_template.configuration.style.imageBackgroundUrl;
	$scope.cockpitModule_template=cockpitModule_template;
	$scope.sbiModule_device=sbiModule_device;

	$scope.initializedSheets = [0]; // first sheet is always loaded

	var initSheet = $scope.$watch('cockpitModule_properties.CURRENT_SHEET',function(newValue,oldValue){
        if(newValue!=undefined && $scope.initializedSheets.indexOf(newValue) == -1){

        	var currentSheet; // get sheet checking proper index
        	for(var i=0; i < cockpitModule_template.sheets.length; i++){
        		if(cockpitModule_template.sheets[i].index == newValue){
        			currentSheet = cockpitModule_template.sheets[i];
        			break;
        		}
        	}

        	for(var i=0; i < currentSheet.widgets.length; i++){
        		var widgetId = currentSheet.widgets[i].id;
            	var tempElement = angular.element(document.querySelector('#w' + widgetId));
            	$rootScope.$broadcast("WIDGET_EVENT" + widgetId, "INIT", {element:tempElement});
        	}

        	$scope.initializedSheets.push(newValue);
        }

        if($scope.initializedSheets.length == cockpitModule_template.sheets.length){
        	initSheet();
        }
    })

	//load the dataset list
	$scope.datasetLoaded=false;

	cockpitModule_datasetServices.loadDatasetsFromTemplate().then(function(){
		$scope.datasetLoaded=true;
		var dsNotInCache = cockpitModule_templateServices.getDatasetAssociatedNotUsedByWidget();
		if(dsNotInCache.length>0){
			cockpitModule_datasetServices.addDatasetInCache(dsNotInCache)
			.then(function(){
				$rootScope.$broadcast("WIDGET_INITIALIZED");
			});
			//WIDGET_INITIALIZED at the end
		}else{
			$rootScope.$broadcast("WIDGET_INITIALIZED");
		}

		if(!cockpitModule_properties.EDIT_MODE){
			cockpitModule_nearRealtimeServices.init();
			cockpitModule_realtimeServices.init();
		}
	},function(){
		console.error("error when load dataset list")
	});

	cockpitModule_documentServices.loadDocumentsFromTemplate();

	if(cockpitModule_properties.DOCUMENT_LABEL != undefined && cockpitModule_properties.DOCUMENT_LABEL != ''){
		cockpitModule_crossServices.loadCrossNavigationByDocument(cockpitModule_properties.DOCUMENT_LABEL).then(
			function(){},
			function(){
				console.error("error when load cross list")
			});
	}

	$scope.exportCsv=function(deferred){
		var finalCsvDataObj = {};
		var csvDataCount = 0;
		var allWidgets = cockpitModule_widgetServices.getAllWidgets();
		var widgets = []
		for (w in allWidgets){
			var obj = allWidgets[w];
			if(obj && obj.dataset && obj.dataset.dsId != undefined){
				widgets.push(obj);
			}
		}
		allWidgets = null;
		var successCount = 0;
		for (w in widgets){
			var def=$q.defer();
	    	$rootScope.$broadcast("WIDGET_EVENT"+widgets[w].id,"EXPORT_CSV",{def:def,csvDataCount:csvDataCount});
	    	def.promise.then(function(data){
	    		if(data != undefined){
	    			finalCsvDataObj[data.csvDataCount] = btoa(data.csvData);
	    		}
	    		successCount++;
	    		if(successCount == widgets.length){
	    			var finalCsvData = '';
	    			for(c in finalCsvDataObj){
	    				finalCsvData += finalCsvDataObj[c] + ',';
	    			}
	    			deferred.resolve(finalCsvData);
	    		}
	    	},function(error){
	    		console.error('Error exporting data for widget');
	    		deferred.reject(error);
	    	});
	    	csvDataCount++;
	    }
		return deferred.promise;
	}
}

})();