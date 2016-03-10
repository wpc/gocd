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

package com.thoughtworks.go.domain.builder;

import com.thoughtworks.go.buildsession.BuildSession;
import com.thoughtworks.go.domain.*;
import com.thoughtworks.go.remote.work.HttpServiceStub;
import com.thoughtworks.go.util.*;
import com.thoughtworks.go.util.command.InMemoryConsumer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.text.StrLookup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

import static com.thoughtworks.go.util.MapBuilder.map;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

public class FetchArtifactBuilderBuildCommandTest {
    private File zip;
    private List<File> toClean = new ArrayList<File>();
    private static final String URL = "http://10.18.7.51:8153/go/remoting/files/cruise/1.0.2341/dev/1/windows-3/cruise-output/console.log";

    private File dest;
    private InMemoryConsumer console;
    private HttpServiceStub httpService;


    @Before
    public void setUp() throws Exception {
        console = new InMemoryConsumer();
        httpService = new HttpServiceStub();
        File folder = TestFileUtil.createTempFolder("log");
        File consolelog = new File(folder, "console.log");
        folder.mkdirs();
        consolelog.createNewFile();

        zip = new ZipUtil().zip(folder, TestFileUtil.createUniqueTempFile(folder.getName()), Deflater.NO_COMPRESSION);
        toClean.add(folder);
        toClean.add(zip);
        dest = new File("dest");
        dest.mkdirs();
        toClean.add(dest);
    }

    @After
    public void tearDown() throws Exception {
        for (File fileToClean : toClean) {
            FileUtils.deleteQuietly(fileToClean);
        }
    }

    @Test
    public void shouldUnzipWhenFetchingFolder() throws Exception {
        httpService.setupDownload(format("%s/remoting/files/cruise/1/dev/1/windows/log.zip", new URLService().baseRemoteURL()), zip);

        File destOnAgent = new File("pipelines/cruise/", dest.getPath());
        toClean.add(destOnAgent);

        FetchArtifactBuilder builder = getBuilder(new JobIdentifier("cruise", -10, "1", "dev", "1", "windows", 1L), "log", dest.getPath(), new DirHandler("log",destOnAgent));
        runBuilder(builder, JobResult.Passed);
        assertDownloaded(destOnAgent);
    }

    @Test
    public void shouldGiveWarningWhenMd5FileNotExists() throws Exception {
        httpService.setupDownload(format("%s/remoting/files/cruise/1/dev/1/windows/a.jar", new URLService().baseRemoteURL()), "some content");

        File artifactOnAgent = new File("pipelines/cruise/foo/a.jar");
        toClean.add(artifactOnAgent);

        FetchArtifactBuilder builder = getBuilder(new JobIdentifier("cruise", -1, "1", "dev", "1", "windows", 1L), "a.jar", "foo", new FileHandler(artifactOnAgent, "a.jar"));

        runBuilder(builder, JobResult.Passed);
        assertThat(artifactOnAgent.isFile(), is(true));
        assertThat(console.output(), containsString("[WARN] The md5checksum property file was not found"));
    }

    @Test
    public void shouldFailBuildWhenChecksumNotValidForArtifact() throws Exception {
        httpService.setupDownload(format("%s/remoting/files/cruise/1/dev/1/windows/cruise-output/md5.checksum", new URLService().baseRemoteURL()), "a.jar=invalid-checksum");
        httpService.setupDownload(format("%s/remoting/files/cruise/1/dev/1/windows/a.jar", new URLService().baseRemoteURL()), "some content");

        File artifactOnAgent = new File("pipelines/cruise/foo/a.jar");
        toClean.add(artifactOnAgent);

        FetchArtifactBuilder builder = getBuilder(new JobIdentifier("cruise", -1, "1", "dev", "1", "windows", 1L), "a.jar", "foo", new FileHandler(artifactOnAgent, "a.jar"));
        runBuilder(builder, JobResult.Failed);
        assertThat(console.output(), containsString("[ERROR] Verification of the integrity of the artifact [a.jar] failed"));
        assertThat(artifactOnAgent.isFile(), is(true));
    }

