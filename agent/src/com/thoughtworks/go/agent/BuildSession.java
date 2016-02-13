package com.thoughtworks.go.agent;

import com.thoughtworks.go.agent.service.AgentWebsocketService;
import com.thoughtworks.go.config.ArtifactPlan;
import com.thoughtworks.go.domain.ArtifactType;
import com.thoughtworks.go.domain.JobState;
import com.thoughtworks.go.domain.Property;
import com.thoughtworks.go.domain.exception.ArtifactPublishingException;
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
import java.util.*;

import static com.thoughtworks.go.util.ExceptionUtils.bomb;
import static com.thoughtworks.go.util.FileUtil.normalizePath;
import static java.lang.String.format;

public class BuildSession {
    private static final Logger LOG = LoggerFactory.getLogger(BuildSession.class);

    private ConsoleOutputTransmitter console;
    private String buildId;
    private BuildSessionGoPublisher publisher;

    enum BulidCommandType {
        start, end, echo, export, compose, exec, test, generateProperty, uploadArtifact, generateTestReport, report
    }

    private Map<String, String> envs = new HashMap<>();
    private AgentRuntimeInfo agentRuntimeInfo;
    private SystemEnvironment systemEnvironment;
    private HttpService httpService;
    private Boolean buildPass;
    private final AgentWebsocketService websocketService;

    public BuildSession(AgentRuntimeInfo agentRuntimeInfo, SystemEnvironment systemEnvironment, HttpService httpService, AgentWebsocketService websocketService) {
        this.agentRuntimeInfo = agentRuntimeInfo;
        this.systemEnvironment = systemEnvironment;
        this.httpService = httpService;
        this.websocketService = websocketService;
    }

    public CommandResult process(BuildCommand command) {
        LOG.info("Processing build command {}", command);
        try {

            BulidCommandType type = BulidCommandType.valueOf(command.getName());

            if(buildPass != null ) {
                if ("passed".equals(command.getRunIfConfig()) && !this.buildPass) {
                    return new CommandResult(0, agentRuntimeInfo);
                } else if ("failed".equals(command.getRunIfConfig()) && this.buildPass) {
                    return new CommandResult(0, agentRuntimeInfo);
                }
            }

            BuildCommand.Test test = command.getTest();
            if(test != null) {
                CommandResult testResult = process(test.command);
                if(testResult.isSuccess() != test.expectation) {
                    return new CommandResult(0, agentRuntimeInfo);
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
                case report:
                    return report(command);
                case generateProperty:
                    return generateProperty(command);
                case uploadArtifact:
                    return uploadArtifact(command);
                case generateTestReport:
                    return generateTestReport(command);
                case end:
                    return end(command);
                default:
                    return new CommandResult(1, agentRuntimeInfo, "Unknown command: " + command.toString());
            }
        } catch (RuntimeException e) {
            LOG.error("Processing error: ", e);
            agentRuntimeInfo.idle();
            return new CommandResult(1, agentRuntimeInfo, e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private CommandResult generateTestReport(BuildCommand command) {
        TestReporter testReporter = new TestReporter(this.publisher, command.getWorkingDirectory());
        testReporter.generateAndUpload(command.getStringArgs());
        return successResult();
    }

    private CommandResult uploadArtifact(BuildCommand command) {
        final String src = (String) command.getArgs()[0];
        final String dest = (String) command.getArgs()[1];
        ArtifactPlan p = ArtifactPlan.create(ArtifactType.file, src, dest);
        p.publish(this.publisher, new File(command.getWorkingDirectory()));
        return new CommandResult(0, agentRuntimeInfo);
    }

    private CommandResult generateProperty(BuildCommand command) {
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
        return successResult();
    }

    private CommandResult report(BuildCommand command) {
        JobState jobState = JobState.valueOf((String) command.getArgs()[0]);
        websocketService.send(new Message(Action.reportCurrentStatus, new Report(agentRuntimeInfo, buildId, jobState)));;
        return new CommandResult(0, agentRuntimeInfo);
    }

    private CommandResult test(BuildCommand command) {
        boolean success = false;
        if(command.getArgs()[0].equals("-d")) {
            success = new File((String) command.getArgs()[1]).isDirectory();
        }
        return new CommandResult(success ? 0 : 1, agentRuntimeInfo);
    }

    private CommandResult exec(BuildCommand command) {
        String execCommand = (String) command.getArgs()[0];
        CommandLine commandLine = CommandLine.createCommandLine(execCommand);

        if(command.getWorkingDirectory() != null) {
            commandLine.withWorkingDir(new File(command.getWorkingDirectory()));
        }

        for (int i = 1; i < command.getArgs().length; i++) {
            commandLine.withArg(new StringArgument(command.getArgs()[i].toString()));
        }

        int exitCode = commandLine.run(new ProcessOutputStreamConsumer<>(console, console), agentRuntimeInfo.getUUId());
        return new CommandResult(exitCode, agentRuntimeInfo);
    }

    private CommandResult compose(BuildCommand command) {
        CommandResult result = new CommandResult(0, agentRuntimeInfo);
        for (BuildCommand arg : command.getSubCommands()) {
            CommandResult childResult = process(arg);
            result.addChild(childResult);
            if(!childResult.isSuccess()) {
                result.setExitCode(1);
                buildPass = false;
            }
        }
        return result;
    }

    private CommandResult export(BuildCommand command) {
        if(command.getArgs().length > 0) {
            Map<String, String> vars = (Map<String, String>) command.getArgs()[0];
            envs.putAll(vars);
            return successResult();
        } else {
            ArrayList<String> list = new ArrayList<>(envs.size());
            for (String var : envs.keySet()) {
                list.add(format("export %s=%s", var, envs.get(var)));
            }
            return process(new BuildCommand("echo", list.toArray(new String[list.size()])));
        }
    }

    private CommandResult end(BuildCommand command) {
        agentRuntimeInfo.idle();
        buildPass = null;
        return successResult();
    }

    private CommandResult echo(BuildCommand command) {
        for (Object line : command.getArgs()) {
            console.consumeLine(line.toString());
        }
        return successResult();
    }

    private CommandResult start(BuildCommand command) {
        Map<String, Object> settings = (Map<String, Object>) command.getArgs()[0];
        String buildLocator = (String) settings.get("buildLocator");
        String buildLocatorForDisplay = (String) settings.get("buildLocatorForDisplay");
        String consoleURI = (String) settings.get("consoleURI");
        String artifactUploadBaseUrl = (String) settings.get("artifactUploadBaseUrl");
        String propertyBaseUrl = (String) settings.get("propertyBaseUrl");
        this.buildId = (String) settings.get("buildId");

        agentRuntimeInfo.busy(new AgentBuildingInfo(buildLocatorForDisplay, buildLocator));
        this.envs = new HashMap<>();
        console = new ConsoleOutputTransmitter(
                new RemoteConsoleAppender(consoleURI, httpService, agentRuntimeInfo.getIdentifier()));
        this.buildPass = true;


        this.publisher = new BuildSessionGoPublisher(console, httpService, artifactUploadBaseUrl, propertyBaseUrl, buildId);
        return successResult();
    }

    private CommandResult successResult() {
        return new CommandResult(0, agentRuntimeInfo);
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
            return format("%s/%s", propertyBaseUrl, UrlUtil.encodeInUtf8(name));
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
