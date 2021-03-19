/*
 * Knowage, Open Source Business Intelligence suite
 * Copyright (C) 2016 Engineering Ingegneria Informatica S.p.A.

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
package it.eng.spagobi.api.v2;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import it.eng.spagobi.api.v2.export.Entry;
import it.eng.spagobi.api.v2.export.ExportDeleteOldJob;
import it.eng.spagobi.api.v2.export.ExportJobBuilder;
import it.eng.spagobi.api.v2.export.ExportMetadata;
import it.eng.spagobi.api.v2.export.ExportPathBuilder;
import it.eng.spagobi.api.v2.export.cockpit.CockpitDataExportJobBuilder;
import it.eng.spagobi.api.v2.export.cockpit.DocumentExportConf;
import it.eng.spagobi.commons.bo.UserProfile;
import it.eng.spagobi.commons.dao.DAOFactory;
import it.eng.spagobi.commons.utilities.SpagoBIUtilities;
import it.eng.spagobi.tools.dataset.bo.IDataSet;
import it.eng.spagobi.tools.dataset.dao.IDataSetDAO;
import it.eng.spagobi.tools.dataset.resource.export.Utilities;
import it.eng.spagobi.tools.dataset.utils.DataSetUtilities;
import it.eng.spagobi.user.UserProfileManager;
import it.eng.spagobi.utilities.exceptions.SpagoBIRuntimeException;

/**
 * Manage entity exported to file.
 *
 * @author Marco Libanori
 */
@Path("/2.0/export")
public class ExportResource {

	private static final String BODY_ATTR_PARAMETERS = "parameters";

	private static final String BODY_ATTR_DRIVERS = "drivers";

	private static final Logger logger = Logger.getLogger(ExportResource.class);

	@Context
	protected HttpServletRequest request;

	@Context
	protected HttpServletResponse response;

	/**
	 * List all exported files of a specific user.
	 *
	 * An {@link Entry} is generated by any directory that respect following condtions:
	 * <ul>
	 * <li>Contains a file with name metadata</li>
	 * <li>Contains a file with name data</li>
	 * <li>Contains an optional file with name downloaded</li>
	 * </ul>
	 *
	 * @return List of {@link Entry} with files exported by logged user
	 * @throws IOException In case of errors during access of the filesystem
	 */
	@GET
	@Path("/dataset")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Entry> dataset(@DefaultValue("false") @QueryParam("showAll") boolean showAll) throws IOException {

		logger.debug("IN");
		Utilities exportResourceUtilities = new Utilities();

		List<Entry> ret = exportResourceUtilities.getAllExportedFiles(showAll);

		logger.debug("OUT");

		return ret;
	}

	/**
	 * Schedules an export in CSV format of the dataset in input.
	 *
	 * @param dataSetId Id of the dataset to be exported
	 * @param body      JSON that contains drivers and parameters data
	 * @return The job id
	 */
	@POST
	@Path("/dataset/{dataSetId}/csv")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	public Response datasetAsCsv(@PathParam("dataSetId") Integer dataSetId, String body) {

		logger.debug("IN - Exporting dataset " + String.valueOf(dataSetId) + " in CSV");

		JSONObject driversJson = null;
		JSONArray paramsJson = null;

		try {
			JSONObject data = new JSONObject(body);
			driversJson = data.has(BODY_ATTR_DRIVERS) ? data.getJSONObject(BODY_ATTR_DRIVERS) : null;
			paramsJson = data.has(BODY_ATTR_PARAMETERS) ? data.getJSONArray(BODY_ATTR_PARAMETERS) : null;
		} catch (JSONException e) {
			String msg = String.format("Body data is invalid: %s", body);
			logger.error(msg, e);
			return Response.serverError().build();
		}

		Response ret = null;
		Locale locale = request.getLocale();
		UserProfile userProfile = UserProfileManager.getProfile();
		IDataSetDAO dsDAO = DAOFactory.getDataSetDAO();
		IDataSet dataSet = dsDAO.loadDataSetById(dataSetId);
		JSONObject jsonObject;
		try {
			jsonObject = DataSetUtilities.parametersJSONArray2JSONObject(dataSet, paramsJson);
		} catch (Exception e) {
			throw new SpagoBIRuntimeException("Error while getting parameters for dataset", e);
		}
		Map<String, String> params = DataSetUtilities.getParametersMap(jsonObject);
		Map<String, Object> drivers = DataSetUtilities.getDriversMap(driversJson);

		try {
			Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

			JobDetail exportJob = ExportJobBuilder.fromDataSetIdAndUserProfile(dataSetId, userProfile).withTypeOfCsv().withDrivers(drivers)
					.withParameters(params).withLocale(locale).build();

			scheduler.addJob(exportJob, true);
			scheduler.triggerJob(exportJob.getName(), exportJob.getGroup());

			ret = Response.ok().entity(exportJob.getName()).build();

			scheduleCleanUp();

		} catch (SchedulerException e) {
			String msg = String.format("Error during scheduling of export job for dataset %d", dataSetId);
			logger.error(msg, e);
			ret = Response.serverError().build();
		}

		logger.debug("OUT - Exporting dataset \" + String.valueOf(dataSetId) + \" in CSV");

		return ret;
	}

