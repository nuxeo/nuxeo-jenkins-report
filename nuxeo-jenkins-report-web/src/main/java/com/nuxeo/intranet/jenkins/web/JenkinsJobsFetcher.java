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

import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.faces.component.UIComponent;
import javax.faces.event.ActionEvent;

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
            String jsonURL = jenkinsURL.trim();
            if (!jsonURL.endsWith("/")) {
                jsonURL += "/";
            }
            jsonURL += "api/json";

            JSONObject json = retrieveJSONObject(jsonURL);
            List<Map<String, Serializable>> jenkinsData = JenkinsJsonConverter.convertJobs(
                    json, this);

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

    protected JSONObject retrieveJSONObject(String url) {
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

}