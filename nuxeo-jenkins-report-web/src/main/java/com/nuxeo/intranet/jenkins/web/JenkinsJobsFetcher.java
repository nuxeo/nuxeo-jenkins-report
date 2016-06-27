/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package com.nuxeo.intranet.jenkins.web;

import static org.jboss.seam.ScopeType.EVENT;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.faces.application.FacesMessage;
import javax.faces.component.EditableValueHolder;
import javax.faces.component.UIComponent;
import javax.faces.event.ActionEvent;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.web.RequestParameter;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.international.StatusMessage;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.platform.ui.web.component.list.UIEditableList;
import org.nuxeo.ecm.platform.ui.web.model.EditableModel;
import org.nuxeo.ecm.platform.ui.web.util.ComponentUtils;
import org.nuxeo.ecm.webapp.contentbrowser.DocumentActions;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * Fetches unstable jobs information from Jenkins json API and fill a list JSF component with returned values.
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
    protected String lastUpdateFeedbackComponentId;

    @RequestParameter
    protected String feedbackComponentId;

    @RequestParameter
    protected Boolean removeSuccessful;

    @In(create = true, required = false)
    protected FacesMessages facesMessages;

    @In(create = true, required = false)
    protected DocumentActions documentActions;

    @In(create = true)
    protected NavigationContext navigationContext;

    @SuppressWarnings("unchecked")
    public void fetchJobsToList(ActionEvent event) {
        // retrieve new values from URL first
        if (jenkinsURL == null) {
            logMessage(StatusMessage.Severity.ERROR, "No URL sent to check Jenkins jobs");
            return;
        }

        try {
            UIComponent component = event.getComponent();
            if (component == null) {
                return;
            }
            UIEditableList list = ComponentUtils.getComponent(component, listComponentId, UIEditableList.class);
            EditableValueHolder comment = ComponentUtils.getComponent(component, lastUpdateFeedbackComponentId,
                    EditableValueHolder.class);

            if (list != null) {
                String jsonURL = jenkinsURL.trim();
                if (!jsonURL.endsWith("/")) {
                    jsonURL += "/";
                }
                jsonURL += "api/json";

                JSONObject json = retrieveJSONObject(jsonURL);
                EditableModel em = list.getEditableModel();
                List<Map<String, Serializable>> oldData = (List<Map<String, Serializable>>) em.getWrappedData();
                JenkinsJsonConverter cv = new JenkinsJsonConverter();
                List<Map<String, Serializable>> jenkinsData = cv.convertJobs(json, oldData, this);
                List<Map<String, Serializable>> mergedData = cv.mergeData(oldData, jenkinsData);
                em.setWrappedData(mergedData);

                logMessage(StatusMessage.Severity.INFO, "Jobs retrieved from Jenkins, enjoy!");
                String updateMessage = computeLastUpdateFeedbackMessage(cv);
                if (comment != null) {
                    comment.setSubmittedValue(updateMessage);
                }
            }

        } catch (Exception e) {
            log.error(e, e);
            logMessage(StatusMessage.Severity.ERROR,
                    String.format("Error while retrieving jobs from Jenkins: %s", e.getMessage()));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void updateFromJenkins(ActionEvent event) {
        // retrieve new values from URL first
        if (jenkinsURL == null) {
            logMessage(StatusMessage.Severity.ERROR, "No URL sent to check Jenkins jobs");
            return;
        }

        try {
            String jsonURL = jenkinsURL.trim();
            if (!jsonURL.endsWith("/")) {
                jsonURL += "/";
            }
            jsonURL += "api/json";

            JSONObject json = retrieveJSONObject(jsonURL);
            DocumentModel currentDoc = navigationContext.getCurrentDocument();
            List<Map<String, Serializable>> oldData = (List) currentDoc.getPropertyValue(
                    JenkinsReportFields.JOBS_PROPERTY);
            JenkinsJsonConverter cv = new JenkinsJsonConverter();
            List<Map<String, Serializable>> jenkinsData = cv.convertJobs(json, oldData, this);
            List<Map<String, Serializable>> mergedData = cv.mergeData(oldData, jenkinsData);
            currentDoc.setPropertyValue(JenkinsReportFields.JOBS_PROPERTY, (Serializable) mergedData);
            logMessage(StatusMessage.Severity.INFO, "Jobs retrieved from Jenkins, enjoy!");
            String updateMessage = computeLastUpdateFeedbackMessage(cv);
            currentDoc.setPropertyValue(JenkinsReportFields.LAST_UPDATE_FEEDBACK_PROPERTY, updateMessage);
            documentActions.updateDocument(currentDoc, Boolean.TRUE);

        } catch (Exception e) {
            log.error(e, e);
            logMessage(StatusMessage.Severity.ERROR,
                    String.format("Error while retrieving jobs from Jenkins: %s", e.getMessage()));
        }
    }

    /**
     * methods used by the {@link JenkinsJsonConverter} class
     */

    /**
     * Retrieve the json data for given url
     *
     * @since 5.6
     * @return
     */
    protected JSONObject retrieveJSONObject(String url) {
        if (url == null) {
            return null;
        }
        BufferedReader in = null;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            int status = conn.getResponseCode();
            boolean redirect = false;
            if (status != HttpURLConnection.HTTP_OK && (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER)) {
                redirect = true;
            }
            if (redirect) {
                String newUrl = conn.getHeaderField("Location");
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
            }
            in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            IOUtils.copy(in, output);
            JSONObject jsonObject = JSONObject.fromObject(output.toString());
            return jsonObject;
        } catch (FileNotFoundException e) {
            // no last status for this job
            return null;
        } catch (IOException | JSONException e) {
            log.error(e, e);
            logMessage(StatusMessage.Severity.ERROR,
                    String.format("Error while retrieving jobs from Jenkins for url %s: %s", url, e.getMessage()));
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error(e, e);
                    logMessage(StatusMessage.Severity.ERROR,
                            String.format("Error while closing stream for url %s: %s", url, e.getMessage()));
                }
            }
        }

    }

    protected void logMessage(StatusMessage.Severity severity, String message) {
        facesMessages.addToControl(feedbackComponentId, severity, message);
    }

    @SuppressWarnings("boxing")
    protected String computeLastUpdateFeedbackMessage(JenkinsJsonConverter converter) {
        StringBuilder res = new StringBuilder();
        res.append("Last Update done at ");
        DateFormat aDateFormat = DateFormat.getDateTimeInstance();
        res.append(aDateFormat.format((new Date())));
        res.append('\n');
        // copy error messages that could have been notified to JSF
        List<FacesMessage> messages = facesMessages.getCurrentMessagesForControl(feedbackComponentId);
        if (messages != null) {
            for (FacesMessage msg : messages) {
                if (msg.getSeverity().getOrdinal() > 0) {
                    res.append(msg.getSummary());
                    res.append('\n');
                } else {
                    res.append("to remove: " + msg.getSummary());
                    res.append('\n');
                }
            }
        }
        // add final message
        if (converter != null) {
            res.append(String.format("Jobs retrieved from Jenkins: %s new failures, %s fixed, %s unchanged.",
                    converter.getNewFailingCount(), converter.getFixedCount(), converter.getUnchangedCount()));
        }
        return res.toString();
    }
}
