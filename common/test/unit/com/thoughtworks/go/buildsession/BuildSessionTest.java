/*************************** GO-LICENSE-START*********************************
 * Copyright 2016 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ************************GO-LICENSE-END***********************************/
package com.thoughtworks.go.buildsession;

import com.googlecode.junit.ext.JunitExtRunner;
import com.googlecode.junit.ext.RunIf;
import com.thoughtworks.go.domain.ArtifactsRepositoryStub;
import com.thoughtworks.go.domain.BuildCommand;
import com.thoughtworks.go.domain.BuildStateReporterStub;
import com.thoughtworks.go.domain.JobResult;
import com.thoughtworks.go.junitext.EnhancedOSChecker;
import com.thoughtworks.go.remote.work.HttpServiceStub;
import com.thoughtworks.go.util.*;
import com.thoughtworks.go.util.command.InMemoryConsumer;
import org.apache.commons.lang.text.StrLookup;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import static com.google.common.collect.Iterables.getLast;
import static com.thoughtworks.go.domain.BuildCommand.*;
import static com.thoughtworks.go.domain.JobResult.*;
import static com.thoughtworks.go.domain.JobState.*;
import static com.thoughtworks.go.junitext.EnhancedOSChecker.DO_NOT_RUN_ON;
import static com.thoughtworks.go.junitext.EnhancedOSChecker.WINDOWS;
import static com.thoughtworks.go.matchers.ConsoleOutMatcher.printedAppsMissingInfoOnUnix;
import static com.thoughtworks.go.matchers.ConsoleOutMatcher.printedAppsMissingInfoOnWindows;
import static com.thoughtworks.go.util.ExceptionUtils.bomb;
import static com.thoughtworks.go.util.MapBuilder.map;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertTrue;

@RunWith(JunitExtRunner.class)
public class BuildSessionTest {
    private BuildStateReporterStub statusReporter;
    private Map<String, String> buildVariables;
    private File sanbox;
    private ArtifactsRepositoryStub artifactsRepository;
    private InMemoryConsumer console;
    private HttpServiceStub httpService;

    @Before
    public void setup() {
        statusReporter = new BuildStateReporterStub();
        buildVariables = new HashMap<>();
        artifactsRepository = new ArtifactsRepositoryStub();
        sanbox = TestFileUtil.createTempFolder(UUID.randomUUID().toString());
        console = new InMemoryConsumer();
        httpService = new HttpServiceStub();
    }

    @Test
    public void echoCommandAppendContentToConsole() {
        runBuild(echo("o1", "o2"), Passed);
        assertThat(console.asList(), is(Arrays.asList("o1", "o2")));
    }

    @Test
    public void testReportCurrentStatus() {
        runBuild(compose(
                reportCurrentStatus(Preparing),
                reportCurrentStatus(Building),
                reportCurrentStatus(Completing)), Passed);
        assertThat(statusReporter.status(), is(Arrays.asList(Preparing, Building, Completing, Completed)));
    }

    @Test
    public void testReportCompleting() {
        runBuild(reportCompleting(), Passed);
        assertThat(statusReporter.results(), is(Arrays.asList(Passed, Passed)));
    }

    @Test
    public void resultShouldBeFailedWhenCommandFailed() {
        runBuild(fail("force build failure"), Failed);
        assertThat(statusReporter.singleResult(), is(Failed));
    }

    @Test
    public void composeRunAllSubCommands() {
        runBuild(compose(echo("hello"), echo("world")), Passed);
        assertThat(console.asList(), is(Arrays.asList("hello", "world")));
    }

    @Test
    public void execExecuteExternalCommandAndConnectOutputToBuildConsole() {
        runBuild(exec("echo", "foo"), Passed);
        assertThat(console.lastLine(), is("foo"));
    }

    @Test
    public void execShouldFailIfWorkingDirectoryNotExists() {
        runBuild(exec("echo", "should not show").setWorkingDirectory("not-exists"), Failed);
        assertThat(console.asList().size(), is(1));
        assertThat(console.firstLine(), containsString("not-exists\" is not a directory!"));
    }

