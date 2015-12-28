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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;

/**
 * Miscellaneous rendering helpers
 *
 * @since 5.7
 */
@Name("jenkinsJobsActions")
@Scope(EVENT)
public class JenkinsJobsActions {

    /**
     * Converts a job comment to HTML and parses JIRA issues to turn them into links.
     *
     * @param jiraUrl TODO
     */
    public String getConvertedJobComment(String toConvert, String jiraURL, String[] jiraProjects) {
        if (toConvert == null) {
            return null;
        }

        if (StringUtils.isBlank(jiraURL) || jiraProjects == null || jiraProjects.length == 0) {
            toConvert = toConvert.replace("\n", "<br />\n");
            return toConvert;
        }

        String res = "";
        String regexp = "\\b(" + StringUtils.join(jiraProjects, "|") + ")-\\d+\\b";
        Pattern pattern = Pattern.compile(regexp, Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(toConvert);
        int lastIndex = 0;
        boolean done = false;
        while (m.find()) {
            String jiraIssue = m.group(0);
            res += toConvert.substring(lastIndex, m.start()) + getJiraUrlTag(jiraURL, jiraIssue);
            lastIndex = m.end();
            done = true;
        }
        if (done) {
            res += toConvert.substring(lastIndex);
        } else {
            res = toConvert;
        }
        res = res.replace("\n", "<br />\n");
        return res;
    }

    protected String getJiraUrlTag(String jiraURL, String jiraIssue) {
        return "<a href=\"" + jiraURL + jiraIssue.toUpperCase() + "\" target=\"_blank\">" + jiraIssue.toUpperCase()
                + "</a>";
    }
}