    @Test
    public void shouldBuildWhenChecksumValidForArtifact() throws Exception {
        httpService.setupDownload(format("%s/remoting/files/cruise/1/dev/1/windows/cruise-output/md5.checksum", new URLService().baseRemoteURL()), "a.jar=9893532233caff98cd083a116b013c0b");
        httpService.setupDownload(format("%s/remoting/files/cruise/1/dev/1/windows/a.jar", new URLService().baseRemoteURL()), "some content");

        File artifactOnAgent = new File("pipelines/cruise/foo/a.jar");
        toClean.add(artifactOnAgent);

        FetchArtifactBuilder builder = getBuilder(new JobIdentifier("cruise", -1, "1", "dev", "1", "windows", 1L), "a.jar", "foo", new FileHandler(artifactOnAgent, "a.jar"));
        runBuilder(builder, JobResult.Passed);
        assertThat(console.output(), containsString(format("Saved artifact to [%s] after verifying the integrity of its contents", artifactOnAgent.getPath())));
    }

    @Test
    public void shouldFailBuildAndPrintErrorMessageToConsoleWhenArtifactNotExisit() throws Exception {
        File artifactOnAgent = new File("pipelines/cruise/foo/a.jar");
        toClean.add(artifactOnAgent);

        FetchArtifactBuilder builder = getBuilder(new JobIdentifier("cruise", -1, "1", "dev", "1", "windows", 1L), "a.jar", "foo", new FileHandler(artifactOnAgent, "a.jar"));
        runBuilder(builder, JobResult.Failed);
        assertThat(console.output(), not(containsString("Saved artifact")));
        assertThat(console.output(), containsString("Could not fetch artifact"));
    }

    @Test
    public void shouldDownloadWithURLContainsSHA1WhenFileExists() throws Exception {
        File artifactOnAgent = new File("pipelines/cruise/foo/a.jar");
        toClean.add(artifactOnAgent);
        Files.write(Paths.get(artifactOnAgent.getPath()), "foobar".getBytes());
        String sha1 = java.net.URLEncoder.encode(StringUtil.sha1Digest(artifactOnAgent), "UTF-8");

        httpService.setupDownload(format("%s/remoting/files/cruise/1/dev/1/windows/a.jar", new URLService().baseRemoteURL()), "content for url without sha1");

        httpService.setupDownload(format("%s/remoting/files/cruise/1/dev/1/windows/a.jar?sha1=%s", new URLService().baseRemoteURL(), sha1), "content for url with sha1");


        FetchArtifactBuilder builder = getBuilder(new JobIdentifier("cruise", -1, "1", "dev", "1", "windows", 1L), "a.jar", "foo", new FileHandler(artifactOnAgent, "a.jar"));

        runBuilder(builder, JobResult.Passed);
        assertThat(artifactOnAgent.isFile(), is(true));
        assertThat(FileUtil.readContentFromFile(artifactOnAgent), is("content for url with sha1"));
    }


    private void assertDownloaded(File destOnAgent) {
        File logFolder = new File(destOnAgent, "log");
        assertThat(logFolder.exists(), is(true));
        assertThat(logFolder.isDirectory(), is(true));
        assertThat(new File(logFolder, "console.log").exists(), is(true));
        assertThat(destOnAgent.listFiles(), is(new File[]{logFolder}));
    }


    private void runBuilder(FetchArtifactBuilder builder, JobResult expectedResult) {
        BuildCommand buildCommand = builder.buildCommand();
        BuildSession buildSession = new BuildSession("build1", new BuildStateReporterStub(), console, StrLookup.mapLookup(map()), new ArtifactsRepositoryStub(), httpService, new TestingClock());
        JobResult result = buildSession.build(buildCommand);
        assertThat("builder assertion failure: current console: \n" + console.output(), result, is(expectedResult));
    }

    private String getSrc() {
        return "";
    }

    private FetchArtifactBuilder getBuilder(JobIdentifier jobLocator, String srcdir, String dest, FetchHandler handler) {
        return new FetchArtifactBuilder(new RunIfConfigs(), new NullBuilder(), "", jobLocator, srcdir, dest, handler, new ChecksumFileHandler(checksumFile(jobLocator, srcdir, dest)));
    }

    private File checksumFile(JobIdentifier jobIdentifier, String srcdir, String dest) {
        File destOnAgent = new File("pipelines" + '/' + jobIdentifier.getPipelineName() + '/' + dest);
        return new File(destOnAgent, String.format("%s_%s_%s_md5.checksum", jobIdentifier.getPipelineName(), jobIdentifier.getStageName(), jobIdentifier.getBuildName()));
    }

}
