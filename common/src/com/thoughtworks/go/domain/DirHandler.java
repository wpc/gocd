/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.domain;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.servlet.http.HttpServletResponse;

import com.thoughtworks.go.util.FileUtil;
import com.thoughtworks.go.util.ZipUtil;
import com.thoughtworks.go.validation.ChecksumValidator;
import com.thoughtworks.go.work.DefaultGoPublisher;
import com.thoughtworks.go.work.GoPublisher;
import org.apache.log4j.Logger;

import static com.thoughtworks.go.util.CachedDigestUtils.md5Hex;

public class DirHandler implements FetchHandler {
    public String getSrcFile() {
        return srcFile;
    }

    private final String srcFile;
    private final File destOnAgent;
    private static final Logger LOG = Logger.getLogger(DirHandler.class);
    private ArtifactMd5Checksums artifactMd5Checksums;
    private ChecksumValidationPublisher checksumValidationPublisher;

    public DirHandler(String srcFile, File destOnAgent) {
        this.srcFile = srcFile;
        this.destOnAgent = destOnAgent;
        checksumValidationPublisher = new ChecksumValidationPublisher();
    }

    public String url(String remoteHost, String workingUrl) throws IOException {
        return java.lang.String.format("%s/%s/%s/%s.zip", remoteHost, "remoting", "files", workingUrl);
    }

    public void handle(InputStream stream) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(stream);
        LOG.info(String.format("[Agent Fetch Artifact] Downloading from '%s' to '%s'. Will read from Socket stream to compute MD5 and write to file", srcFile, destOnAgent.getAbsolutePath()));

        long before = System.currentTimeMillis();
        new ZipUtil(new ZipUtil.ZipEntryHandler() {
            public void handleEntry(ZipEntry entry, InputStream stream) throws IOException {
                LOG.info(String.format("[Agent Fetch Artifact] Downloading a directory from '%s' to '%s'. Handling the entry: '%s'", srcFile, destOnAgent.getAbsolutePath(), entry.getName()));
                new ChecksumValidator(artifactMd5Checksums).validate(getSrcFilePath(entry), md5Hex(stream), checksumValidationPublisher);
            }
        }).unzip(zipInputStream, destOnAgent);
        LOG.info(String.format("[Agent Fetch Artifact] Downloading a directory from '%s' to '%s'. Took: %sms", srcFile, destOnAgent.getAbsolutePath(), System.currentTimeMillis() - before));
    }

    private String getSrcFilePath(ZipEntry entry) {
        String parent = new File(srcFile).getParent();
        return FileUtil.normalizePath(new File(parent, entry.getName()).getPath());
    }

    public boolean handleResult(int httpCode, GoPublisher goPublisher) {
        checksumValidationPublisher.publish(httpCode, destOnAgent, goPublisher);
        return httpCode < HttpServletResponse.SC_BAD_REQUEST;
    }

    public void useArtifactMd5Checksums(ArtifactMd5Checksums artifactMd5Checksums) {
        this.artifactMd5Checksums = artifactMd5Checksums;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DirHandler)) {
            return false;
        }

        DirHandler that = (DirHandler) o;

        if (destOnAgent != null ? !destOnAgent.equals(that.destOnAgent) : that.destOnAgent != null) {
            return false;
        }
        if (srcFile != null ? !srcFile.equals(that.srcFile) : that.srcFile != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = srcFile != null ? srcFile.hashCode() : 0;
        result = 31 * result + (destOnAgent != null ? destOnAgent.hashCode() : 0);
        return result;
    }


    public File getDestOnAgent() {
        return destOnAgent;
    }
}
