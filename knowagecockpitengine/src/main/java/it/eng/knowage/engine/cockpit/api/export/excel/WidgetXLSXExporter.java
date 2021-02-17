/*
 * Knowage, Open Source Business Intelligence suite
 * Copyright (C) 2021 Engineering Ingegneria Informatica S.p.A.

 * Knowage is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Knowage is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.eng.knowage.engine.cockpit.api.export.excel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import it.eng.knowage.engine.cockpit.api.crosstable.CrossTab;
import it.eng.knowage.engine.cockpit.api.crosstable.CrosstabBuilder;
import it.eng.knowage.engine.cockpit.api.crosstable.CrosstabSerializationConstants;
import it.eng.knowage.engine.cockpit.api.crosstable.NodeComparator;
import it.eng.knowage.engine.cockpit.api.export.excel.crosstab.CrosstabXLSXExporter;
import it.eng.spagobi.utilities.json.JSONUtils;

class WidgetXLSXExporter {

	static private Logger logger = Logger.getLogger(WidgetXLSXExporter.class);

	ExcelExporter excelExporter;
	String widgetType;
	String templateString;
	String widgetId;
	Workbook wb;
	JSONObject optionsObj;

	public WidgetXLSXExporter(ExcelExporter excelExporter, String widgetType, String templateString, String widgetId, Workbook wb, JSONObject options) {
		super();
		this.excelExporter = excelExporter;
		this.widgetType = widgetType;
		this.templateString = templateString;
		this.widgetId = widgetId;
		this.wb = wb;
		this.optionsObj = options;
	}

	public void export() {
		if (widgetType.equalsIgnoreCase("static-pivot-table") && optionsObj != null) {
			exportCrossTabWidget();
		} else if (widgetType.equalsIgnoreCase("map")) {
			exportMapWidget();
		} else {
			exportGenericWidget();
		}
	}

	private void exportGenericWidget() {
		try {
			JSONObject template = new JSONObject(templateString);
			JSONObject widget = getWidgetById(template, widgetId);
			if (widget != null) {
				String widgetName = null;
				JSONObject style = widget.optJSONObject("style");
				if (style != null) {
					JSONObject title = style.optJSONObject("title");
					if (title != null) {
						widgetName = title.optString("label");
					} else {
						JSONObject content = widget.optJSONObject("content");
						if (content != null) {
							widgetName = content.getString("name");
						}
					}
				}

				JSONObject dataStore = excelExporter.getDataStoreForWidget(template, widget);
				if (dataStore != null) {
					String cockpitSheetName = getCockpitSheetName(template, widgetId);
					excelExporter.createExcelFile(dataStore, wb, widgetName, cockpitSheetName);
				}
			}
		} catch (Exception e) {
			logger.error("Unable to export widget: " + widgetId, e);
		}
	}

	private void exportCrossTabWidget() {
		try {
			JSONObject template = new JSONObject(templateString);
			JSONObject widget = getWidgetById(template, widgetId);
			if (widget != null) {
				String widgetName = null;
				JSONObject style = widget.optJSONObject("style");
				if (style != null) {
					JSONObject title = style.optJSONObject("title");
					if (title != null) {
						widgetName = title.optString("label");
					} else {
						JSONObject content = widget.optJSONObject("content");
						if (content != null) {
							widgetName = content.getString("name");
						}
					}
				}

				JSONObject crosstabDefinition = optionsObj.getJSONObject("crosstabDefinition");
				JSONArray measures = crosstabDefinition.optJSONArray("measures");
				JSONObject variables = optionsObj.optJSONObject("variables");
				Map<String, List<Threshold>> thresholdColorsMap = getThresholdColorsMap(measures);

				CrosstabXLSXExporter exporter = new CrosstabXLSXExporter(null, variables, thresholdColorsMap);

				JSONObject crosstabDefinitionJo = optionsObj.getJSONObject("crosstabDefinition");
				JSONObject crosstabDefinitionConfigJo = crosstabDefinitionJo.optJSONObject(CrosstabSerializationConstants.CONFIG);
				JSONObject crosstabStyleJo = (optionsObj.isNull("style")) ? new JSONObject() : optionsObj.getJSONObject("style");
				crosstabDefinitionConfigJo.put("style", crosstabStyleJo);

				JSONObject sortOptions = optionsObj.getJSONObject("sortOptions");

				List<Map<String, Object>> columnsSortKeys;
				List<Map<String, Object>> rowsSortKeys;
				List<Map<String, Object>> measuresSortKeys;

				// the id of the crosstab in the client configuration array
				Integer myGlobalId;
				JSONArray columnsSortKeysJo = sortOptions.optJSONArray("columnsSortKeys");
				JSONArray rowsSortKeysJo = sortOptions.optJSONArray("rowsSortKeys");
				JSONArray measuresSortKeysJo = sortOptions.optJSONArray("measuresSortKeys");
				myGlobalId = sortOptions.optInt("myGlobalId");
				columnsSortKeys = JSONUtils.toMap(columnsSortKeysJo);
				rowsSortKeys = JSONUtils.toMap(rowsSortKeysJo);
				measuresSortKeys = JSONUtils.toMap(measuresSortKeysJo);
				if (optionsObj != null) {
					logger.debug("Export cockpit crosstab optionsObj.toString(): " + optionsObj.toString());
				}

				Map<Integer, NodeComparator> columnsSortKeysMap = toComparatorMap(columnsSortKeys);
				Map<Integer, NodeComparator> rowsSortKeysMap = toComparatorMap(rowsSortKeys);
				Map<Integer, NodeComparator> measuresSortKeysMap = toComparatorMap(measuresSortKeys);
				CrosstabBuilder builder = new CrosstabBuilder(excelExporter.getLocale(), crosstabDefinition, optionsObj.getJSONArray("jsonData"),
						optionsObj.getJSONObject("metadata"), null);

				CrossTab cs = builder.getSortedCrosstabObj(columnsSortKeysMap, rowsSortKeysMap, measuresSortKeysMap, myGlobalId);

				Sheet sheet;

				String cockpitSheetName = getCockpitSheetName(template, widgetId);
				sheet = excelExporter.createUniqueSafeSheet(wb, widgetName, cockpitSheetName);

				CreationHelper createHelper = wb.getCreationHelper();

				exporter.fillAlreadyCreatedSheet(sheet, cs, createHelper, 0, excelExporter.getLocale());

			}
		} catch (Exception e) {
			logger.error("Unable to export crosstab: " + widgetId, e);
		}
	}

	private Map<String, List<Threshold>> getThresholdColorsMap(JSONArray measures) {
		Map<String, List<Threshold>> toReturn = new HashMap<String, List<Threshold>>();
		try {
			for (int i = 0; i < measures.length(); i++) {
				JSONObject measure = measures.getJSONObject(i);
				String id = measure.getString("id");
				if (!measure.has("ranges"))
					continue;
				JSONArray ranges = measure.getJSONArray("ranges");
				List<Threshold> allThresholds = new ArrayList<Threshold>();
				for (int j = 0; j < ranges.length(); j++) {
					JSONObject range = ranges.getJSONObject(j);
					String operator = range.getString("operator");
					if (!operator.equals("none")) {
						Double value = range.getDouble("value");
						String color = range.getString("background-color");
						Threshold threshold = new Threshold(operator, value, color);
						allThresholds.add(threshold);
					}
				}
				toReturn.put(id, allThresholds);
			}
		} catch (Exception e) {
			logger.error("Unable to build threshold color map", e);
			Map<String, List<Threshold>> emptyMap = new HashMap<String, List<Threshold>>();
			return emptyMap;
		}
		return toReturn;
	}

	private Map<Integer, NodeComparator> toComparatorMap(List<Map<String, Object>> sortKeyMap) {
		Map<Integer, NodeComparator> sortKeys = new HashMap<Integer, NodeComparator>();

		for (int s = 0; s < sortKeyMap.size(); s++) {
			Map<String, Object> sMap = sortKeyMap.get(s);
			NodeComparator nc = new NodeComparator();

			nc.setParentValue((String) sMap.get("parentValue"));
			nc.setMeasureLabel((String) sMap.get("measureLabel"));
			if (sMap.get("direction") != null) {
				nc.setDirection(Integer.valueOf(sMap.get("direction").toString()));
				sortKeys.put(Integer.valueOf(sMap.get("column").toString()), nc);
			}
		}
		return sortKeys;
	}

	private void exportMapWidget() {
		try {
			JSONObject template = new JSONObject(templateString);
			JSONObject widget = getWidgetById(template, widgetId);
			if (widget != null) {
				String widgetName = null;
				JSONObject style = widget.optJSONObject("style");
				if (style != null) {
					JSONObject title = style.optJSONObject("title");
					if (title != null) {
						widgetName = title.optString("label");
					} else {
						JSONObject content = widget.optJSONObject("content");
						if (content != null) {
							widgetName = content.getString("name");
						}
					}
				}

				JSONArray dataStoreArray = excelExporter.getMultiDataStoreForWidget(template, widget);
				for (int i = 0; i < dataStoreArray.length(); i++) {
					JSONObject dataStore = dataStoreArray.getJSONObject(i);
					if (dataStore != null) {
						String cockpitSheetName = getCockpitSheetName(template, widgetId) + String.valueOf(i);
						excelExporter.createExcelFile(dataStore, wb, widgetName, cockpitSheetName);
					}
				}
			}
		} catch (Exception e) {
			logger.error("Unable to export map widget: " + widgetId, e);
		}
	}

	private String getCockpitSheetName(JSONObject template, String widgetId) {
		try {
			JSONArray sheets = template.getJSONArray("sheets");
			if (sheets.length() == 1)
				return "";
			for (int i = 0; i < sheets.length(); i++) {
				JSONObject sheet = sheets.getJSONObject(i);
				JSONArray widgets = sheet.getJSONArray("widgets");
				for (int j = 0; j < widgets.length(); j++) {
					JSONObject widget = widgets.getJSONObject(j);
					if (widgetId.equals(widget.getString("id")))
						return sheet.getString("label");
				}
			}
			return "";
		} catch (Exception e) {
			logger.error("Unable to retrieve cockpit sheet name from template", e);
			return "";
		}
	}

	private JSONObject getWidgetById(JSONObject template, String widgetId) {
		try {
			long widget_id = Long.parseLong(widgetId);

			JSONArray sheets = template.getJSONArray("sheets");
			for (int i = 0; i < sheets.length(); i++) {
				JSONObject sheet = sheets.getJSONObject(i);
				JSONArray widgets = sheet.getJSONArray("widgets");
				for (int j = 0; j < widgets.length(); j++) {
					JSONObject widget = widgets.getJSONObject(j);
					long id = widget.getLong("id");
					if (id == widget_id) {
						return widget;
					}
				}
			}
		} catch (JSONException e) {
			logger.error("Unable to get widget", e);
		}
		return null;
	}

}
