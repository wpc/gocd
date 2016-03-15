package com.thoughtworks.go.agent;

import com.google.gson.Gson;
import com.thoughtworks.go.agent.service.AgentWebsocketService;
import com.thoughtworks.go.config.ArtifactPlan;
import com.thoughtworks.go.domain.*;
import com.thoughtworks.go.domain.builder.pluggableTask.PluggableTaskConsole;
import com.thoughtworks.go.domain.builder.pluggableTask.PluggableTaskContext;
import com.thoughtworks.go.domain.exception.ArtifactPublishingException;
import com.thoughtworks.go.plugin.access.PluginRequestHelper;
import com.thoughtworks.go.plugin.access.pluggabletask.JobConsoleLoggerInternal;
import com.thoughtworks.go.plugin.access.pluggabletask.TaskExtension;
import com.thoughtworks.go.plugin.api.BuildCommand;
import com.thoughtworks.go.plugin.api.response.execution.ExecutionResult;
import com.thoughtworks.go.plugin.api.task.Console;
import com.thoughtworks.go.plugin.api.task.EnvironmentVariables;
import com.thoughtworks.go.plugin.api.task.TaskConfig;
import com.thoughtworks.go.plugin.api.task.TaskExecutionContext;
import com.thoughtworks.go.plugin.infra.ActionWithReturn;
import com.thoughtworks.go.plugin.infra.PluginManager;
import com.thoughtworks.go.plugin.infra.plugininfo.GoPluginDescriptor;
import com.thoughtworks.go.publishers.GoArtifactsManipulator;
import com.thoughtworks.go.remote.work.ConsoleOutputTransmitter;
import com.thoughtworks.go.remote.work.RemoteConsoleAppender;
import com.thoughtworks.go.server.service.AgentBuildingInfo;
import com.thoughtworks.go.server.service.AgentRuntimeInfo;
import com.thoughtworks.go.util.*;
import com.thoughtworks.go.util.command.CommandLine;
import com.thoughtworks.go.util.command.EnvironmentVariableContext;
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
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;

public class BuildSession {
    private static final Logger LOG = LoggerFactory.getLogger(BuildSession.class);
    private static final Gson gson = new Gson();

    private ConsoleOutputTransmitter console;
    private String buildId;
    private BuildSessionGoPublisher publisher;
    private TaskExtension taskExtension;

    enum BulidCommandType {
        start, end, echo, export, compose, exec, test, generateProperty, uploadArtifact, generateTestReport, downloadFile, downloadDir, reportCompleting, reportCompleted, callExtension, callAPIBasedTaskExtension, reportCurrentStatus
    }

    private Map<String, String> envs = new HashMap<>();
    private AgentRuntimeInfo agentRuntimeInfo;
    private HttpService httpService;
    private Boolean buildPass;
    private final AgentWebsocketService websocketService;
    private PluginManager pluginManager;

