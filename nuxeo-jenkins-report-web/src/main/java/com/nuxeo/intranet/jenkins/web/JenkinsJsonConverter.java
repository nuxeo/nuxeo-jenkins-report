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
 * Convertes json data retrieved from Jenkins into data that can be fed to
 * document properties.
 *
 * @since 5.6
 */
public class JenkinsJsonConverter {

    private static final Log log = LogFactory.getLog(JenkinsJsonConverter.class);

    public static List<Map<String, Serializable>> convertJobs(
            JSONObject jsonObject, JenkinsJobsFetcher fetcher)
            throws IOException {
        List<Map<String, Serializable>> res = new ArrayList<Map<String, Serializable>>();
        if (jsonObject != null) {
            JSONArray jsonJobs = jsonObject.optJSONArray("jobs");
            if (jsonJobs != null) {
                for (Object jsonJob : jsonJobs) {
                    String color = ((JSONObject) jsonJob).getString("color");
                    if (color != null && !color.startsWith("blue")
                            && !color.startsWith("grey")
                            && !color.startsWith("disabled")) {
                        Map<String, Serializable> job = new HashMap<String, Serializable>();
                        String url = ((JSONObject) jsonJob).getString("url");
                        String jobId = ((JSONObject) jsonJob).getString("name");
                        job.put("job_id", jobId);
                        job.put("job_url", url);
                        List<Map<String, Serializable>> subJobs = null;
                        if (fetcher != null && url != null) {
                            // retrieve additional info for each failing job,
                            // fetching the whole state in one query using the
                            // "depth" attribute is more costly
                            JSONObject jsonBuild = fetcher.retrieveJSONObject(url.trim()
                                    + "lastCompletedBuild/api/json");
                            if (jsonBuild != null) {
                                job.putAll(convertBuild(jsonBuild));
                                subJobs = convertMultiOSDBJobs(jobId,
                                        jsonBuild, fetcher);
                            }
                        }
                        if (subJobs != null && !subJobs.isEmpty()) {
                            // do not add the main job for multi jobs
                            res.addAll(subJobs);
                        } else {
                            res.add(job);
                        }
                    }
                }
            }
        }
        return res;
    }

    public static List<Map<String, Serializable>> convertMultiOSDBJobs(
            String parentBuildId, JSONObject jsonParentBuild,
            JenkinsJobsFetcher fetcher) throws IOException {
        List<Map<String, Serializable>> res = new ArrayList<Map<String, Serializable>>();
        if (jsonParentBuild.containsKey("runs")) {
            // multiosdb job => retrieve info from subjobs
            JSONArray runs = jsonParentBuild.optJSONArray("runs");
            if (runs != null) {
                for (Object jsonRun : runs) {
                    if (jsonRun != null && ((JSONObject) jsonRun).has("url")) {
                        String runUrl = ((JSONObject) jsonRun).getString("url");
                        if (runUrl != null) {
                            Map<String, Serializable> runJob = new HashMap<String, Serializable>();
                            if (runUrl.contains("./")) {
                                // parse it and make it
                                // the id
                                String runJobId = runUrl.substring(runUrl.indexOf("./") + 2);
                                runJobId = runJobId.substring(0,
                                        runJobId.indexOf("/"));
                                runJob.put("job_id", parentBuildId + "#"
                                        + runJobId);
                                // remove build number from job URL
                                String subUrl = runUrl;
                                if (subUrl.endsWith("/")) {
                                    subUrl = subUrl.substring(0,
                                            subUrl.length() - 1);
                                }
                                subUrl = subUrl.substring(0,
                                        subUrl.lastIndexOf("/") + 1);
                                runJob.put("job_url", subUrl);
                                if (fetcher != null) {
                                    JSONObject jsonRunBuild = fetcher.retrieveJSONObject(runUrl
                                            + "api/json");
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

    public static Map<String, Serializable> convertBuild(JSONObject jsonBuild)
            throws IOException {
        Map<String, Serializable> build = new HashMap<String, Serializable>();
        // get build number
        build.put("build_number", String.valueOf(jsonBuild.optInt("number")));
        String comment = null;
        // get claim info
        JSONArray actions = jsonBuild.optJSONArray("actions");
        if (actions != null) {
            for (Object jsonAction : actions) {
                if (jsonAction != null
                        && ((JSONObject) jsonAction).has("claimed")) {
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

    public static List<Map<String, Serializable>> mergeData(
            List<Map<String, Serializable>> oldData,
            List<Map<String, Serializable>> newData) {
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
                    if (build_number != null
                            && build_number.equals(oldBuildNumber)) {
                        // already the same job => update claimer and comment
                        // override claimer and comments
                        oldItem.put("claimer", item.get("claimer"));
                        oldItem.put("comment", item.get("comment"));
                        res.put(id, oldItem);
                    } else {
                        oldItem.put("updated_build_number", build_number);
                        oldItem.put("updated_type", item.get("type"));
                        // override claimer and comments
                        oldItem.put("claimer", item.get("claimer"));
                        // merge comment
                        String oldComment = String.valueOf(oldItem.get("comment"));
                        String newComent = String.valueOf(item.get("comment"));
                        if (oldComment != null) {
                            if (newComent != null
                                    && !newComent.equals(oldComment)) {
                                String mergedComment = String.format(
                                        "Comments for build %s:\n%s\n\nComments for build %s:\n%s",
                                        oldBuildNumber, oldComment,
                                        build_number, newComent);
                                oldItem.put("comment", mergedComment);
                            }
                        } else if (newComent != null) {
                            oldItem.put("comment", newComent);
                        }
                        oldItem.put("cause", item.get("code"));
                        res.put(id, oldItem);
                    }
                } else {
                    res.put(id, item);
                }
            }
        }

        return new ArrayList<Map<String, Serializable>>(res.values());
    }

    protected static boolean isEmpty(String value) {
        return StringUtils.isBlank(value) || "null".equals(value);
    }

}
