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
package org.ngrinder.agent.controller;

import static org.ngrinder.common.util.CollectionUtils.buildMap;

import org.ngrinder.agent.service.AgentManagerService;
import org.ngrinder.common.controller.BaseController;
import org.ngrinder.common.controller.RestAPI;
import org.ngrinder.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Agent management controller.
 *
 * @author JunHo Yoon
 * @since 3.1
 */
@Controller
@RequestMapping("/agent")
@PreAuthorize("hasAnyRole('A', 'S')")
public class AgentManagerController extends BaseController {

	@SuppressWarnings("SpringJavaAutowiringInspection")
	@Autowired
	private AgentManagerService agentManagerService;

	/**
	 * Agents list
	 */
	@RequestMapping({"", "/", "/list"})
	@PreAuthorize("hasAnyRole('A', 'S', 'U')")
	public String getAll(final User user) {
		return "app";
	}

	/**
	 * Get the agent detail info for the given agent id.
	 *
	 * @param id    agent id
	 * @param model model
	 * @return agent/agentDetail
	 */
	@RequestMapping("/{id}")
	public String getOne(@PathVariable Long id, ModelMap model) {
		model.addAttribute("agent", agentManagerService.getOne(id));
		return "agent/detail";
	}

	/**
	 * Get the number of available agents.
	 *
	 * @param user The login user
	 * @param targetRegion The name of target region
	 * @return availableAgentCount Available agent count
	 */
	@RestAPI
	@RequestMapping(value = {"/api/availableAgentCount"}, method = RequestMethod.GET)
	@PreAuthorize("permitAll")
	public HttpEntity<String> getAvailableAgentCount(User user,
		@RequestParam(value = "targetRegion", required = true) String targetRegion) {
		int availableAgentCount = agentManagerService.getReadyAgentCount(user, targetRegion);
		HttpEntity<String> returnHttpEntity = toJsonHttpEntity(buildMap("availableAgentCount",
			availableAgentCount));
		return returnHttpEntity;
	}

}