    public BuildSession(AgentRuntimeInfo agentRuntimeInfo, HttpService httpService, AgentWebsocketService websocketService, PluginManager pluginManager, TaskExtension taskExtension) {
        this.agentRuntimeInfo = agentRuntimeInfo;
        this.httpService = httpService;
        this.websocketService = websocketService;
        this.pluginManager = pluginManager;
        this.taskExtension = taskExtension;
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
                case callAPIBasedTaskExtension:
                    return callAPIBasedTaskExtension(command);
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

    private boolean callAPIBasedTaskExtension(BuildCommand command) {
        final Map<String, String> callParams = command.getArgs();
        Map config = gson.fromJson(callParams.get("taskConfig"), Map.class);
        final TaskConfig taskConfig = new TaskConfig();
        for (Object key : config.keySet()) {
            Map map = (Map) config.get(key);
            String value = map.get("value").toString();
            taskConfig.add(new com.thoughtworks.go.plugin.api.config.Property(key.toString(), value, null));
        }
        final EnvironmentVariableContext environmentVariableContext = new EnvironmentVariableContext();
        Map environmentVariables = gson.fromJson(callParams.get("environmentVariables"), Map.class);
        for (Object key : environmentVariables.keySet()) {
            environmentVariableContext.setProperty(key.toString(), environmentVariables.get(key).toString(), false);
        }
        ExecutionResult executionResult = taskExtension.execute((String) callParams.get("pluginId"), new ActionWithReturn<com.thoughtworks.go.plugin.api.task.Task, ExecutionResult>() {
            @Override
            public ExecutionResult execute(com.thoughtworks.go.plugin.api.task.Task task, GoPluginDescriptor pluginDescriptor) {
                final TaskExecutionContext taskExecutionContext = new PluggableTaskContext(
                        null,
                        publisher,
                        environmentVariableContext,
                        (String) callParams.get("workingDir"));
                return task.executor().execute(taskConfig, taskExecutionContext);
            }
        });

        if (!executionResult.isSuccessful()) {
            String errorMessage = executionResult.getMessagesForDisplay();
            LOG.error(errorMessage);
            publisher.consumeLine(errorMessage);
        }

        return executionResult.isSuccessful();
    }

    private boolean callExtension(BuildCommand command) {
        Map<String, String> callParams = command.getArgs();
        PluginRequestHelper pluginRequestHelper = new PluginRequestHelper(pluginManager,
                null, callParams.get("name"));

        pluginRequestHelper.submitRequest(
                callParams.get("pluginId"),
                callParams.get("requestName"),
                callParams.get("extensionVersion"),
                callParams.get("requestBody"),
               null);
        return true;
    }


    private boolean downloadDir(BuildCommand command) {
        final String url = command.getArgs().get("url");
        final String src = command.getArgs().get("src");
        final String dest = command.getArgs().get("dest");

        String checksumUrl = null;
        ChecksumFileHandler checksumFileHandler = null;
        if (command.getArgs().size() > 4) {
            checksumUrl = command.getArgs().get("checksumUrl");
            String checksumFile = command.getArgs().get("checksumFile");
            checksumFileHandler = new ChecksumFileHandler(new File(checksumFile));
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
        final String url = command.getArgs().get("url");
        final String src = command.getArgs().get("src");
        final String dest = command.getArgs().get("dest");

        String checksumUrl = null;
        ChecksumFileHandler checksumFileHandler = null;

        if (command.getArgs().size() > 4) {
            checksumUrl = command.getArgs().get("checksumUrl");
            String checksumFile = command.getArgs().get("checksumFile");
            checksumFileHandler = new ChecksumFileHandler(new File(checksumFile));
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
        testReporter.generateAndUpload(command.getArgsList(command.getArgs().size()));
        return true;
    }

    private boolean uploadArtifact(BuildCommand command) {
        final String src = command.getArgs().get("src");
        final String dest = command.getArgs().get("dest");
        ArtifactPlan p = ArtifactPlan.create(ArtifactType.file, src, dest);
        p.publish(this.publisher, new File(command.getWorkingDirectory()));
        return true;
    }

    private boolean generateProperty(BuildCommand command) {
        String name = command.getArgs().get("name");
        String src = command.getArgs().get("src");
        String xpath = command.getArgs().get("xpath");
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
        JobState jobState = JobState.valueOf(command.getArgs().get("jobState"));
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
        if ("-d".equals(command.getArgs().get("flag"))) {
            success = new File(command.getArgs().get("path")).isDirectory();
        }
        return success;
    }

    private boolean exec(BuildCommand command) {
        String execCommand = command.getArgs().get("command");
        CommandLine commandLine = CommandLine.createCommandLine(execCommand);

        if (command.getWorkingDirectory() != null) {
            commandLine.withWorkingDir(new File(command.getWorkingDirectory()));
        }

        String[] args = command.getArgsList(command.getArgs().size() - 1);
        for (String arg : args) {
            commandLine.withArg(new StringArgument(arg));
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
        if (command.getArgs().size() > 0) {
            envs.putAll(command.getArgs());
            return true;
        } else {
            StringBuffer buffer = new StringBuffer();
            for (String var : envs.keySet()) {
                buffer.append(format("export %s=%s", var, envs.get(var)));
                buffer.append("\n");
            }
            return process(BuildCommand.echo(buffer.toString()));
        }
    }

    private boolean end() {
        agentRuntimeInfo.idle();
        buildPass = null;
        JobConsoleLoggerInternal.unsetContext();
        console.stop();
        return true;
    }

    private boolean echo(BuildCommand command) {
        for (String line : command.getArgsList(command.getArgs().size())) {
            console.consumeLine(line);
        }
        return true;
    }

    private boolean start(BuildCommand command) {
        SystemEnvironment env = new SystemEnvironment();
        Map<String, String> settings = command.getArgs();
        String buildLocator = settings.get("buildLocator");
        String buildLocatorForDisplay = settings.get("buildLocatorForDisplay");
        final String consoleURI = env.getServiceUrl() + settings.get("consoleURI");
        String artifactUploadBaseUrl = env.getServiceUrl() + settings.get("artifactUploadBaseUrl");
        String propertyBaseUrl = env.getServiceUrl() + settings.get("propertyBaseUrl");
        this.buildId = settings.get("buildId");

        agentRuntimeInfo.busy(new AgentBuildingInfo(buildLocatorForDisplay, buildLocator));
        this.envs = new HashMap<>();
        console = new ConsoleOutputTransmitter(
                new RemoteConsoleAppender(consoleURI, httpService, agentRuntimeInfo.getIdentifier()));
        this.buildPass = true;


        this.publisher = new BuildSessionGoPublisher(console, httpService, artifactUploadBaseUrl, propertyBaseUrl, buildId);

        final Console.SecureEnvVarSpecifier notSecure = new Console.SecureEnvVarSpecifier() {
            @Override
            public boolean isSecure(String variableName) {
                return false;
            }
        };

        JobConsoleLoggerInternal.setContext(new TaskExecutionContext() {
            @Override
            public EnvironmentVariables environment() {
                return new EnvironmentVariables() {
                    @Override
                    public Map<String, String> asMap() {
                        return BuildSession.this.envs;
                    }

                    @Override
                    public void writeTo(Console console) {
                        console.printEnvironment(BuildSession.this.envs, notSecure);
                    }

                    @Override
                    public Console.SecureEnvVarSpecifier secureEnvSpecifier() {
                        return notSecure;
                    }
                };
            }

            @Override
            public Console console() {
                return new PluggableTaskConsole(null, publisher);
            }

            @Override
            public String workingDir() {
                return null;
            }
        });
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
                httpService.postProperty(propertyURI(property.getKey()), property.getValue());
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