    @Test
    public void execUseSystemEnvironmentVariables() {
        runBuild(execEchoEnv(pathSystemEnvName()), Passed);
        assertThat(console.output(), is(System.getenv(pathSystemEnvName())));
    }

    @Test
    public  void execUsePresetEnvs() {
        BuildSession buildSession = newBuildSession();
        buildSession.setEnv("GO_SERVER_URL", "https://far.far.away/go");
        runBuild(buildSession, execEchoEnv("GO_SERVER_URL"), Passed);
        assertThat(console.output(), is("https://far.far.away/go"));
    }

    @Test
    public void execUseExportedEnv() throws IOException {
        runBuild(compose(
                export("foo", "bar", false),
                execEchoEnv("foo")), Passed);
        assertThat(console.lastLine(), is("bar"));
    }

    @Test
    public void execUseExportedEnvWithOverriden() throws Exception {
        runBuild(compose(
                export("answer", "2", false),
                export("answer", "42", false),
                execEchoEnv("answer")), Passed);
        assertThat(console.lastLine(), is("42"));
    }


    @Test
    public void execUseOverriddenSystemEnvValue() throws Exception {
        runBuild(compose(
                export(pathSystemEnvName(), "/foo/bar", false),
                execEchoEnv(pathSystemEnvName())), Passed);
        assertThat(console.lastLine(), is("/foo/bar"));
    }


    @Test
    @RunIf(value = EnhancedOSChecker.class, arguments = {DO_NOT_RUN_ON, WINDOWS})
    public void execExecuteNotExistExternalCommandOnUnix() {
        runBuild(exec("not-not-not-exist"), Failed);
        assertThat(console.output(), printedAppsMissingInfoOnUnix("not-not-not-exist"));
    }

    @Test
    @RunIf(value = EnhancedOSChecker.class, arguments = {EnhancedOSChecker.WINDOWS})
    public void execExecuteNotExistExternalCommandOnWindows() {
        runBuild(exec("not-not-not-exist"), Failed);
        assertThat(console.output(), printedAppsMissingInfoOnWindows("not-not-not-exist"));
    }

    @Test
    public void forceBuildFailWithMessage() {
        runBuild(fail("force failure"), Failed);
        assertThat(console.output(), is("force failure"));
    }

    @Test
    public void shouldNotRunCommandWithRunIfFailedIfBuildIsPassing() {
        runBuild(compose(
                echo("on pass"),
                echo("on failure").runIf("failed")), Passed);
        assertThat(console.asList(), is(Collections.singletonList("on pass")));
    }

    @Test
    public void shouldRunCommandWithRunIfFailedIfBuildIsFailed() {
        runBuild(compose(
                fail("force failure"),
                echo("on failure").runIf("failed")), Failed);
        assertThat(console.lastLine(), is("on failure"));
    }

    @Test
    public void shouldRunCommandWithRunIfAnyRegardlessOfBuildResult() {
        runBuild(compose(
                echo("foo"),
                echo("on passing").runIf("any"),
                fail("force failure"),
                echo("on failure").runIf("any")), Failed);
        assertThat(console.asList(), is(Arrays.asList("foo", "on passing", "force failure", "on failure")));
    }

    @Test
    public void mkdirsShouldCreateDirectoryIfNotExists() {
        File dir = new File(sanbox, "foo");
        runBuild(mkdirs(dir.getPath()), Passed);
        assertThat(dir.isDirectory(), is(true));
    }

    @Test
    public void mkdirsShouldFailIfDirExists() {
        File dir = new File(sanbox, "foo");
        runBuild(mkdirs(dir.getPath()), Passed);
        runBuild(mkdirs(dir.getPath()), Failed);
    }

    @Test
    public void testDirectoryExistsBeforeMkdir() {
        File dir = new File(sanbox, "foo");
        runBuild(mkdirs(dir.getPath()), Passed);
        runBuild(mkdirs(dir.getPath()).setTest(test("-d", dir.getPath()), false), Passed);
    }

