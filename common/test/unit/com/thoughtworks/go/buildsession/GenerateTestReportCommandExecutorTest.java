package com.thoughtworks.go.buildsession;

import com.thoughtworks.go.domain.*;
import com.thoughtworks.go.domain.exception.ArtifactPublishingException;
import com.thoughtworks.go.util.FileUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.util.Collections;

import static com.thoughtworks.go.util.TestUtils.copyAndClose;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GenerateTestReportCommandExecutorTest extends BuildSessionBasedTestCase {

    private File testFolder;

    @Before
    public void setUp() {
        testFolder = new File(sandbox, "test-reports");
        testFolder.mkdir();
    }


    @Test
    public void generateReportForNUnit() throws IOException, ArtifactPublishingException {
        copyAndClose(source("TestResult.xml"), target("test-result.xml"));
        runBuild(BuildCommand.generateTestReport(Collections.singletonList("test-reports/test-result.xml"), "test-out"), JobResult.Passed);
        assertThat(artifactsRepository.getFileUploaded().size(), is(1));
        assertThat(artifactsRepository.getFileUploaded().get(0).destPath, is("test-out"));
        assertThat(artifactsRepository.propertyValue(TestReportGenerator.TOTAL_TEST_COUNT), is("206"));
        assertThat(artifactsRepository.propertyValue(TestReportGenerator.FAILED_TEST_COUNT), is("0"));
        assertThat(artifactsRepository.propertyValue(TestReportGenerator.IGNORED_TEST_COUNT), is("0"));
        assertThat(artifactsRepository.propertyValue(TestReportGenerator.TEST_TIME), is("NaN"));
    }


    private OutputStream target(String targetFile) throws FileNotFoundException {
        return new FileOutputStream(testFolder.getAbsolutePath() + FileUtil.fileseparator() + targetFile);
    }

    private InputStream source(String filename) throws IOException {
        return new ClassPathResource(FileUtil.fileseparator() + "data" + FileUtil.fileseparator() + filename).getInputStream();
    }

}