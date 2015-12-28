/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
package com.nuxeo.intranet.jenkins.blobholder;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.BlobHolderFactory;
import org.nuxeo.ecm.core.api.blobholder.DocumentStringBlobHolder;

/**
 * Blob holder factory for the jenkins report document, to handle Drive edition.
 *
 * @since 4.0.0
 */
public class JenkinsReportBlobHolderFactory implements BlobHolderFactory {

    @Override
    public BlobHolder getBlobHolder(DocumentModel doc) {
        String mt = (String) doc.getPropertyValue("jenkinsreport:duty_comments_mimetype");
        if (StringUtils.isBlank(mt)) {
            mt = "text/html"; // BBB
        }
        return new DocumentStringBlobHolder(doc, "dc:description", mt);
    }

}