    @Test
    public void shouldNotFailBuildWhenTestCommandFail() {
        runBuild(echo("foo").setTest(fail(""), false), Passed);
        assertThat(statusReporter.singleResult(), is(Passed));
        assertThat(console.output(), containsString("foo"));
    }

    @Test
    public void shouldNotFailBuildWhenComposedTestCommandFail() {
        runBuild(echo("foo").setTest(compose(echo(""), fail("")), false), Passed);
        assertThat(statusReporter.singleResult(), is(JobResult.Passed));
        assertThat(console.output(), containsString("foo"));
    }

    @Test
    public void shouldNotFailBuildWhenTestEqWithComposedCommandOutputFail() {
        runBuild(echo("foo").setTest(test("-eq", "42", compose(fail("42"))), true), Passed);
        assertThat(statusReporter.singleResult(), is(Passed));
        assertThat(console.output(), containsString("foo"));
    }

    @Test
    public void cleanDirWithoutAllows() throws IOException {
        runBuild(mkdirs(new File(sanbox, "foo/baz").getPath()), Passed);
        assertTrue(new File(sanbox, "foo/file1").createNewFile());
        assertTrue(new File(sanbox, "file2").createNewFile());

        runBuild(cleandir(sanbox.getPath()), Passed);
        assertThat(sanbox.exists(), is(true));
        assertThat(sanbox.listFiles().length, is(0));
    }

    @Test
    public void cleanDirWithAllows() throws IOException {
        runBuild(mkdirs(new File(sanbox, "foo/baz").getPath()), Passed);
        runBuild(mkdirs(new File(sanbox, "foo2").getPath()), Passed);
        assertTrue(new File(sanbox, "foo/file1").createNewFile());
        assertTrue(new File(sanbox, "file2").createNewFile());

        runBuild(cleandir(sanbox.getPath(), "file2", "foo2"), Passed);
        assertThat(sanbox.exists(), is(true));
        assertThat(sanbox.listFiles(), is(new File[]{new File(sanbox, "file2"), new File(sanbox, "foo2")}));
    }


    @Test
    public void echoWithBuildVariableSubstitution() {
        runBuild(echo("hello ${test.foo}"), Passed);
        assertThat(console.lastLine(), is("hello ${test.foo}"));
        buildVariables.put("test.foo", "world");
        runBuild(echo("hello ${test.foo}"), Passed);
        assertThat(console.lastLine(), is("hello world"));
    }

    @Test
    public void testDirExists() throws IOException {
        runBuild(test("-d", sanbox.getPath()), Passed);
        runBuild(test("-d", new File(sanbox, "foo").getPath()), Failed);
        File file = new File(sanbox, "file");
        file.createNewFile();
        runBuild(test("-d", file.getPath()), Failed);
    }

    @Test
    public void testFileExists() throws IOException {
        runBuild(test("-f", sanbox.getPath()), Failed);
        runBuild(test("-f", new File(sanbox, "foo").getPath()), Failed);
        File file = new File(sanbox, "file");
        assertTrue(file.createNewFile());
        runBuild(test("-f", file.getPath()), Passed);
    }

    @Test
    public void testEqWithCommandOutput() throws IOException {
        runBuild(test("-eq", "foo", echo("foo")), Passed);
        assertThat(console.lineCount(), is(0));
    }

    @Test
    public void exportEnvironmentVariableHasMeaningfulOutput() throws Exception {
        runBuild(compose(
                export("answer", "2", false),
                export("answer", "42", false)), Passed);
        assertThat(console.asList().get(0), is("[go] setting environment variable 'answer' to value '2'"));
        assertThat(console.asList().get(1), is("[go] overriding environment variable 'answer' with value '42'"));
    }

    @Test
    public void exportOutputWhenOverridingSystemEnv() throws Exception {
        String envName = pathSystemEnvName();
        runBuild(export(envName, "/foo/bar", false), Passed);
        assertThat(console.output(), is(String.format("[go] overriding environment variable '%s' with value '/foo/bar'", envName)));
    }

