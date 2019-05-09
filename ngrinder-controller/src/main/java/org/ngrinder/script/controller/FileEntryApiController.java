/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ngrinder.script.controller;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FilenameUtils.getPath;
import static org.ngrinder.common.util.AopUtils.proxy;
import static org.ngrinder.common.util.EncodingUtils.encodePathWithUTF8;
import static org.ngrinder.common.util.ExceptionUtils.processException;
import static org.ngrinder.common.util.PathUtils.removePrependedSlash;
import static org.ngrinder.common.util.PathUtils.trimPathSeparatorBothSides;
import static org.ngrinder.common.util.Preconditions.checkNotNull;

import com.nhncorp.lucy.security.xss.XssPreventer;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.ngrinder.common.controller.BaseController;
import org.ngrinder.common.controller.RestAPI;
import org.ngrinder.common.util.HttpContainerContext;
import org.ngrinder.common.util.PathUtils;
import org.ngrinder.common.util.UrlUtils;
import org.ngrinder.infra.spring.RemainedPath;
import org.ngrinder.model.User;
import org.ngrinder.script.handler.*;
import org.ngrinder.script.model.FileCategory;
import org.ngrinder.script.model.FileEntry;
import org.ngrinder.script.model.FileType;
import org.ngrinder.script.service.FileEntryService;
import org.ngrinder.script.service.ScriptValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

/**
 * FileEntry manipulation API controller.
 *
 * @since 3.5.0
 */
@RestController
@RequestMapping("/script/api")
public class FileEntryApiController extends BaseController {

	private static final Logger LOG = LoggerFactory.getLogger(FileEntryApiController.class);

	private static final Comparator<FileEntry> DIRECTORY_PRIORITY_FILE_ENTRY_COMPARATOR = (o1, o2) -> {
		if (o1.getFileType() == FileType.DIR && o2.getFileType() != FileType.DIR) {
			return -1;
		}
		return (o1.getFileName().compareTo(o2.getFileName()));
	};

	@Autowired
	private FileEntryService fileEntryService;

	@Autowired
	private ScriptHandlerFactory handlerFactory;

	@Autowired
	HttpContainerContext httpContainerContext;

	@Autowired
	private ScriptValidationService scriptValidationService;

	/**
	 * Get all files which belongs to given user and path.
	 *
	 * @param user user
	 * @param path path
	 * @return json string
	 */
	@RestAPI
	@GetMapping("/list/**")
	public List<FileEntry> getAll(User user, @RemainedPath String path) {
		final String trimmedPath = StringUtils.trimToEmpty(path);

		return fileEntryService.getAll(user)
			.stream()
			.filter(Objects::nonNull)
			.filter(fileEntry -> trimPathSeparatorBothSides(getPath(fileEntry.getPath())).equals(trimmedPath))
			.sorted(DIRECTORY_PRIORITY_FILE_ENTRY_COMPARATOR)
			.peek(fileEntry -> fileEntry.setPath(removePrependedSlash(fileEntry.getPath())))
			.collect(toList());
	}

	/**
	 * Get the SVN url BreadCrumbs HTML string.
	 *
	 * @param user user
	 * @param path path
	 * @return generated HTML
	 */
	@RestAPI
	@GetMapping("/svnUrl")
	public String getSvnUrlBreadcrumbs(User user, String path) {
		String contextPath = httpContainerContext.getCurrentContextUrlFromUserRequest();
		String[] parts = StringUtils.split(path, '/');
		StringBuilder accumulatedPart = new StringBuilder(contextPath).append("/script/list");
		StringBuilder returnHtml = new StringBuilder().append("<a href='").append(accumulatedPart).append("'>")
			.append(contextPath).append("/svn/").append(user.getUserId()).append("</a>");
		for (String each : parts) {
			returnHtml.append("/");
			accumulatedPart.append("/").append(each);
			returnHtml.append("<a href='").append(accumulatedPart).append("'>").append(each).append("</a>");
		}
		return returnHtml.toString();
	}

	@RestAPI
	@GetMapping("/handlers")
	public List<ScriptHandler> getHandlers() {
		return handlerFactory.getVisibleHandlers();
	}

	/**
	 * Search files on the query.
	 *
	 * @param user  current user
	 * @param query query string
	 * @return list of filtered files
	 */
	@RestAPI
	@GetMapping("/search")
	public List<FileEntry> search(User user, @RequestParam(required = true, value = "query") final String query) {
		final String trimmedQuery = StringUtils.trimToEmpty(query);

		return fileEntryService.getAll(user)
			.stream()
			.filter(Objects::nonNull)
			.filter(fileEntry -> fileEntry.getFileType() != FileType.DIR)
			.filter(fileEntry -> StringUtils.containsIgnoreCase(new File(fileEntry.getPath()).getName(), trimmedQuery))
			.collect(toList());
	}


