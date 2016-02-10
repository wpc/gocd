package com.thoughtworks.go.agent;

import com.thoughtworks.go.remote.work.ConsoleOutputTransmitter;
import com.thoughtworks.go.remote.work.RemoteConsoleAppender;
import com.thoughtworks.go.server.service.AgentBuildingInfo;
import com.thoughtworks.go.server.service.AgentRuntimeInfo;
import com.thoughtworks.go.util.HttpService;
import com.thoughtworks.go.util.SystemEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuildSession {

    private ConsoleOutputTransmitter console;

    enum BulidCommandType {
        start, end, echo, export, chdir, compose;
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
        BulidCommandType type = BulidCommandType.valueOf(command.getName());
        switch (type) {
            case start:
                return start(command);
            case compose:
                return compose(command);
            case chdir:
                return chdir(command);
            case echo:
                return echo(command);
            case export:
                return export(command);
            case end:
                return end(command);
        }
        throw new RuntimeException("Unknown command: " + command);
    }

    private CommandResult compose(BuildCommand command) {
        CommandResult result = new CommandResult(0, "", "", agentRuntimeInfo);
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
        return new BuildCommand((String) attrs.get("name"), args.toArray());
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

    private CommandResult chdir(BuildCommand command) {
        this.workingDir = new File((String) command.getArgs()[0]);
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
        return successResult();
    }

    private CommandResult successResult() {
        return new CommandResult(0, "", "", agentRuntimeInfo);
    }
}
