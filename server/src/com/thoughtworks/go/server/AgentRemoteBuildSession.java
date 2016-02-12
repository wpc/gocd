package com.thoughtworks.go.server;

import com.thoughtworks.go.agent.BuildCommand;
import com.thoughtworks.go.agent.CommandResult;
import com.thoughtworks.go.agent.RemoteBuildSession;
import com.thoughtworks.go.domain.AgentInstance;
import com.thoughtworks.go.domain.JobState;
import com.thoughtworks.go.remote.work.Callback;
import com.thoughtworks.go.server.websocket.AgentRemoteHandler;
import com.thoughtworks.go.util.GoConstants;
import com.thoughtworks.go.websocket.Action;
import com.thoughtworks.go.websocket.Message;

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
    public void start(String buildLocator, String buildLocatorForDisplay, Long buildId, String consoleURI, final Callback<CommandResult> callback) {
        Map<String, Object> sessionSettings = new HashMap<>();
        sessionSettings.put("buildLocator", buildLocator);
        sessionSettings.put("buildLocatorForDisplay", buildLocatorForDisplay);
        sessionSettings.put("consoleURI", consoleURI);
        sessionSettings.put("buildId", buildId.toString());
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
      BuildCommand compose = new BuildCommand("compose");
      compose.setSubCommands(commands.toArray(new BuildCommand[commands.size()]));
      agentRemoteHandler.sendMessageWithCallback(agentInstance.getUuid(),
                new Message(Action.cmd, compose),
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
        BuildCommand end = new BuildCommand("end");
        end.setRunIfConfig("any");
        commands.add(end);
    }


    @Override
    public void addCommand(BuildCommand command) {
        commands.add(command);
    }

    @Override
    public void report(JobState state) {
        addCommand(new BuildCommand("report", state.toString()));
    }
}