	/**
	 * Provide new file creation form data.
	 *
	 * @param user                  current user
	 * @param path                  path in which a file will be added
	 * @param testUrl               url which the script may use
	 * @param fileName              fileName
	 * @param scriptType            Type of script. optional
	 * @param createLibAndResources true if libs and resources should be created as well.
	 * @return response map
	 */
	@PostMapping(value = "/new/**", params = "type=script")
	public Map<String, Object> createScript(User user, @RemainedPath String path,
										 @RequestParam(value = "testUrl", required = false) String testUrl,
										 @RequestParam("fileName") String fileName,
										 @RequestParam(value = "scriptType", required = false) String scriptType,
										 @RequestParam(value = "createLibAndResource", defaultValue = "false") boolean createLibAndResources,
										 @RequestParam(value = "options", required = false) String options) {
		fileName = StringUtils.trimToEmpty(fileName);
		String name = "Test1";
		if (StringUtils.isEmpty(testUrl)) {
			testUrl = StringUtils.defaultIfBlank(testUrl, "http://please_modify_this.com");
		} else {
			name = UrlUtils.getHost(testUrl);
		}
		ScriptHandler scriptHandler = fileEntryService.getScriptHandler(scriptType);
		FileEntry entry;
		if (scriptHandler instanceof ProjectHandler) {
			if (!fileEntryService.hasFileEntry(user, PathUtils.join(path, fileName))) {
				fileEntryService.prepareNewEntry(user, path, fileName, name, testUrl, scriptHandler, createLibAndResources, options);
				return of(
					"message", fileName + " project is created.",
					"path", "/script/list/" + encodePathWithUTF8(path) + "/" + fileName);
			} else {
				return of(
					"message", fileName + " is already existing. Please choose the different name",
					"path", "/script/list/" + encodePathWithUTF8(path) + "/");
			}

		} else {
			String fullPath = PathUtils.join(path, fileName);
			if (fileEntryService.hasFileEntry(user, fullPath)) {
				entry = fileEntryService.getOne(user, fullPath);
			} else {
				entry = fileEntryService.prepareNewEntry(user, path, fileName, name, testUrl, scriptHandler, createLibAndResources, options);
			}
		}

		proxy(this).save(user, entry, null, "0", createLibAndResources);

		return of("file", entry);
	}

	/**
	 * Add a folder on the given path.
	 *
	 * @param user       current user
	 * @param path       path in which folder will be added
	 * @param folderName folderName
	 */
	@PostMapping(value = "/new/**", params = "type=folder")
	public HttpEntity<String> addFolder(User user, @RemainedPath String path, @RequestParam("folderName") String folderName) {
		fileEntryService.addFolder(user, path, StringUtils.trimToEmpty(folderName), "");
		return successJsonHttpEntity();
	}

