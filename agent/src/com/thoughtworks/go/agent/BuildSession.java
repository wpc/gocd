package com.thoughtworks.go.agent;

import com.thoughtworks.go.agent.service.AgentWebsocketService;
import com.thoughtworks.go.config.ArtifactPlan;
import com.thoughtworks.go.domain.*;
import com.thoughtworks.go.domain.exception.ArtifactPublishingException;
import com.thoughtworks.go.plugin.access.PluginRequestHelper;
import com.thoughtworks.go.plugin.infra.PluginManager;
import com.thoughtworks.go.publishers.GoArtifactsManipulator;
import com.thoughtworks.go.remote.work.ConsoleOutputTransmitter;
import com.thoughtworks.go.remote.work.RemoteConsoleAppender;
import com.thoughtworks.go.server.service.AgentBuildingInfo;
import com.thoughtworks.go.server.service.AgentRuntimeInfo;
import com.thoughtworks.go.util.*;
import com.thoughtworks.go.util.command.CommandLine;
import com.thoughtworks.go.util.command.ProcessOutputStreamConsumer;
import com.thoughtworks.go.util.command.StringArgument;
import com.thoughtworks.go.websocket.Action;
import com.thoughtworks.go.websocket.Message;
import com.thoughtworks.go.websocket.Report;
import com.thoughtworks.go.work.GoPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;

public class BuildSession {
    private static final Logger LOG = LoggerFactory.getLogger(BuildSession.class);

    private ConsoleOutputTransmitter console;
    private String buildId;
    private BuildSessionGoPublisher publisher;

    enum BulidCommandType {
        start, end, echo, export, compose, exec, test, generateProperty, uploadArtifact, generateTestReport, downloadFile, downloadDir, reportCompleting, reportCompleted, callExtension, reportCurrentStatus
    }

    private Map<String, String> envs = new HashMap<>();
    private AgentRuntimeInfo agentRuntimeInfo;
    private HttpService httpService;
    private Boolean buildPass;
    private final AgentWebsocketService websocketService;
    private PluginManager pluginManager;

    public BuildSession(AgentRuntimeInfo agentRuntimeInfo, HttpService httpService, AgentWebsocketService websocketService, PluginManager pluginManager) {
        this.agentRuntimeInfo = agentRuntimeInfo;
        this.httpService = httpService;
        this.websocketService = websocketService;
        this.pluginManager = pluginManager;

    }

    public boolean process(BuildCommand command) {
        LOG.debug("Processing build command {}", command);
        try {

            BulidCommandType type = BulidCommandType.valueOf(command.getName());

            if (buildPass != null
                    && ("passed".equals(command.getRunIfConfig()) && !this.buildPass
                    || "failed".equals(command.getRunIfConfig()) && this.buildPass)) {
                return true;
            }

            BuildCommand.Test test = command.getTest();
            if (test != null) {
                if (process(test.command) != test.expectation) {
                    return true;
                }
            }

            switch (type) {
                case start:
                    return start(command);
                case compose:
                    return compose(command);
                case echo:
                    return echo(command);
                case export:
                    return export(command);
                case exec:
                    return exec(command);
                case test:
                    return test(command);
                case reportCurrentStatus:
                    return reportCurrentStatus(command);
                case reportCompleting:
                    return reportCompleting();
                case reportCompleted:
                    return reportCompleted();
                case generateProperty:
                    return generateProperty(command);
                case downloadFile:
                    return downloadFile(command);
                case downloadDir:
                    return downloadDir(command);
                case uploadArtifact:
                    return uploadArtifact(command);
                case generateTestReport:
                    return generateTestReport(command);
                case callExtension:
                    return callExtension(command);
                case end:
                    return end();
                default:
                    throw new RuntimeException("Unknown command: " + command);
            }
        } catch (RuntimeException e) {
            LOG.error("Processing error: ", e);
            return false;
        }
    }

    private boolean callExtension(BuildCommand command) {
        Map<String, Object> callParams = (Map<String, Object>) command.getArgs()[0];
        PluginRequestHelper pluginRequestHelper = new PluginRequestHelper(pluginManager,
                null, (String) callParams.get("name"));

        pluginRequestHelper.submitRequest(
                (String) callParams.get("pluginId"),
                (String) callParams.get("requestName"),
                (String) callParams.get("extensionVersion"),
                (String) callParams.get("requestBody"),
                (Map<String, String>) callParams.get("requestParams"));
        return true;
    }


