package com.thoughtworks.go.agent;

import com.thoughtworks.go.remote.work.ConsoleOutputTransmitter;
import com.thoughtworks.go.remote.work.RemoteConsoleAppender;
import com.thoughtworks.go.server.service.AgentBuildingInfo;
import com.thoughtworks.go.server.service.AgentRuntimeInfo;
import com.thoughtworks.go.util.HttpService;
import com.thoughtworks.go.util.SystemEnvironment;
import com.thoughtworks.go.util.command.CommandLine;
import com.thoughtworks.go.util.command.ProcessOutputStreamConsumer;
import com.thoughtworks.go.util.command.StringArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static com.thoughtworks.go.util.ExceptionUtils.bomb;

public class BuildSession {
    private static final Logger LOG = LoggerFactory.getLogger(BuildSession.class);


    private ConsoleOutputTransmitter console;

    enum BulidCommandType {
        start, end, echo, export, compose, exec;
    }

    private File workingDir;
    private Map<String, String> envs = new HashMap<>();
    private AgentRuntimeInfo agentRuntimeInfo;
    private SystemEnvironment systemEnvironment;
    private HttpService httpService;

    public BuildSession(AgentRuntimeInfo agentRuntimeInfo, SystemEnvironment systemEnvironment, HttpService httpService) {
        this.agentRuntimeInfo = agentRuntimeInfo;
        this.systemEnvironment = systemEnvironment;
        this.httpService = httpService;
    }

    public CommandResult process(BuildCommand command) {
        LOG.info("Processing build command {}", command);
        try {
            BulidCommandType type = BulidCommandType.valueOf(command.getName());
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
        for (Object arg : command.getArgs()) {
            CommandResult childResult = process(parseArbitaryAgentCommand(arg));
            result.addChild(childResult);
            if(!childResult.isSuccess()) {
                result.setExitCode(1);
                break;
            }
        }
        return result;
    }

    private BuildCommand parseArbitaryAgentCommand(Object obj) {
        Map<String, Object> attrs = (Map<String, Object>) obj;
        List args = (List) attrs.get("args");
        BuildCommand cmd = new BuildCommand((String) attrs.get("name"), args.toArray());
        cmd.setWorkingDirectory((String) attrs.get("workingDirectory"));
        return cmd;
    }

    private CommandResult export(BuildCommand command) {
        if(command.getArgs().length > 0) {
            Map<String, String> vars = (Map<String, String>) command.getArgs()[0];
            envs.putAll(vars);
            return successResult();
        } else {
            ArrayList<String> list = new ArrayList<>(envs.size());
            for (String var : envs.keySet()) {
                list.add(String.format("export %s=%s", var, envs.get(var)));
            }
            return process(new BuildCommand("echo", list.toArray()));
        }
    }

    private CommandResult end(BuildCommand command) {
        agentRuntimeInfo.idle();
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

        agentRuntimeInfo.busy(new AgentBuildingInfo(buildLocatorForDisplay, buildLocator));
        this.envs = new HashMap<>();
        console = new ConsoleOutputTransmitter(
                new RemoteConsoleAppender(consoleURI, httpService, agentRuntimeInfo.getIdentifier()));
        this.workingDir = new File("./");
        return successResult();
    }

    private CommandResult successResult() {
        return new CommandResult(0, agentRuntimeInfo);
    }
}