	/**
	 * Get the script path BreadCrumbs HTML string.
	 *
	 * @param path path
	 * @return generated HTML
	 */
	public String getScriptPathBreadcrumbs(String path) {
		String contextPath = httpContainerContext.getCurrentContextUrlFromUserRequest();
		String[] parts = StringUtils.split(path, '/');
		StringBuilder accumulatedPart = new StringBuilder(contextPath).append("/script/list");
		StringBuilder returnHtml = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			String each = parts[i];
			accumulatedPart.append("/").append(each);
			if (i != parts.length - 1) {
				returnHtml.append("<a target='_path_view' href='").append(accumulatedPart).append("'>").append(each)
					.append("</a>").append("/");
			} else {
				returnHtml.append(each);
			}
		}
		return returnHtml.toString();
	}

	/**
	 * Delete files on the given path.
	 *
	 * @param user        user
	 * @param path        base path
	 * @param filesString file list delimited by ","
	 * @return json string
	 */
	@PostMapping(value = "/delete/**")
	@ResponseBody
	public String delete(User user, @RemainedPath String path, @RequestParam("filesString") String filesString) {
		String[] files = filesString.split(",");
		fileEntryService.delete(user, path, files);
		Map<String, Object> rtnMap = new HashMap<>(1);
		rtnMap.put(JSON_SUCCESS, true);
		return toJson(rtnMap);
	}


	/**
	 * Upload a file.
	 *
	 * @param user        current user
	 * @param path        path
	 * @param description description
	 * @param file        multi part file
	 */
	@RequestMapping(value = "/upload/**", method = RequestMethod.POST)
	public HttpEntity<String> uploadFile(User user, @RemainedPath String path, @RequestParam("description") String description,
						 @RequestParam("uploadFile") MultipartFile file) {
		try {
			description = XssPreventer.escape(description);
			upload(user, path, description, file);
		} catch (IOException e) {
			LOG.error("Error while getting file content: {}", e.getMessage(), e);
			throw processException("Error while getting file content:" + e.getMessage(), e);
		}
		return successJsonHttpEntity();
	}

	private void upload(User user, String path, String description, MultipartFile file) throws IOException {
		FileEntry fileEntry = new FileEntry();
		fileEntry.setContentBytes(file.getBytes());
		fileEntry.setDescription(description);
		fileEntry.setPath(FilenameUtils.separatorsToUnix(FilenameUtils.concat(path, file.getOriginalFilename())));
		fileEntryService.save(user, fileEntry);
	}


	/**
	 * Create the given file.
	 *
	 * @param user      user
	 * @param fileEntry file entry
	 * @return success json string
	 */
	@RestAPI
	@PostMapping(value = {"/", ""})
	public HttpEntity<String> create(User user, FileEntry fileEntry) {
		fileEntryService.save(user, fileEntry);
		return successJsonHttpEntity();
	}

	/**
	 * Create the given file.
	 *
	 * @param user        user
	 * @param path        path
	 * @param description description
	 * @param file        multi part file
	 * @return success json string
	 */
	@RestAPI
	@PostMapping(value = "/**", params = "action=upload")
	public HttpEntity<String> uploadForAPI(User user, @RemainedPath String path,
										   @RequestParam("description") String description,
										   @RequestParam("uploadFile") MultipartFile file) throws IOException {
		upload(user, path, description, file);
		return successJsonHttpEntity();
	}

	/**
	 * Check the file by given path.
	 *
	 * @param user user
	 * @param path path
	 * @return json string
	 */
	@RestAPI
	@GetMapping(value = "/**", params = "action=view")
	public HttpEntity<String> viewOne(User user, @RemainedPath String path) {
		FileEntry fileEntry = fileEntryService.getOne(user, path, -1L);
		return toJsonHttpEntity(checkNotNull(fileEntry
			, "%s file is not viewable", path));
	}

	/**
	 * Get all files which belongs to given user.
	 *
	 * @param user user
	 * @return json string
	 */
	@RestAPI
	@GetMapping(value = {"/**", "/", ""}, params = "action=all")
	public HttpEntity<String> getAll(User user) {
		return toJsonHttpEntity(fileEntryService.getAll(user));
	}

	/**
	 * Delete file by given user and path.
	 *
	 * @param user user
	 * @param path path
	 * @return json string
	 */
	@RestAPI
	@DeleteMapping("/**")
	public HttpEntity<String> deleteOne(User user, @RemainedPath String path) {
		fileEntryService.delete(user, path);
		return successJsonHttpEntity();
	}

	/**
	 * Validate the script.
	 *
	 * @param user       current user
	 * @param fileEntry  fileEntry
	 * @param hostString hostString
	 * @return validation Result string
	 */
	@RestAPI
	@PostMapping("/validate")
	public HttpEntity<String> validate(User user, FileEntry fileEntry,
									   @RequestParam(value = "hostString", required = false) String hostString) {
		fileEntry.setCreatedUser(user);
		return toJsonHttpEntity(scriptValidationService.validate(user, fileEntry, false, hostString));
	}

	/**
	 * Save a fileEntry and return to the the path.
	 *
	 * @param user                 current user
	 * @param fileEntry            file to be saved
	 * @param targetHosts          target host parameter
	 * @param validated            validated the script or not, 1 is validated, 0 is not.
	 * @param createLibAndResource true if lib and resources should be created as well.
	 * @return basePath
	 */
	@RestAPI
	@RequestMapping(value = "/save/**", method = RequestMethod.POST)
	public String save(User user, FileEntry fileEntry,
					   @RequestParam String targetHosts, @RequestParam(defaultValue = "0") String validated,
					   @RequestParam(defaultValue = "false") boolean createLibAndResource) {
		if (fileEntry.getFileType().getFileCategory() == FileCategory.SCRIPT) {
			Map<String, String> map = of(
				"validated", validated,
				"targetHosts", StringUtils.trimToEmpty(targetHosts)
			);
			fileEntry.setProperties(map);
		}
		fileEntryService.save(user, fileEntry);

		String basePath = getPath(fileEntry.getPath());
		if (createLibAndResource) {
			fileEntryService.addFolder(user, basePath, "lib", getMessages("script.commit.libFolder"));
			fileEntryService.addFolder(user, basePath, "resources", getMessages("script.commit.resourceFolder"));
		}
		return encodePathWithUTF8(basePath);
	}

	/**
	 * Get the details of given path.
	 *
	 * @param user     user
	 * @param path     user
	 * @param revision revision. -1 if HEAD
	 * @return detail view properties
	 */
	@RequestMapping(value = "/detail/**")
	public Map<String, Object> getOne(User user, @RemainedPath String path, @RequestParam(value = "r", required = false) Long revision) {
		FileEntry script = fileEntryService.getOne(user, path, revision);
		if (script == null || !script.getFileType().isEditable()) {
			LOG.error("Error while getting file detail on {}. the file does not exist or not editable", path);
			return of();
		}

		return of(
			"file", script,
			"breadcrumbPath", getScriptPathBreadcrumbs(path),
			"scriptHandler", fileEntryService.getScriptHandler(script),
			"ownerId", user.getUserId()
		);
	}
}