    private boolean downloadDir(BuildCommand command) {
        String[] args = command.getStringArgs();
        final String url = args[0];
        final String src = args[1];
        final String dest = args[2];

        String checksumUrl = null;
        ChecksumFileHandler checksumFileHandler = null;

        if (args.length > 4) {
            checksumUrl = args[3];
            checksumFileHandler = new ChecksumFileHandler(new File(args[4]));
        }

        DirHandler handler = new DirHandler(src, new File(dest));
        DownloadAction downloadAction = new DownloadAction(httpService, publisher, new SystemTimeClock());

        try {
            if (checksumFileHandler != null) {
                downloadAction.perform(checksumUrl, checksumFileHandler);
                handler.useArtifactMd5Checksums(checksumFileHandler.getArtifactMd5Checksums());
            }

            downloadAction.perform(url, handler);
        } catch (InterruptedException e) {
            throw new RuntimeException("download interrupted");
        }
        return true;
    }

    private boolean downloadFile(BuildCommand command) {
        String[] args = command.getStringArgs();
        final String url = args[0];
        final String src = args[1];
        final String dest = args[2];

        String checksumUrl = null;
        ChecksumFileHandler checksumFileHandler = null;

        if (args.length > 4) {
            checksumUrl = args[3];
            checksumFileHandler = new ChecksumFileHandler(new File(args[4]));
        }

        FileHandler handler = new FileHandler(new File(dest), src);
        DownloadAction downloadAction = new DownloadAction(httpService, publisher, new SystemTimeClock());

        try {
            if (checksumUrl != null) {
                downloadAction.perform(checksumUrl, checksumFileHandler);
                handler.useArtifactMd5Checksums(checksumFileHandler.getArtifactMd5Checksums());
            }
            downloadAction.perform(url, handler);
        } catch (InterruptedException e) {
            throw new RuntimeException("download interrupted");
        }
        return true;
    }

    private boolean generateTestReport(BuildCommand command) {
        TestReporter testReporter = new TestReporter(this.publisher, command.getWorkingDirectory());
        testReporter.generateAndUpload(command.getStringArgs());
        return true;
    }

    private boolean uploadArtifact(BuildCommand command) {
        final String src = (String) command.getArgs()[0];
        final String dest = (String) command.getArgs()[1];
        ArtifactPlan p = ArtifactPlan.create(ArtifactType.file, src, dest);
        p.publish(this.publisher, new File(command.getWorkingDirectory()));
        return true;
    }

    private boolean generateProperty(BuildCommand command) {
        String name = (String) command.getArgs()[0];
        String src = (String) command.getArgs()[1];
        String xpath = (String) command.getArgs()[2];
        File file = new File(command.getWorkingDirectory(), src);
        String indent = "             ";
        if (!file.exists()) {
            console.consumeLine(format("%sFailed to create property %s. File %s does not exist.", indent, name, file.getAbsolutePath()));
        } else {
            try {
                if (!XpathUtils.nodeExists(file, xpath)) {
                    console.consumeLine(format("%sFailed to create property %s. Nothing matched xpath \"%s\" in the file: %s.", indent, name, name, file.getAbsolutePath()));
                } else {
                    String value = XpathUtils.evaluate(file, xpath);
                    this.publisher.setProperty(new Property(name, value));
                    console.consumeLine(format("%sProperty %s = %s created." + "\n", indent, name, value));
                }
            } catch (Exception e) {
                String error = (e instanceof XPathExpressionException) ? (format("Illegal xpath: \"%s\"", xpath)) : ExceptionUtils.messageOf(e);
                String message = format("%sFailed to create property %s. %s", indent, name, error);
                LOG.error(message, e);
                console.consumeLine(message);
            }
        }
        return true;
    }

    private boolean reportCurrentStatus(BuildCommand command) {
        JobState jobState = JobState.valueOf((String) command.getArgs()[0]);
        websocketService.send(new Message(Action.reportCurrentStatus, new Report(agentRuntimeInfo, buildId, jobState, null)));
        return true;
    }

    private boolean reportCompleted() {
        JobResult result = this.buildPass ? JobResult.Passed : JobResult.Failed;
        websocketService.send(new Message(Action.reportCompleted, new Report(agentRuntimeInfo, buildId, null, result)));
        return true;
    }

    private boolean reportCompleting() {
        JobResult result = this.buildPass ? JobResult.Passed : JobResult.Failed;
        websocketService.send(new Message(Action.reportCompleting, new Report(agentRuntimeInfo, buildId, null, result)));
        return true;
    }


