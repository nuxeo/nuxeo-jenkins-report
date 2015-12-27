/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Convertes json data retrieved from Jenkins into data that can be fed to document properties.
 *
 * @since 5.6
 */
public class JenkinsJsonConverter {

    private static final Log log = LogFactory.getLog(JenkinsJsonConverter.class);

    protected int newFailingCount = 0;

    protected int fixedCount = 0;

    protected int stillUnstable = 0;

    protected int unchangedCount = 0;

    List<Map<String, Serializable>> mergedData = null;

    public List<Map<String, Serializable>> convertJobs(JSONObject jsonObject, List<Map<String, Serializable>> oldData,
            JenkinsJobsFetcher fetcher) throws IOException {
        List<Map<String, Serializable>> res = new ArrayList<Map<String, Serializable>>();
        List<String> retrievedJobs = new ArrayList<String>();
        if (jsonObject != null) {
            JSONArray jsonJobs = jsonObject.optJSONArray("jobs");
            if (jsonJobs != null) {
                for (Object jsonJob : jsonJobs) {
                    String color = ((JSONObject) jsonJob).optString("color");
                    if (color != null && !color.startsWith("blue") && !color.startsWith("grey")
                            && !color.startsWith("disabled")) {
                        String url = ((JSONObject) jsonJob).getString("url");
                        String jobId = ((JSONObject) jsonJob).getString("name");
                        res.addAll(retrieveJobs(jobId, url, fetcher));
                        retrievedJobs.add(jobId);
                    }
                }
            }
        }
        if (oldData != null) {
            // retrieve status for old builds that are not part of the new
            // results: might be because their status is ok now
            for (Map<String, Serializable> item : oldData) {
                String jobId = (String) item.get("job_id");
                String url = (String) item.get("job_url");
                if (!retrievedJobs.contains(item.get("job_id"))) {
                    res.addAll(retrieveJobs(jobId, url, fetcher));
                }
            }
        }
        return res;
    }

    protected List<Map<String, Serializable>> retrieveJobs(String jobId, String url, JenkinsJobsFetcher fetcher)
            throws IOException {
        List<Map<String, Serializable>> res = new ArrayList<Map<String, Serializable>>();
        Map<String, Serializable> job = new HashMap<String, Serializable>();
        job.put("job_id", jobId);
        job.put("job_url", url);
        List<Map<String, Serializable>> subJobs = null;
        if (fetcher != null && url != null) {
            // retrieve additional info for each failing job,
            // fetching the whole state in one query using the
            // "depth" attribute is more costly
            JSONObject jsonBuild = fetcher.retrieveJSONObject(url.trim() + "lastCompletedBuild/api/json");
            if (jsonBuild != null) {
                job.putAll(convertBuild(jsonBuild));
                subJobs = convertMultiOSDBJobs(jobId, jsonBuild, fetcher);
            } else {
                // at least fill the status as "unknown"
                job.put("type", "");
            }
        }
        if (subJobs != null && !subJobs.isEmpty()) {
            // do not add the main job for multi jobs
            res.addAll(subJobs);
        } else {
            res.add(job);
        }
        return res;
    }

    public List<Map<String, Serializable>> convertMultiOSDBJobs(String parentBuildId, JSONObject jsonParentBuild,
            JenkinsJobsFetcher fetcher) throws IOException {
        List<Map<String, Serializable>> res = new ArrayList<Map<String, Serializable>>();
        if (jsonParentBuild.containsKey("runs")) {
            // multiosdb job => retrieve info from subjobs
            JSONArray runs = jsonParentBuild.optJSONArray("runs");
            String parentBuildUrl = jsonParentBuild.getString("url");
            String parentUrl = removeBuildNumber(parentBuildUrl);
            if (runs != null) {
                for (Object jsonRun : runs) {
                    if (jsonRun != null && ((JSONObject) jsonRun).has("url")) {
                        String runUrl = ((JSONObject) jsonRun).getString("url");
                        if (runUrl != null) {
                            Map<String, Serializable> runJob = new HashMap<String, Serializable>();
                            if (runUrl.startsWith(parentUrl)) {
                                // parse it and make it the id
                                String runJobId = runUrl.substring(parentUrl.length());
                                runJobId = runJobId.substring(0, runJobId.indexOf("/"));
                                runJob.put("job_id", parentBuildId + "#" + runJobId);
                                // remove build number from job URL
                                String subUrl = removeBuildNumber(runUrl);
                                runJob.put("job_url", subUrl);
                                if (fetcher != null) {
                                    JSONObject jsonRunBuild = fetcher.retrieveJSONObject(runUrl + "api/json");
                                    if (jsonRunBuild != null) {
                                        runJob.putAll(convertBuild(jsonRunBuild));
                                    }
                                }
                                String failureType = (String) runJob.get("type");
                                if (!"SUCCESS".equals(failureType)) {
                                    // ignore sub jobs that are ok
                                    res.add(runJob);
                                }
                            } else {
                                // ignore for now...
                                log.warn("Ignoring failing job at " + runUrl);
                            }
                        }
                    }
                }
            }
        }
        return res;
    }