	/**
	 * Schedules an export in Excel format of the dataset in input.
	 *
	 * @param dataSetId Id of the dataset to be exported
	 * @param body      JSON that contains drivers and parameters data
	 * @return The job id
	 */
	@POST
	@Path("/dataset/{dataSetId}/xls")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	public Response datasetAsXls(@PathParam("dataSetId") Integer dataSetId, String body) {

		logger.debug("IN");

		JSONObject driversJson = null;
		JSONArray paramsJson = null;

		try {
			JSONObject data = new JSONObject(body);
			driversJson = data.has(BODY_ATTR_DRIVERS) ? data.getJSONObject(BODY_ATTR_DRIVERS) : null;
			paramsJson = data.has(BODY_ATTR_PARAMETERS) ? data.getJSONArray(BODY_ATTR_PARAMETERS) : null;
		} catch (JSONException e) {
			String msg = String.format("Body data is invalid: %s", body);
			logger.error(msg, e);
			return Response.serverError().build();
		}

		Response ret = null;
		Locale locale = request.getLocale();
		UserProfile userProfile = UserProfileManager.getProfile();
		IDataSetDAO dsDAO = DAOFactory.getDataSetDAO();
		IDataSet dataSet = dsDAO.loadDataSetById(dataSetId);
		JSONObject jsonObject;
		try {
			jsonObject = DataSetUtilities.parametersJSONArray2JSONObject(dataSet, paramsJson);
		} catch (Exception e) {
			throw new SpagoBIRuntimeException("Error while getting parameters for dataset", e);
		}
		Map<String, String> params = DataSetUtilities.getParametersMap(jsonObject);
		Map<String, Object> drivers = DataSetUtilities.getDriversMap(driversJson);

		try {
			Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

			JobDetail exportJob = ExportJobBuilder.fromDataSetIdAndUserProfile(dataSetId, userProfile).withTypeOfXls().withDrivers(drivers)
					.withParameters(params).withLocale(locale).build();

			scheduler.addJob(exportJob, true);
			scheduler.triggerJob(exportJob.getName(), exportJob.getGroup());

			ret = Response.ok().entity(exportJob.getName()).build();

			scheduleCleanUp();

		} catch (SchedulerException e) {
			String msg = String.format("Error during scheduling of export job for dataset %d", dataSetId);
			logger.error(msg, e);
			ret = Response.serverError().build();
		}

		logger.debug("OUT");

		return ret;
	}

