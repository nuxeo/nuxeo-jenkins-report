/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
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