    @Test
    public void exportSecretEnvShouldMaskValue() throws Exception {
        runBuild(export("answer", "42", true), Passed);
        assertThat(console.output(), is("[go] setting environment variable 'answer' to value '********'"));
    }

    @Test
    public void exportWithoutValueDisplayCurrentValue() throws Exception {
        runBuild(export("foo"), Passed);
        assertThat(console.lastLine(), is("[go] setting environment variable 'foo' to value 'null'"));
        runBuild(compose(
                export("foo", "bar", false),
                export("foo")), Passed);
        assertThat(console.lastLine(), is("[go] setting environment variable 'foo' to value 'bar'"));
    }

    @Test
    public void secretMaskValuesInExecOutput() throws Exception {
        runBuild(compose(
                secret("42"),
                exec("echo", "the answer is 42")), Passed);
        assertThat(console.output(), containsString("the answer is ******"));
    }

    @Test
    public void secretMaskValuesInExportOutput() throws Exception {
        runBuild(compose(
                secret("42"),
                export("oracle", "the answer is 42", false)), Passed);
        assertThat(console.output(), is("[go] setting environment variable 'oracle' to value 'the answer is ******'"));
    }

    @Test
    public void addSecretWithSubstitution() throws  Exception {
        runBuild(compose(
                secret("foo:bar@ssss.com", "foo:******@ssss.com"),
                exec("echo", "connecting to foo:bar@ssss.com"),
                exec("echo", "connecting to foo:bar@tttt.com")), Passed);
        assertThat(console.firstLine(), containsString("connecting to foo:******@ssss.com"));
        assertThat(console.asList().get(1), containsString("connecting to foo:bar@tttt.com"));
    }

    @Test
    public void uploadSingleFileArtifact() throws Exception {
        File targetFile = new File(sanbox, "foo");
        assertTrue(targetFile.createNewFile());
        runBuild(uploadArtifact("foo", "foo-dest").setWorkingDirectory(sanbox.getPath()), Passed);
        assertThat(artifactsRepository.getFileUploaded().size(), is(1));
        assertThat(artifactsRepository.getFileUploaded().get(0).file, is(targetFile));
        assertThat(artifactsRepository.getFileUploaded().get(0).destPath, is("foo-dest"));
        assertThat(artifactsRepository.getFileUploaded().get(0).buildId, is("build1"));
    }

    @Test
    public void uploadMultipleArtifact() throws Exception {
        File dir = new File(sanbox, "foo");
        assertTrue(dir.mkdirs());
        assertTrue(new File(dir, "bar").createNewFile());
        assertTrue(new File(dir, "baz").createNewFile());
        runBuild(uploadArtifact("foo/*", "foo-dest").setWorkingDirectory(sanbox.getPath()), Passed);
        assertThat(artifactsRepository.getFileUploaded().size(), is(2));
        assertThat(artifactsRepository.getFileUploaded().get(0).file, is(new File(dir, "bar")));
        assertThat(artifactsRepository.getFileUploaded().get(1).file, is(new File(dir, "baz")));
    }


