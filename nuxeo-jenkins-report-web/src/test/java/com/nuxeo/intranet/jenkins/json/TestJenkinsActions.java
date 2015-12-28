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

import junit.framework.Assert;

import org.junit.Test;

import com.nuxeo.intranet.jenkins.web.JenkinsJobsActions;

public class TestJenkinsActions {

    @Test
    public void testJobsConverter() throws Exception {
        String jiraUrl = "https://jira.nuxeo.com/browse/";
        JenkinsJobsActions actions = new JenkinsJobsActions();
        String[] jiraProjects = new String[] { "NXP", "NXBT" };

        String toConvert = "This is a text mentioning NXP-34345, NXP-5630 "
                + "(and also NXP-2343) and NXBT-23434 that are issues from "
                + "the NXP and NXBT project. The regexp should wrap NXBT-Something.";
        Assert.assertEquals(
                actions.getConvertedJobComment(toConvert, jiraUrl, jiraProjects),
                "This is a text mentioning <a href=\"https://jira.nuxeo.com/browse/NXP-34345\" target=\"_blank\">NXP-34345</a>, "
                        + "<a href=\"https://jira.nuxeo.com/browse/NXP-5630\" target=\"_blank\">NXP-5630</a> "
                        + "(and also <a href=\"https://jira.nuxeo.com/browse/NXP-2343\" target=\"_blank\">NXP-2343</a>) "
                        + "and <a href=\"https://jira.nuxeo.com/browse/NXBT-23434\" target=\"_blank\">NXBT-23434</a> that "
                        + "are issues from the NXP and NXBT project. The regexp should wrap NXBT-Something.");

        toConvert = "NXP-123";
        Assert.assertEquals(actions.getConvertedJobComment(toConvert, jiraUrl, jiraProjects),
                "<a href=\"https://jira.nuxeo.com/browse/NXP-123\" target=\"_blank\">NXP-123</a>");

        toConvert = "nxp-123";
        Assert.assertEquals(actions.getConvertedJobComment(toConvert, jiraUrl, jiraProjects),
                "<a href=\"https://jira.nuxeo.com/browse/NXP-123\" target=\"_blank\">NXP-123</a>");

        toConvert = null;
        Assert.assertNull(actions.getConvertedJobComment(toConvert, jiraUrl, jiraProjects));
        toConvert = "voila voila\n voila";
        Assert.assertEquals(actions.getConvertedJobComment(toConvert, jiraUrl, jiraProjects),
                "voila voila<br />\n voila");
        toConvert = "NXP-123";
        Assert.assertEquals(actions.getConvertedJobComment(toConvert, null, jiraProjects), "NXP-123");
        Assert.assertEquals(actions.getConvertedJobComment(toConvert, jiraUrl, null), "NXP-123");
    }

}
