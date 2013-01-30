package dsrg.glims;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.http.GenericUrl;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Builder;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;

import dsrg.glims.model.ClientFile;

public class ActualFileServlet extends GLIMSServlet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Given a {@code file_id} URI parameter, return a JSON representation of
	 * the given file.
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String fileId = req.getParameter("file_id");
		if (fileId == null) {
			System.out.println("file id was null");
			sendError(resp, 400, "The `file_id` URI parameter must be specified.");
			return;
		}
		System.out.println("file id: " + fileId);

		File file = null;
		Drive drive = getDriveService(req, resp);
		try {
			file = drive.files().get(fileId).execute();
		} catch (GoogleJsonResponseException e) {
			System.out.println("problem opening file");
			System.out.println(e.getMessage());
			System.out.println();
			if (e.getStatusCode() == 401) { 
				// The user has revoked our token or it is otherwise bad.
				// Delete the local copy so that their next page load will recover.
				deleteCredential(req, resp);
				sendError(resp, 401, "Unauthorized");
				return;
			}
		}
		
		if (file != null) {
			// get all the metadata for a file
			// does this by going two levels up in the file hierarchy
			// there is not enough error handling in this area
			List<ParentReference> parents = file.getParents();
			BatchRequest batch = drive.batch();
			List<FileCallback> parentCallbacks = new ArrayList<FileCallback>();
			for (int i = 0; i < parents.size(); i++) {
				Get fileGet = drive.files().get(parents.get(i).getId());
				FileCallback fileCallback = new FileCallback();
				parentCallbacks.add(fileCallback);
				fileGet.queue(batch,fileCallback);				
			}
			batch.execute();
					
			List<FileCallback> grandParentCallbacks = new ArrayList<FileCallback>();
			for (int i=0; i<parentCallbacks.size(); i++) {
				File parentFile = parentCallbacks.get(i).getFile();
				List<ParentReference> grandParents = parentFile.getParents();
				Get fileGet = drive.files().get(grandParents.get(0).getId()); // This assumes exactly 1 grandparent, which is the way the system is defined 
				FileCallback fileCallback = new FileCallback();
				grandParentCallbacks.add(fileCallback);
				fileGet.queue(batch,fileCallback);								
			}
			batch.execute();
			
			StringBuffer buffer = new StringBuffer();
			buffer.append("{ ");
			for (int i=0; i<parentCallbacks.size(); i++) {
				File parentFile = parentCallbacks.get(i).getFile();
				File grandParentFile = grandParentCallbacks.get(i).getFile();
				if (i > 0)
					buffer.append(", ");
				buffer.append("\"" + grandParentFile.getTitle() + "\": \"" + parentFile.getTitle() + "\""); // got null pointer here
			}
			buffer.append(" }");
			String parentsString = buffer.toString();
			String content = downloadFileContent(drive, file);
			if (content == null) {
				content = "";
			}
			resp.setContentType(JSON_MIMETYPE);
			resp.getWriter().print(new ClientFile(file, content, parentsString).toJson());
		} else {
			sendError(resp, 404, "File not found");
		}

	}
	/**
	 * Build and return a Drive service object based on given request
	 * parameters.
	 * 
	 * @param req
	 *            Request to use to fetch code parameter or accessToken session
	 *            attribute.
	 * @param resp
	 *            HTTP response to use for redirecting for authorization if
	 *            needed.
	 * @return Drive service object that is ready to make requests, or null if
	 *         there was a problem.
	 */
	private Drive getDriveService(HttpServletRequest req,
			HttpServletResponse resp) throws IOException {
		Credential credentials = getCredential(req, resp);
		return new Builder(TRANSPORT, JSON_FACTORY, credentials).build();
	}

	/**
	 * Download the content of the given file.
	 * 
	 * @param service
	 *            Drive service to use for downloading.
	 * @param file
	 *            File metadata object whose content to download.
	 * @return String representation of file content. String is returned here
	 *         because this app is setup for text/plain files.
	 * @throws IOException
	 *             Thrown if the request fails for whatever reason.
	 */
	private String downloadFileContent(Drive service, File file) throws IOException {

		GenericUrl u = new GenericUrl(file.getDownloadUrl());
		Get request = service.files().get(file.getId());
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		MediaHttpDownloader mhd = request.getMediaHttpDownloader();
		mhd.download(u, bos);
		return bos.toString();
	}
	
//	private AppEngineFile downloadFileContentToBlob(Drive service, File file) throws IOException {
//		FileService fileService = FileServiceFactory.getFileService();
//		AppEngineFile blobfile = fileService.createNewBlobFile("text/plain");
//		FileWriteChannel writeChannel = fileService.openWriteChannel(blobfile, true);
//
//		GenericUrl genericUrl = new GenericUrl(file.getDownloadUrl());
//		Get get = service.files().get(file.getId());
//		MediaHttpDownloader downloader = get.getMediaHttpDownloader();
//
//		downloader.download(genericUrl, Channels.newOutputStream(writeChannel));
//		writeChannel.closeFinally();
//		return blobfile;
//	}
}
