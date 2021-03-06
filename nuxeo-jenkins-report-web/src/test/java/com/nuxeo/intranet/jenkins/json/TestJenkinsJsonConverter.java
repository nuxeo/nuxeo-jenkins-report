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
package com.nuxeo.intranet.jenkins.json;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.nuxeo.common.utils.FileUtils;

import com.nuxeo.intranet.jenkins.web.JenkinsJsonConverter;

import net.sf.json.JSONObject;

/**
 * @since 5.6
 */
public class TestJenkinsJsonConverter {

    protected JSONObject getJsonBuild(String jsonPath) throws Exception {
        try (InputStream stream = new FileInputStream(FileUtils.getResourcePathFromContext(jsonPath))) {
            return JSONObject.fromObject(IOUtils.toString(stream));
        }
    }

    @Test
    public void testJobsConverter() throws Exception {
        JSONObject json = getJsonBuild("jobs.json");
        // use a null fetcher: each job info will not be retrieved
        JenkinsJsonConverter cv = new JenkinsJsonConverter();
        List<Map<String, Serializable>> res = cv.convertJobs(json, null, null);
        assertEquals(59, res.size());

        Map<String, Serializable> failing = res.get(0);
        assertEquals("https://qa.nuxeo.org/jenkins/job/addons_FT-nuxeo-platform-faceted-search-master-tomcat/",
                failing.get("job_url"));
        assertEquals("addons_FT-nuxeo-platform-faceted-search-master-tomcat", failing.get("job_id"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testBuildConverter() throws Exception {
        JSONObject json = getJsonBuild("build.json");

        // use a null fetcher: each job info will not be retrieved
        JenkinsJsonConverter cv = new JenkinsJsonConverter();
        Map<String, Serializable> build = cv.convertBuild(json);
        assertNotNull(build);
        assertEquals("702", build.get("build_number"));
        assertEquals("UNSTABLE", build.get("type"));
        assertEquals("mcedica", build.get("claimer"));
        assertEquals("Claim reason: checking\n\nDescription: test comment", build.get("comment"));
        assertNull(build.get("updated_comment"));
        assertEquals(1, ((List) build.get("culprits")).size());
        assertEquals("Laurent Doguin <ldoguin@nuxeo.com>", ((List) build.get("culprits")).get(0));
    }

    protected JenkinsJsonConverter mergeData(Map<String, Serializable> oldBuild, Map<String, Serializable> newBuild) {
        JenkinsJsonConverter cv = new JenkinsJsonConverter();
        List<Map<String, Serializable>> oldData = new ArrayList<Map<String, Serializable>>();
        oldData.add(oldBuild);
        List<Map<String, Serializable>> newData = new ArrayList<Map<String, Serializable>>();
        newData.add(newBuild);
        cv.mergeData(oldData, newData);
        return cv;
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testMergeBuildConverterNoChange() throws Exception {
        JSONObject json = getJsonBuild("build.json");
        JenkinsJsonConverter cv = new JenkinsJsonConverter();
        Map<String, Serializable> build = cv.convertBuild(json);
        Map<String, Serializable> newBuild = cv.convertBuild(json);

        // merge with same data and check nothing has changed
        cv = mergeData(build, newBuild);
        List<Map<String, Serializable>> res = cv.getMergedData();

        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals(0, cv.getNewFailingCount());
        assertEquals(0, cv.getFixedCount());
        assertEquals(1, cv.getUnchangedCount());

        Map<String, Serializable> mergedBuild = res.get(0);
        assertNotNull(mergedBuild);
        assertEquals("702", mergedBuild.get("build_number"));
        assertEquals("UNSTABLE", mergedBuild.get("type"));
        assertEquals("mcedica", mergedBuild.get("claimer"));
        assertEquals("Claim reason: checking\n\n" + "Description: test comment", mergedBuild.get("comment"));
        assertNull(mergedBuild.get("updated_comment"));
        assertEquals(1, ((List) mergedBuild.get("culprits")).size());
        assertEquals("Laurent Doguin <ldoguin@nuxeo.com>", ((List) mergedBuild.get("culprits")).get(0));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testMergeBuildConverterBuildChange() throws Exception {
        JenkinsJsonConverter cv = new JenkinsJsonConverter();
        JSONObject json = getJsonBuild("build.json");
        Map<String, Serializable> build = cv.convertBuild(json);
        json = getJsonBuild("modified_build.json");
        Map<String, Serializable> newBuild = cv.convertBuild(json);

        // merge with same data and check nothing has changed
        cv = mergeData(build, newBuild);
        List<Map<String, Serializable>> res = cv.getMergedData();

        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals(0, cv.getNewFailingCount());
        assertEquals(0, cv.getFixedCount());
        assertEquals(1, cv.getUnchangedCount());

        Map<String, Serializable> mergedBuild = res.get(0);
        assertNotNull(mergedBuild);
        assertEquals("702", mergedBuild.get("build_number"));
        assertEquals("UNSTABLE", mergedBuild.get("type"));
        assertEquals("mcedica", mergedBuild.get("claimer"));
        assertEquals("Claim reason: checking with modified claim reason\n\n" + "Description: test comment modified",
                mergedBuild.get("comment"));
        assertNull(mergedBuild.get("updated_comment"));
        assertEquals(1, ((List) mergedBuild.get("culprits")).size());
        assertEquals("Laurent Doguin <ldoguin@nuxeo.com>", ((List) mergedBuild.get("culprits")).get(0));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testMergeBuildConverterBuildNewer() throws Exception {
        JenkinsJsonConverter cv = new JenkinsJsonConverter();
        JSONObject json = getJsonBuild("build.json");
        Map<String, Serializable> build = cv.convertBuild(json);
        json = getJsonBuild("new_build.json");
        Map<String, Serializable> newBuild = cv.convertBuild(json);

        // merge with changed data with new build, and check changes
        cv = mergeData(build, newBuild);
        List<Map<String, Serializable>> res = cv.getMergedData();

        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals(0, cv.getNewFailingCount());
        assertEquals(0, cv.getFixedCount());
        assertEquals(1, cv.getUnchangedCount());

        Map<String, Serializable> mergedBuild = res.get(0);
        assertNotNull(mergedBuild);
        assertEquals("702", mergedBuild.get("build_number"));
        assertEquals("UNSTABLE", mergedBuild.get("type"));
        assertEquals("mcedica", mergedBuild.get("claimer"));
        assertEquals("Claim reason: checking\n\n" + "Description: test comment", mergedBuild.get("comment"));
        assertEquals("Claim reason: checking new stuff\n\n" + "Description: test new comment",
                mergedBuild.get("updated_comment"));
        assertEquals(1, ((List) mergedBuild.get("culprits")).size());
        assertEquals("Laurent Doguin <ldoguin@nuxeo.com>", ((List) mergedBuild.get("culprits")).get(0));
    }

    @Test
    public void testMultiOSDBBuildConverter() throws Exception {
        InputStream stream = new FileInputStream(FileUtils.getResourcePathFromContext("multiosdb_build.json"));
        JSONObject json = JSONObject.fromObject(IOUtils.toString(stream));

        // use a null fetcher: each job info will not be retrieved
        JenkinsJsonConverter cv = new JenkinsJsonConverter();
        List<Map<String, Serializable>> builds = cv.convertMultiOSDBJobs("FT-nuxeo-5.6.0-selenium-dm-tomcat", json,
                null);
        assertNotNull(builds);
        assertEquals(9, builds.size());
        assertEquals(
                "https://qa.nuxeo.org/jenkins/job/FT-nuxeo-5.6.0-selenium-dm-tomcat-multiosdb/Slave=MULTIDB_LINUX,dbprofile=mssql/",
                builds.get(0).get("job_url"));
        assertEquals("FT-nuxeo-5.6.0-selenium-dm-tomcat#Slave=MULTIDB_LINUX,dbprofile=mssql",
                builds.get(0).get("job_id"));
    }

}