    @Test
    public void cancelLongRunningBuild() throws InterruptedException {
        final BuildSession buildSession = newBuildSession();
        Thread buildingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                buildSession.build(compose(
                        execSleepScript(50),
                        echo("build done")));
            }
        });
        try {
            buildingThread.start();
            waitForConsoleOutputIncludes("start sleeping", 5);
            assertTrue(info(), buildSession.cancel(30, TimeUnit.SECONDS));
            assertThat(info(), getLast(statusReporter.results()), is(Cancelled));
            assertThat(info(), console.output(), not(containsString("build done")));
        } finally {
            buildingThread.join();
        }
    }

    @Test
    public void cancelLongRunningTestCommand() throws InterruptedException {
        final BuildSession buildSession = newBuildSession();
        Thread buildingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                buildSession.build(compose(
                        echo("after sleep").setTest(compose(
                                execSleepScript(50),
                                fail("")), false)));
            }
        });
        try {
            buildingThread.start();
            waitForConsoleOutputIncludes("start sleeping", 5);
            assertTrue(info(), buildSession.cancel(30, TimeUnit.SECONDS));
            assertThat(info(), getLast(statusReporter.results()), is(Cancelled));
            assertThat(info(), console.output(), not(containsString("after sleep")));
        } finally {
            buildingThread.join();
        }
    }

    @Test
    public void doubleCancelDoNothing() throws InterruptedException {
        final BuildSession buildSession = newBuildSession();
        Thread buildingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                buildSession.build(execSleepScript(50));
            }
        });
        Runnable cancel = new Runnable() {
            @Override
            public void run() {
                try {
                    buildSession.cancel(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw bomb(e);
                }
            }
        };
        Thread cancelThread1 = new Thread(cancel);
        Thread cancelThread2 = new Thread(cancel);

        try {
            buildingThread.start();
            waitForConsoleOutputIncludes("start sleeping", 5);
            cancelThread1.start();
            cancelThread2.start();
            cancelThread1.join();
            cancelThread2.join();
            assertThat(info(), getLast(statusReporter.results()), is(Cancelled));
            assertThat(info(), console.output(), not(containsString("after sleep")));
        } finally {
            buildingThread.join();
        }

    }

    @Test
    public void cancelShouldProcessOnCancelCommandOfCommandThatIsRunning() throws InterruptedException {
        final BuildSession buildSession = newBuildSession();
        Thread buildingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                buildSession.build(compose(
                        compose(
                            execSleepScript(50).setOnCancel(echo("exec canceled")),
                            echo("after sleep"))
                                .setOnCancel(echo("inner oncancel"))
                ).setOnCancel(echo("outter oncancel")));
            }
        });

        try {
            buildingThread.start();
            waitForConsoleOutputIncludes("start sleeping", 5);
            assertTrue(info(), buildSession.cancel(30, TimeUnit.SECONDS));
            assertThat(info(), getLast(statusReporter.results()), is(Cancelled));
            assertThat(info(), console.output(), not(containsString("after sleep")));
            assertThat(info(), console.output(), containsString("exec canceled"));
            assertThat(info(), console.output(), containsString("inner oncancel"));
            assertThat(info(), console.output(), containsString("outter oncancel"));
        } finally {
            buildingThread.join();
        }
    }

    @Test
    public void downloadFilePrintErrorWhenFailed() {
        runBuild(downloadFile(map(
                "url", "http://far.far.away/foo.jar",
                "dest", new File(sanbox, "bar.jar").getPath())), Failed);
        assertThat(console.output(), containsString("Could not fetch artifact"));
    }

    @Test
    public void downloadFileWithoutMD5Check() throws IOException {
        File dest = new File(sanbox, "bar.jar");
        httpService.setupDownload("http://far.far.away/foo.jar", "some content");
        runBuild(downloadFile(map(
                "url", "http://far.far.away/foo.jar",
                "dest", dest.getPath())), Passed);
        assertThat(console.output(), containsString("without verifying the integrity"));
        assertThat(FileUtil.readContentFromFile(dest), is("some content"));
    }

    @Test
    public void downloadFileWithMD5Check() throws IOException {
        File dest = new File(sanbox, "bar.jar");
        httpService.setupDownload("http://far.far.away/foo.jar", "some content");
        httpService.setupDownload("http://far.far.away/foo.jar.md5", "foo.jar=9893532233caff98cd083a116b013c0b");
        runBuild(downloadFile(map(
                "url", "http://far.far.away/foo.jar",
                "dest", dest.getPath(),
                "src", "foo.jar",
                "checksumUrl", "http://far.far.away/foo.jar.md5")), Passed);
        assertThat(console.output(), containsString(String.format("Saved artifact to [%s] after verifying the integrity of its contents", dest.getAbsolutePath())));
        assertThat(FileUtil.readContentFromFile(dest), is("some content"));
    }

    @Test
    public void downloadFileShouldAppendSha1IntoDownloadUrlIfDestFileAlreadyExists() throws IOException {
        File dest = new File(sanbox, "bar.jar");
        Files.write(Paths.get(dest.getPath()), "foobar".getBytes());
        String sha1 = java.net.URLEncoder.encode(StringUtil.sha1Digest(dest), "UTF-8");

        httpService.setupDownload("http://far.far.away/foo.jar", "content without sha1");
        httpService.setupDownload("http://far.far.away/foo.jar?sha1=" + sha1, "content with sha1");
        runBuild(downloadFile(map(
                "url", "http://far.far.away/foo.jar",
                "dest", dest.getPath())), Passed);
        assertThat(console.output(), containsString("Saved artifact"));
        assertThat(FileUtil.readContentFromFile(dest), is("content with sha1"));
    }

    @Test
    public void downloadDirWithChecksum() throws Exception {
        File dest = new File(sanbox, "dest");
        assertTrue(dest.mkdirs());

        File folder = TestFileUtil.createTempFolder("log");
        Files.write(Paths.get(folder.getPath(), "a"), "content for a".getBytes());
        Files.write(Paths.get(folder.getPath(), "b"), "content for b".getBytes());


        File zip = new ZipUtil().zip(folder, TestFileUtil.createUniqueTempFile(folder.getName()), Deflater.NO_COMPRESSION);

        httpService.setupDownload("http://far.far.away/log.zip", zip);
        httpService.setupDownload("http://far.far.away/log.zip.md5", "log/a=524ebd45bd7de3616317127f6e639bd6\nlog/b=83c0aa3048df233340203c74e8a93d7d");

        runBuild(downloadDir(map(
                "url", "http://far.far.away/log.zip",
                "dest", dest.getPath(),
                "src", "foo.jar",
                "checksumUrl", "http://far.far.away/log.zip.md5")), Passed);
        assertThat(console.output(), containsString(String.format("Saved artifact to [%s] after verifying the integrity of its contents", dest.getPath())));
        assertThat(FileUtil.readContentFromFile(new File(dest, "log/a")), is("content for a"));
        assertThat(FileUtil.readContentFromFile(new File(dest, "log/b")), is("content for b"));
    }


    private void runBuild(BuildSession buildSession, BuildCommand command, JobResult expectedResult) {
        JobResult result = buildSession.build(command);
        assertThat(info(), result, is(expectedResult));

    }

    private void runBuild(BuildCommand command, JobResult expectedResult) {
        runBuild(newBuildSession(), command, expectedResult);
    }

    private BuildSession newBuildSession() {
        return new BuildSession("build1",
                statusReporter,
                console,
                StrLookup.mapLookup(buildVariables),
                artifactsRepository, httpService, new TestingClock());
    }

    private void waitForConsoleOutputIncludes(String content, int timoutInSeconds) throws InterruptedException {
        long start = System.nanoTime();
        while (true) {
            if (console.output().contains(content)) {
                break;
            }
            if (System.nanoTime() - start > TimeUnit.SECONDS.toNanos(timoutInSeconds)) {
                throw new RuntimeException("waiting timeout!" + info());
            }
            Thread.sleep(10);
        }
    }

    private BuildCommand execEchoEnv(final String envname) {
        if (SystemUtil.isWindows()) {
            return exec("echo", "%" + envname + "%");
        } else {
            return exec("/bin/sh", "-c", String.format("echo ${%s}", envname));
        }
    }

    public static BuildCommand execSleepScript(int seconds) {
        if (SystemUtil.isWindows()) {
            return exec("cmd", "/c", "echo start sleeping & ping 1.1.1.1 -n 1 -w " + seconds * 1000 + " >NULL & echo after sleep");
        } else {
            return exec("/bin/sh", "-c", "echo start sleeping;sleep " + seconds + ";echo after sleep");
        }
    }

    private String pathSystemEnvName() {
        return SystemUtil.isWindows() ? "Path" : "PATH";
    }

    private String info() {
        return "\n*** Assertion failure *** \n"
                + "build status: " + statusReporter.status() + "\n"
                + "build result: " + statusReporter.results() + "\n"
                + "build console output: \n"
                + console.output()
                + "\n******";
    }
}