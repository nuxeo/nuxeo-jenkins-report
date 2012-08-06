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
package com.nuxeo.intranet.jenkins.json;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.junit.Test;
import org.nuxeo.common.utils.FileUtils;

import com.nuxeo.intranet.jenkins.web.JenkinsJsonConverter;

/**
 * @since 5.6
 */
public class TestJenkinsJsonConverter {

    @Test
    public void testJobsConverter() throws Exception {
        InputStream stream = new FileInputStream(
                FileUtils.getResourcePathFromContext("jobs.json"));
        JSONObject json = JSONObject.fromObject(FileUtils.read(stream));

        // use a null fetcher: each job info will not be retrieved
        List<Map<String, Serializable>> res = JenkinsJsonConverter.convertJobs(
                json, null);
        assertEquals(57, res.size());

        Map<String, Serializable> failing = res.get(0);
        assertEquals(
                "https://qa.nuxeo.org/jenkins/job/addons_FT-nuxeo-platform-faceted-search-master-tomcat/",
                failing.get("job_url"));
        assertEquals("addons_FT-nuxeo-platform-faceted-search-master-tomcat",
                failing.get("job_id"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testBuildConverter() throws Exception {
        InputStream stream = new FileInputStream(
                FileUtils.getResourcePathFromContext("build.json"));
        JSONObject json = JSONObject.fromObject(FileUtils.read(stream));

        // use a null fetcher: each job info will not be retrieved
        Map<String, Serializable> build = JenkinsJsonConverter.convertBuild(json);
        assertNotNull(build);
        assertEquals("702", build.get("build_number"));
        assertEquals("UNSTABLE", build.get("type"));
        assertEquals("mcedica", build.get("claimer"));
        assertEquals("Claim reason: checking\n\nDescription: test comment",
                build.get("comment"));
        assertEquals(1, ((List) build.get("culprits")).size());
        assertEquals("Laurent Doguin <ldoguin@nuxeo.com>",
                ((List) build.get("culprits")).get(0));
    }
}
