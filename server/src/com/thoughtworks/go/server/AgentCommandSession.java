package com.thoughtworks.go.server;

import com.thoughtworks.go.agent.AgentCommand;
import com.thoughtworks.go.agent.CommandResult;
import com.thoughtworks.go.agent.CommandSession;
import com.thoughtworks.go.domain.AgentInstance;
import com.thoughtworks.go.remote.work.Callback;
import com.thoughtworks.go.server.websocket.AgentRemoteHandler;
import com.thoughtworks.go.websocket.Action;
import com.thoughtworks.go.websocket.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentCommandSession implements CommandSession {

    private final AgentInstance agentInstance;
    private AgentRemoteHandler agentRemoteHandler;
    private List<AgentCommand> commands = new ArrayList<>();


    public AgentCommandSession(AgentInstance agentInstance, AgentRemoteHandler agentRemoteHandler) {
        this.agentInstance = agentInstance;
        this.agentRemoteHandler = agentRemoteHandler;
    }

    @Override
    public void start(String buildLocator, String buildLocatorForDisplay, String consoleURI, final Callback<CommandResult> callback) {
        HashMap<String, Object> sessionSettings = new HashMap<>();
        sessionSettings.put("buildLocator", buildLocator);
        sessionSettings.put("buildLocatorForDisplay", buildLocatorForDisplay);
        sessionSettings.put("consoleURI", consoleURI);
        AgentCommand cmd = new AgentCommand("start", sessionSettings);
        agentRemoteHandler.sendMessageWithCallback(agentInstance.getUuid(),
                new Message(Action.cmd, cmd),
                new Callback<Object>() {
                    @Override
                    public void call(Object commandResult) {
                        callback.call((CommandResult) commandResult);
                    }
                });
    }

    @Override
    public void export(Map<String, String> envs) {
        commands.add(new AgentCommand("export", envs));
    }

    @Override
    public void export() {
        commands.add(new AgentCommand("export"));
    }

    @Override
    public void chdir(File workingDirectory) {
        commands.add(new AgentCommand("chdir", workingDirectory.getPath()));
    }

    @Override
    public void flush(final Callback<CommandResult> callback) {
        agentRemoteHandler.sendMessageWithCallback(agentInstance.getUuid(),
                new Message(Action.cmd, new AgentCommand("compose", commands.toArray())),
                new Callback<Object>() {
                    @Override
                    public void call(Object commandResult) {
                        callback.call((CommandResult) commandResult);
                    }
                });
        commands = new ArrayList<>();
    }

    @Override
    public void echo(String s) {
        commands.add(new AgentCommand("echo", s));
    }

    @Override
    public void end() {
        commands.add(new AgentCommand("end"));
    }
}