    private boolean test(BuildCommand command) {
        boolean success = false;
        if (command.getArgs()[0].equals("-d")) {
            success = new File((String) command.getArgs()[1]).isDirectory();
        }
        return success;
    }

    private boolean exec(BuildCommand command) {
        String execCommand = (String) command.getArgs()[0];
        CommandLine commandLine = CommandLine.createCommandLine(execCommand);

        if (command.getWorkingDirectory() != null) {
            commandLine.withWorkingDir(new File(command.getWorkingDirectory()));
        }

        for (int i = 1; i < command.getArgs().length; i++) {
            commandLine.withArg(new StringArgument(command.getArgs()[i].toString()));
        }

        int exitCode = commandLine.run(new ProcessOutputStreamConsumer<>(console, console), agentRuntimeInfo.getUUId());
        return exitCode == 0;
    }

    private boolean compose(BuildCommand command) {
        boolean success = true;
        for (BuildCommand arg : command.getSubCommands()) {
            if (!process(arg)) {
                success = false;
                buildPass = false;
            }
        }
        return success;
    }

    private boolean export(BuildCommand command) {
        if (command.getArgs().length > 0) {
            Map<String, String> vars = (Map<String, String>) command.getArgs()[0];
            envs.putAll(vars);
            return true;
        } else {
            ArrayList<String> list = new ArrayList<>(envs.size());
            for (String var : envs.keySet()) {
                list.add(format("export %s=%s", var, envs.get(var)));
            }
            return process(new BuildCommand("echo", list.toArray(new String[list.size()])));
        }
    }

    private boolean end() {
        agentRuntimeInfo.idle();
        buildPass = null;
        console.stop();
        return true;
    }

    private boolean echo(BuildCommand command) {
        for (Object line : command.getArgs()) {
            console.consumeLine(line.toString());
        }
        return true;
    }

    private boolean start(BuildCommand command) {
        Map<String, String> settings = (Map<String, String>) command.getArgs()[0];
        String buildLocator = settings.get("buildLocator");
        String buildLocatorForDisplay = settings.get("buildLocatorForDisplay");
        String consoleURI = settings.get("consoleURI");
        String artifactUploadBaseUrl = settings.get("artifactUploadBaseUrl");
        String propertyBaseUrl = settings.get("propertyBaseUrl");
        this.buildId = settings.get("buildId");

        agentRuntimeInfo.busy(new AgentBuildingInfo(buildLocatorForDisplay, buildLocator));
        this.envs = new HashMap<>();
        console = new ConsoleOutputTransmitter(
                new RemoteConsoleAppender(consoleURI, httpService, agentRuntimeInfo.getIdentifier()));
        this.buildPass = true;


        this.publisher = new BuildSessionGoPublisher(console, httpService, artifactUploadBaseUrl, propertyBaseUrl, buildId);
        return true;
    }

    private static class BuildSessionGoPublisher implements GoPublisher {
        private final ConsoleOutputTransmitter console;
        private final HttpService httpService;
        private final String artifactUploadBaseUrl;
        private final String propertyBaseUrl;
        private final GoArtifactsManipulator gam;
        private String buildId;

        public BuildSessionGoPublisher(ConsoleOutputTransmitter console, HttpService httpService, String artifactUploadBaseUrl, String propertyBaseUrl, String buildId) {
            this.console = console;
            this.httpService = httpService;
            this.artifactUploadBaseUrl = artifactUploadBaseUrl;
            this.propertyBaseUrl = propertyBaseUrl;
            this.buildId = buildId;
            this.gam = new GoArtifactsManipulator(httpService, null, new ZipUtil());
        }

        @Override
        public void upload(File fileToUpload, String destPath) {
            gam.publish(console, fileToUpload, destPath, artifactUploadBaseUrl, this.buildId);

        }

        @Override
        public void consumeLineWithPrefix(String message) {
            consumeLine(String.format("[%s] %s", GoConstants.PRODUCT_NAME, message));
        }

        @Override
        public void setProperty(Property property) {
            try {
                httpService.postToUrl(propertyURI(property.getKey()), property.getValue());
            } catch (IOException e) {
                throw new ArtifactPublishingException(format("Failed to set property %s with value %s", property.getKey(), property.getValue()), e);
            }
        }

        private String propertyURI(String name) {
            return format("%s%s", propertyBaseUrl, UrlUtil.encodeInUtf8(name));
        }

        @Override
        public void reportErrorMessage(String message, Exception e) {
            LOG.error(message, e);
            console.consumeLine(message);
        }

        @Override
        public void consumeLine(String line) {
            console.consumeLine(line);
        }
    }
}
