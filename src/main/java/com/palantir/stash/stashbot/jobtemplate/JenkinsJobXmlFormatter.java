//   Copyright 2013 Palantir Technologies
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
package com.palantir.stash.stashbot.jobtemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Map;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.repository.Repository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.stash.stashbot.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbot.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbot.config.RepositoryConfiguration;
import com.palantir.stash.stashbot.managers.VelocityManager;
import com.palantir.stash.stashbot.urlbuilder.StashbotUrlBuilder;

public class JenkinsJobXmlFormatter {

	// Tacking this onto the end of the build command makes it print out
	// "BUILD SUCCESS0" on success and
	// "BUILD FAILURE1" on failure.
	private static final String BUILD_COMMAND_POSTFIX = "&& echo \"BUILD SUCCESS$?\" || /bin/false || (echo \"BUILD FAILURE$?\" && /bin/false)";

	private final VelocityManager velocityManager;
	private final ConfigurationPersistenceManager cpm;
	private final StashbotUrlBuilder sub;
	private final NavBuilder navBuilder;

	public JenkinsJobXmlFormatter(VelocityManager velocityManager,
			ConfigurationPersistenceManager cpm, StashbotUrlBuilder sub,
			NavBuilder navBuilder) throws IOException {
		this.velocityManager = velocityManager;
		this.cpm = cpm;
		this.sub = sub;
		this.navBuilder = navBuilder;
	}

	private String curlCommandBuilder(Repository repo, JobTemplate jobTemplate,
			RepositoryConfiguration rc, String repositoryUrl, String status)
			throws SQLException {
		final JenkinsServerConfiguration jsc = cpm
				.getJenkinsServerConfiguration(rc.getJenkinsServerName());
		StringBuffer sb = new StringBuffer();
		sb.append("/usr/bin/curl -s -i ");
		sb.append(sub.buildReportingUrl(repo, jobTemplate.getJobType(), jsc, status));
		return sb.toString();
	}

	public String generateJobXml(JobTemplate jobTemplate, Repository repo)
			throws SQLException {

		final VelocityContext vc = velocityManager.getVelocityContext();
		final RepositoryConfiguration rc = cpm
				.getRepositoryConfigurationForRepository(repo);
		final JenkinsServerConfiguration jsc = cpm
				.getJenkinsServerConfiguration(rc.getJenkinsServerName());

		String repositoryUrl = navBuilder.repo(repo).clone(repo.getScmId())
				.buildAbsoluteWithoutUsername();
		// manually insert the username and pw we are configured to use
		repositoryUrl = repositoryUrl.replace("://",
				"://" + jsc.getStashUsername() + ":" + jsc.getStashPassword()
						+ "@");

		vc.put("repositoryUrl", repositoryUrl);

		vc.put("prebuildCommand", rc.getPrebuildCommand());

		// Put build command depending on build type
		// TODO: figure out build command some other way?
		switch (jobTemplate.getJobType()) {
		case VERIFY_COMMIT:
			vc.put("buildCommand", buildCommand(rc.getVerifyBuildCommand()));
			break;
		case VERIFY_PR:
			vc.put("buildCommand", buildCommand(rc.getVerifyBuildCommand()));
			break;
		case PUBLISH:
			vc.put("buildCommand", buildCommand(rc.getPublishBuildCommand()));
			break;
		case NOOP:
			vc.put("buildCommand", buildCommand("/bin/true"));
			break;
		}

		vc.put("startedCommand",
				curlCommandBuilder(repo, jobTemplate, rc, repositoryUrl,
						"inprogress"));
		vc.put("successCommand",
				curlCommandBuilder(repo, jobTemplate, rc, repositoryUrl,
						"successful"));
		vc.put("failedCommand",
				curlCommandBuilder(repo, jobTemplate, rc, repositoryUrl,
						"failed"));
		vc.put("repositoryLink", navBuilder.repo(repo).browse().buildAbsolute());
		vc.put("repositoryName",
				repo.getProject().getName() + " " + repo.getName());

		// Parameters are type-dependent for now
		ImmutableList.Builder<Map<String, String>> paramBuilder = new ImmutableList.Builder<Map<String, String>>();
		switch (jobTemplate.getJobType()) {
		case VERIFY_COMMIT:
			// repoId
			paramBuilder.add(ImmutableMap.of("name", "repoId", "typeName",
					JenkinsBuildParamType.StringParameterDefinition.toString(),
					"description", "stash repository Id", "defaultValue",
					"unknown"));
			// buildHead
			paramBuilder.add(ImmutableMap.of("name", "buildHead", "typeName",
					JenkinsBuildParamType.StringParameterDefinition.toString(),
					"description", "the change to build", "defaultValue",
					"head"));
			break;
		case VERIFY_PR:
			// repoId
			paramBuilder.add(ImmutableMap.of("name", "repoId", "typeName",
					JenkinsBuildParamType.StringParameterDefinition.toString(),
					"description", "stash repository Id", "defaultValue",
					"unknown"));
			// buildHead
			paramBuilder.add(ImmutableMap.of("name", "buildHead", "typeName",
					JenkinsBuildParamType.StringParameterDefinition.toString(),
					"description", "the change to build", "defaultValue",
					"head"));
			// pullRequestId
			paramBuilder.add(ImmutableMap.of("name", "pullRequestId",
					"typeName",
					JenkinsBuildParamType.StringParameterDefinition.toString(),
					"description", "the pull request Id", "defaultValue", ""));
			break;
		case PUBLISH:
			// repoId
			paramBuilder.add(ImmutableMap.of("name", "repoId", "typeName",
					JenkinsBuildParamType.StringParameterDefinition.toString(),
					"description", "stash repository Id", "defaultValue",
					"unknown"));
			// buildHead
			paramBuilder.add(ImmutableMap.of("name", "buildHead", "typeName",
					JenkinsBuildParamType.StringParameterDefinition.toString(),
					"description", "the change to build", "defaultValue",
					"head"));
			break;
		case NOOP:
			// no params
			break;
		}
		vc.put("paramaterList", paramBuilder.build());

		StringWriter xml = new StringWriter();

		VelocityEngine ve = velocityManager.getVelocityEngine();
		Template template = ve.getTemplate(jobTemplate.getTemplateFile());

		template.merge(vc, xml);
		return xml.toString();
	}

	/**
	 * XML specific parameter types
	 * 
	 * @author cmyers
	 */
	public static enum JenkinsBuildParamType {
		StringParameterDefinition, BooleanParameterDefinition;
		// TODO: more?
	}

	/**
	 * Appends the shell magics to the build command to make it succeed/fail
	 * properly.
	 * 
	 * TODO: move this into the template?
	 * 
	 * @param command
	 * @return
	 */
	private String buildCommand(String command) {
		return command + " " + BUILD_COMMAND_POSTFIX;
	}

}