    protected String removeBuildNumber(String url) {
        String res = url;
        if (res.endsWith("/")) {
            res = res.substring(0, res.length() - 1);
        }
        res = res.substring(0, res.lastIndexOf("/") + 1);
        return res;
    }

    public Map<String, Serializable> convertBuild(JSONObject jsonBuild) throws IOException {
        Map<String, Serializable> build = new HashMap<String, Serializable>();
        // get build number
        build.put("build_number", String.valueOf(jsonBuild.optInt("number")));
        String comment = null;
        // get claim info
        JSONArray actions = jsonBuild.optJSONArray("actions");
        if (actions != null) {
            for (Object jsonAction : actions) {
                if (jsonAction != null && ((JSONObject) jsonAction).has("claimed")) {
                    JSONObject claim = (JSONObject) jsonAction;
                    if (claim.optBoolean("claimed")) {
                        build.put("claimer", claim.optString("claimedBy"));
                        String reason = claim.optString("reason");
                        if (!isEmpty(reason)) {
                            comment = "Claim reason: " + reason;
                        }
                    }
                    break;
                }
            }
        }
        String description = jsonBuild.optString("description");
        if (!isEmpty(description)) {
            if (comment != null) {
                comment += "\n\n";
            } else {
                comment = "";
            }
            comment += "Description: " + description;
        }
        build.put("comment", comment);
        // get culprits
        ArrayList<String> culprits = new ArrayList<String>();
        JSONArray jsonCulprits = jsonBuild.optJSONArray("culprits");
        if (jsonCulprits != null) {
            for (Object jsonCulprit : jsonCulprits) {
                if (jsonCulprit != null) {
                    String name = ((JSONObject) jsonCulprit).optString("fullName");
                    if (!isEmpty(name) && !"jenkins".equals(name)) {
                        culprits.add(name);
                    }
                }
            }
        }
        build.put("culprits", culprits);
        // get result
        build.put("type", jsonBuild.optString("result"));
        return build;
    }

    public List<Map<String, Serializable>> mergeData(List<Map<String, Serializable>> oldData,
            List<Map<String, Serializable>> newData) {
        // reset counters and merged data
        newFailingCount = 0;
        fixedCount = 0;
        unchangedCount = 0;
        mergedData = null;

        // gather up all old info, and use a map for easier reference
        HashMap<String, Map<String, Serializable>> res = new LinkedHashMap<String, Map<String, Serializable>>();
        if (oldData != null) {
            for (Map<String, Serializable> item : oldData) {
                res.put((String) item.get("job_id"), item);
            }
        }

        // add up new values and merge if already in the existing list
        if (newData != null) {
            for (Map<String, Serializable> item : newData) {
                String id = (String) item.get("job_id");
                String build_number = (String) item.get("build_number");
                if (res.containsKey(id)) {
                    Map<String, Serializable> oldItem = res.get(id);
                    String oldBuildNumber = String.valueOf(oldItem.get("build_number"));
                    if (build_number != null && build_number.equals(oldBuildNumber)) {
                        // already the same job => update claimer and comment
                        // override claimer and comments
                        oldItem.put("claimer", item.get("claimer"));
                        oldItem.put("comment", item.get("comment"));
                        unchangedCount++;
                    } else {
                        oldItem.put("updated_build_number", build_number);
                        String oldType = (String) oldItem.get("updated_type");
                        String newType = (String) item.get("type");
                        oldItem.put("updated_type", newType);
                        oldItem.put("updated_comment", item.get("comment"));
                        // only override claimer
                        oldItem.put("claimer", item.get("claimer"));
                        if ("SUCCESS".equals(newType) && !"SUCCESS".equals(oldType)) {
                            fixedCount++;
                        }
                    }
                    res.put(id, oldItem);
                } else {
                    if (oldData != null && !oldData.isEmpty()) {
                        item.put("newly_failing", "true");
                    }
                    newFailingCount++;
                    res.put(id, item);
                }
            }
        }

        unchangedCount = res.size() - (fixedCount + newFailingCount);

        mergedData = new ArrayList<Map<String, Serializable>>(res.values());
        return mergedData;
    }

    public int getNewFailingCount() {
        return newFailingCount;
    }

    public int getFixedCount() {
        return fixedCount;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }

    public List<Map<String, Serializable>> getMergedData() {
        return mergedData;
    }

    protected boolean isEmpty(String value) {
        return StringUtils.isBlank(value) || "null".equals(value);
    }

}