	/**
	 * Delete all exported resources requested by a specific user.
	 */
	@DELETE
	@Path("/")
	public void deleteAll() {
		logger.debug("IN");

		UserProfile userProfile = UserProfileManager.getProfile();
		String resoursePath = SpagoBIUtilities.getResourcePath();
		final java.nio.file.Path perUserExportResourcePath = ExportPathBuilder.getInstance().getPerUserExportResourcePath(resoursePath, userProfile);

		try {
			Files.walkFileTree(perUserExportResourcePath, new SimpleFileVisitor<java.nio.file.Path>() {
				@Override
				public FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc) throws IOException {
					/*
					 * It's not a problem to delete user directory but it's not so useful then we can skip it.
					 */
					if (!perUserExportResourcePath.equals(dir)) {
						Files.delete(dir);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (Exception e) {
			logger.error("Error during downloadable resources deletion", e);
		}

		logger.debug("OUT");
	}

	@GET
	@Path("/dataset/{id}")
	public Response get(@PathParam("id") UUID id) throws IOException {

		logger.debug("IN");

		Response ret = Response.status(Status.NOT_FOUND).build();

		ExportPathBuilder exportPathBuilder = ExportPathBuilder.getInstance();

		UserProfile userProfile = UserProfileManager.getProfile();
		String resoursePath = SpagoBIUtilities.getResourcePath();
		java.nio.file.Path dataFile = exportPathBuilder.getPerJobIdDataFile(resoursePath, userProfile, id);

		if (Files.isRegularFile(dataFile)) {
			java.nio.file.Path metadataFile = exportPathBuilder.getPerJobIdMetadataFile(resoursePath, userProfile, id);

			ExportMetadata metadata = ExportMetadata.readFromJsonFile(metadataFile);

			// Create a placeholder to indicate the file is downloaded
			try {
				Files.createFile(exportPathBuilder.getPerJobIdDownloadedPlaceholderFile(resoursePath, userProfile, id));
			} catch (Exception e) {
				// Yes, it's mute!
			}

			ret = Response.ok(dataFile.toFile()).header("Content-Disposition", "attachment" + "; filename=\"" + metadata.getFileName() + "\";")
					.type(metadata.getMimeType()).build();
		}

		logger.debug("OUT");

		return ret;
	}

	@POST
	@Path("/cockpitData")
	@Produces(MediaType.APPLICATION_JSON)
	public Response exportCockpitDocumentWidgetData(DocumentExportConf documentExportConf) {
		logger.debug("IN");

		logger.debug(String.format("document id: %s", documentExportConf.getDocumentId()));
		logger.debug(String.format("document label: %s", documentExportConf.getDocumentLabel()));
		logger.debug(String.format("export type: %s", documentExportConf.getExportType()));
		logger.debug(String.format("parameters: %s", documentExportConf.getParameters()));

		JobDetail exportJob = new CockpitDataExportJobBuilder().setDocumentExportConf(documentExportConf).setLocale(request.getLocale())
				.setUserProfile(UserProfileManager.getProfile()).build();
		logger.debug("Created export job");

		try {
			Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
			scheduler.addJob(exportJob, true);
			scheduler.triggerJob(exportJob.getName(), exportJob.getGroup());
			logger.debug("Export job triggered ");
		} catch (SchedulerException e) {
			String msg = String.format("Error during scheduling of export job for cokcpit document %d", documentExportConf.getDocumentLabel());
			logger.error(msg, e);
			throw new SpagoBIRuntimeException(msg);
		}
		logger.debug("OUT");
		return Response.ok().entity(exportJob.getName()).build();

	}

	/**
	 * Schedula a job to clean old export.
	 *
	 * @throws SchedulerException In case of error during scheduling
	 */
	private void scheduleCleanUp() throws SchedulerException {

		UserProfile userProfile = UserProfileManager.getProfile();
		String resoursePath = SpagoBIUtilities.getResourcePath();

		String jobName = String.format("delete-old-export-for-%s", userProfile.getUserId());
		String jobGroup = "delete-old-export";
		String jobDescription = String.format("Delete old exports for user %s", userProfile.getUserId());

		JobDataMap jobDataMap = new JobDataMap();

		jobDataMap.put(ExportDeleteOldJob.MAP_KEY_RESOURCE_PATH, resoursePath);
		jobDataMap.put(ExportDeleteOldJob.MAP_KEY_USER_PROFILE, userProfile);

		JobDetail job = new JobDetail(jobName, jobGroup, ExportDeleteOldJob.class);

		job.setDescription(jobDescription);
		job.setJobDataMap(jobDataMap);

		Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

		scheduler.addJob(job, true);
		scheduler.triggerJob(job.getName(), job.getGroup());

	}
}