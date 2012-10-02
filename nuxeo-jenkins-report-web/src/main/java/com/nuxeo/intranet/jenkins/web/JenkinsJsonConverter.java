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

/**
 * Convertes json data retrieved from Jenkins into data that can be fed to
 * document properties.
 *
 * @since 5.6
 */
public class JenkinsJsonConverter {

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
                            && !color.startsWith("grey")) {
                        Map<String, Serializable> job = new HashMap<String, Serializable>();
                        String url = ((JSONObject) jsonJob).getString("url");
                        job.put("job_id",
                                ((JSONObject) jsonJob).getString("name"));
                        job.put("job_url", url);
                        if (fetcher != null && url != null) {
                            // retrieve additional info for each failing job,
                            // fetching the whole state in one query using the
                            // "depth" attribute is more costly
                            JSONObject jsonBuild = fetcher.retrieveJSONObject(url.trim()
                                    + "lastCompletedBuild/api/json");
                            if (jsonBuild != null) {
                                job.putAll(convertBuild(jsonBuild));
                            }
                        }
                        res.add(job);
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
        // add up new values and merge if already in the list existing
        if (newData != null) {
            for (Map<String, Serializable> item : newData) {
                String id = (String) item.get("job_id");
                String build_number = (String) item.get("build_number");
                if (res.containsKey(id)) {
                    Map<String, Serializable> oldItem = res.get(id);
                    if (build_number != null
                            && build_number.equals(String.valueOf(oldItem.get("build_number")))) {
                        // already the same job => keep the old one
                    } else {
                        oldItem.put("updated_build_number", build_number);
                        oldItem.put("updated_type", item.get("type"));
                        // override claimer and comments
                        oldItem.put("claimer", item.get("claimer"));
                        oldItem.put("comment", item.get("comment"));
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
