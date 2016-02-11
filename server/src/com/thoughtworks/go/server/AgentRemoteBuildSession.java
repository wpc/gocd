package com.thoughtworks.go.server;

import com.thoughtworks.go.agent.BuildCommand;
import com.thoughtworks.go.agent.CommandResult;
import com.thoughtworks.go.agent.RemoteBuildSession;
import com.thoughtworks.go.domain.AgentInstance;
import com.thoughtworks.go.remote.work.BuildWork;
import com.thoughtworks.go.remote.work.Callback;
import com.thoughtworks.go.server.websocket.AgentRemoteHandler;
import com.thoughtworks.go.websocket.Action;
import com.thoughtworks.go.websocket.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentRemoteBuildSession implements RemoteBuildSession {

    private final AgentInstance agentInstance;
    private AgentRemoteHandler agentRemoteHandler;
    private List<BuildCommand> commands = new ArrayList<>();


    public AgentRemoteBuildSession(AgentInstance agentInstance, AgentRemoteHandler agentRemoteHandler) {
        this.agentInstance = agentInstance;
        this.agentRemoteHandler = agentRemoteHandler;
    }

    @Override
    public void start(String buildLocator, String buildLocatorForDisplay, String consoleURI, final Callback<CommandResult> callback) {
        HashMap<String, Object> sessionSettings = new HashMap<>();
        sessionSettings.put("buildLocator", buildLocator);
        sessionSettings.put("buildLocatorForDisplay", buildLocatorForDisplay);
        sessionSettings.put("consoleURI", consoleURI);
        BuildCommand cmd = new BuildCommand("start", sessionSettings);
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
        commands.add(new BuildCommand("export", envs));
    }

    @Override
    public void export() {
        commands.add(new BuildCommand("export"));
    }

  @Override
    public void flush(final Callback<CommandResult> callback) {
        agentRemoteHandler.sendMessageWithCallback(agentInstance.getUuid(),
                new Message(Action.cmd, new BuildCommand("compose", commands.toArray())),
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
        commands.add(new BuildCommand("echo", s));
    }

    @Override
    public void end() {
        if(commands.size() > 0) {
            throw new RuntimeException("should not end build session when there are commands on fly, use flush callback");
        }
        commands.add(new BuildCommand("end"));
        flush(new Callback<CommandResult>() {
            @Override
            public void call(CommandResult result) {
                //do nothing
            }
        });
    }


    @Override
    public void addCommand(BuildCommand command) {
        commands.add(command);
    }
}
