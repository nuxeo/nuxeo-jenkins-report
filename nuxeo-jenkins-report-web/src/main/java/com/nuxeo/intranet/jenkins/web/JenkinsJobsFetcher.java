/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package com.nuxeo.intranet.jenkins.web;

import static org.jboss.seam.ScopeType.EVENT;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.component.UIComponent;
import javax.faces.event.ActionEvent;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.web.RequestParameter;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.international.StatusMessage;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.URLBlob;
import org.nuxeo.ecm.platform.ui.web.component.list.UIEditableList;
import org.nuxeo.ecm.platform.ui.web.util.ComponentUtils;

/**
 * Fetches unstable jobs information from Jenkins json API and fill a list JSF
 * component with returned values.
 *
 * @since 5.6
 */
@Name("jenkinsJobsFetcher")
@Scope(EVENT)
public class JenkinsJobsFetcher implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(JenkinsJobsFetcher.class);

    @RequestParameter
    protected String jenkinsURL;

    @RequestParameter
    protected String listComponentId;

    @RequestParameter
    protected String feedbackComponentId;

    @In(create = true, required = false)
    protected FacesMessages facesMessages;

    protected void logMessage(StatusMessage.Severity severity, String message) {
        facesMessages.addToControl(feedbackComponentId, severity, message);
    }

    public void fetchJobsToList(ActionEvent event) {
        // retrieve new values from URL first
        if (jenkinsURL == null) {
            facesMessages.addToControl(feedbackComponentId,
                    StatusMessage.Severity.ERROR,
                    "No URL sent to check Jenkins jobs");
            return;
        }

        try {
            String jsonURL = jenkinsURL;
            if (!jsonURL.endsWith("/")) {
                jsonURL += "/";
            }
            jsonURL += "api/json";

            JSONObject json = retrieveJSONObject(jsonURL);
            List<Map<String, Serializable>> jenkinsData = convertJenkinsResponseToDocumentData(json);

            UIComponent component = event.getComponent();
            if (component == null) {
                return;
            }
            UIEditableList list = ComponentUtils.getComponent(component,
                    listComponentId, UIEditableList.class);

            if (list != null) {
                list.getEditableModel().setWrappedData(jenkinsData);
            }

            facesMessages.addToControl(feedbackComponentId,
                    StatusMessage.Severity.INFO,
                    "Jobs retrieved from Jenkins, enjoy!");
        } catch (Exception e) {
            log.error(e, e);
            facesMessages.addToControl(feedbackComponentId,
                    StatusMessage.Severity.ERROR, String.format(
                            "Error while retrieving jobs from Jenkins: %s",
                            e.getMessage()));
        }
    }

    JSONObject retrieveJSONObject(String url) {
        if (url == null) {
            return null;
        }
        try {
            // avoid https
            if (url.startsWith("https")) {
                url = url.replaceFirst("https", "http");
            }
            Blob blob = new URLBlob(new URL(url), "application/json", "UTF-8",
                    "content.json", null);
            String json = blob.getString();
            JSONObject jsonObject = JSONObject.fromObject(json);
            return jsonObject;
        } catch (Exception e) {
            log.error(e, e);
            logMessage(StatusMessage.Severity.ERROR, String.format(
                    "Error while retrieving jobs from Jenkins for url %s: %s",
                    url, e.getMessage()));
            return null;
        }
    }

    protected List<Map<String, Serializable>> convertJenkinsResponseToDocumentData(
            JSONObject jsonObject) throws IOException {
        List<Map<String, Serializable>> res = new ArrayList<Map<String, Serializable>>();
        if (jsonObject != null) {
            JSONArray jsonJobs = jsonObject.optJSONArray("jobs");
            if (jsonJobs != null) {
                for (Object jsonJob : jsonJobs) {
                    String color = ((JSONObject) jsonJob).getString("color");
                    if (color != null && !color.startsWith("blue")
                            && !color.startsWith("grey")) {
                        Map<String, Serializable> job = new HashMap<String, Serializable>();
                        String url = ((JSONObject) jsonJob).getString("url");
                        job.put("job_id",
                                ((JSONObject) jsonJob).getString("name"));
                        job.put("job_url", url);
                        // retrieve additional info for each failing job,
                        // fetching the whole state in one query using the
                        // "depth" attribute is more costly
                        if (url != null) {
                            JSONObject jsonBuild = retrieveJSONObject(url
                                    + "lastBuild/api/json");
                            if (jsonBuild != null) {
                                // get build number
                                job.put("build_number",
                                        String.valueOf(jsonBuild.optInt("number")));
                                // get claim info
                                JSONArray actions = jsonBuild.optJSONArray("actions");
                                if (actions != null) {
                                    for (Object jsonAction : actions) {
                                        if (jsonAction != null
                                                && ((JSONObject) jsonAction).has("claimed")) {
                                            JSONObject claim = (JSONObject) jsonAction;
                                            if (claim.optBoolean("claimed")) {
                                                job.put("claimer",
                                                        claim.optString("claimedBy"));
                                                String reason = claim.optString("reason");
                                                if ("null".equals(reason)) {
                                                    reason = null;
                                                }
                                                job.put("comment", reason);
                                            }
                                            break;
                                        }
                                    }
                                }
                                // get culprits
                                ArrayList<String> culprits = new ArrayList<String>();
                                JSONArray jsonCulprits = jsonBuild.optJSONArray("culprits");
                                if (jsonCulprits != null) {
                                    for (Object jsonCulprit : jsonCulprits) {
                                        if (jsonCulprit != null) {
                                            String name = ((JSONObject) jsonCulprit).optString("fullName");
                                            if (name != null && !name.isEmpty()
                                                    && !"jenkins".equals(name)) {
                                                culprits.add(name);
                                            }
                                        }
                                    }
                                }
                                job.put("culprits", culprits);
                                // get result
                                job.put("type", jsonBuild.optString("result"));
                            }
                        }
                        res.add(job);
                    }
                }
            }
        }
        return res;
    }

}