/* SpagoBI, the Open Source Business Intelligence suite

 * Copyright (C) 2012 Engineering Ingegneria Informatica S.p.A. - SpagoBI Competency Center
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0, without the "Incompatible With Secondary Licenses" notice.
 * If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package it.eng.spagobi.commons.serializer;

import it.eng.spagobi.api.CacheResource;
import it.eng.spagobi.federateddataset.bo.FederatedDataset;

import java.util.Locale;

import org.json.JSONObject;

public class FederatedDatasetJSONSerializer implements Serializer {

	public static final String ID = "id";
	public static final String LABEL = "label";
	public static final String NAME = "name";
	public static final String DATA_SOURCE_LABEL = "data_source_label";
	public static final String DESCRIPTION = "description";
	public static final String CATEGORY = "category";
	public static final String LOCKED = "locked";
	public static final String LOCKER = "locker";
	public static final String RELATIONSHIPS = "relationships";
	public static final String TYPE = "type";
	public static final String CACHE_DATA_SOURCE = "cache_data_source";

	public Object serialize(Object o, Locale locale) throws SerializationException {
		JSONObject result = null;

		if (!(o instanceof FederatedDataset)) {
			throw new SerializationException("FederatedDatasetJSONSerializer is unable to serialize object of type: " + o.getClass().getName());
		}

		try {
			FederatedDataset fd = (FederatedDataset) o;
			result = new JSONObject();
			result.put(ID, fd.getId_sbi_federated_data_set());
			result.put(LABEL, fd.getLabel());
			result.put(NAME, fd.getName());
			result.put(DESCRIPTION, fd.getDescription());
			result.put(RELATIONSHIPS, fd.getRelationships());
			result.put(TYPE, "FEDERATED_DATASET");

			String cacheDataSource = new CacheResource().getCacheDataSource();
			if (cacheDataSource != null) {
				result.put(CACHE_DATA_SOURCE, cacheDataSource);
			}

		} catch (Throwable t) {
			throw new SerializationException("An error occurred while serializing object: " + o, t);
		} finally {

		}

		return result;
	}

}
