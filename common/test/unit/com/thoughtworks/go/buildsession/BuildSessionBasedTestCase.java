package com.thoughtworks.go.buildsession;

import com.thoughtworks.go.domain.ArtifactsRepositoryStub;
import com.thoughtworks.go.domain.BuildCommand;
import com.thoughtworks.go.domain.BuildStateReporterStub;
import com.thoughtworks.go.domain.JobResult;
import com.thoughtworks.go.remote.work.HttpServiceStub;
import com.thoughtworks.go.util.TestFileUtil;
import com.thoughtworks.go.util.TestingClock;
import com.thoughtworks.go.util.command.InMemoryConsumer;
import org.apache.commons.lang.text.StrLookup;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class BuildSessionBasedTestCase {
    protected BuildStateReporterStub statusReporter;
    protected Map<String, String> buildVariables;
    protected File sandbox;
    protected ArtifactsRepositoryStub artifactsRepository;
    protected InMemoryConsumer console;
    protected HttpServiceStub httpService;

    @Before
    public void superSetup() {
        statusReporter = new BuildStateReporterStub();
        buildVariables = new HashMap<>();
        artifactsRepository = new ArtifactsRepositoryStub();
        sandbox = TestFileUtil.createTempFolder(UUID.randomUUID().toString());
        console = new InMemoryConsumer();
        httpService = new HttpServiceStub();
    }

    @After
    public void superTeardown() {
        TestFileUtil.cleanTempFiles();
    }

    protected BuildSession newBuildSession() {
        return new BuildSession("build1",
                statusReporter,
                console,
                StrLookup.mapLookup(buildVariables),
                artifactsRepository, httpService, new TestingClock(), sandbox);
    }

    protected String buildInfo() {
        return "\n *** current build info *** \n"
                + "build status: " + statusReporter.status() + "\n"
                + "build result: " + statusReporter.results() + "\n"
                + "build console output: \n"
                + console.output()
                + "\n******";
    }

    protected void runBuild(BuildSession buildSession, BuildCommand command, JobResult expectedResult) {
        JobResult result = buildSession.build(command);
        assertThat(buildInfo(), result, is(expectedResult));
    }

    protected void runBuild(BuildCommand command, JobResult expectedResult) {
        runBuild(newBuildSession(), command, expectedResult);
    }
}

