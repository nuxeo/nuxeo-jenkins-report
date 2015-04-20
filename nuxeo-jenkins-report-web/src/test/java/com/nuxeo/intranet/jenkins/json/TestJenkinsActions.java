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
