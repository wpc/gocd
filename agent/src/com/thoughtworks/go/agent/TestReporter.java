package com.thoughtworks.go.agent;

import com.thoughtworks.go.domain.UnitTestReportGenerator;
import com.thoughtworks.go.domain.WildcardScanner;
import com.thoughtworks.go.publishers.GoArtifactsManipulator;
import com.thoughtworks.go.remote.work.ConsoleOutputTransmitter;
import com.thoughtworks.go.util.FileUtil;
import com.thoughtworks.go.util.GoConstants;
import com.thoughtworks.go.work.GoPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestReporter {
    private static final Logger LOG = LoggerFactory.getLogger(TestReporter.class);
    private final File workingDirectory;
    private final GoPublisher publisher;


    public TestReporter(GoPublisher publisher, String workingDirectory) {
        this.publisher = publisher;
        this.workingDirectory = new File(workingDirectory);
    }


    public void generateAndUpload(String[] sources) {
        ArrayList<File> allFiles = new ArrayList<File>();
        for (String src : sources) {
            File source = new File(FileUtil.applyBaseDirIfRelativeAndNormalize(this.workingDirectory, new File(src)));
            WildcardScanner wildcardScanner = new WildcardScanner(workingDirectory, src);
            File[] files = wildcardScanner.getFiles();

            if (files.length > 0) {
                final List<File> fileList = files == null ? new ArrayList<File>() : Arrays.asList(files);
                allFiles.addAll(fileList);
            } else {
                final String message = MessageFormat.format("The Directory {0} specified as a test artifact was not found."
                        + " Please check your configuration", FileUtil.normalizePath(source));
                publisher.consumeLineWithPrefix(message);
                LOG.error(message);
            }
        }
        if (allFiles.size() > 0) {
            File tempFolder = null;
            try {
                tempFolder = FileUtil.createTempFolder();
                File testResultSource = new File(tempFolder, "result");
                testResultSource.mkdirs();
                UnitTestReportGenerator generator = new UnitTestReportGenerator(publisher, testResultSource);
                generator.generate(allFiles.toArray(new File[allFiles.size()]));
            } finally {
                if (tempFolder != null) {
                    FileUtil.deleteFolder(tempFolder);
                }
            }

        } else {
            String message = "No files were found in the Test Results folders";
            publisher.consumeLineWithPrefix(message);
            LOG.warn(message);
        }
    }
}
