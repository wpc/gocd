package com.thoughtworks.go.agent;

import com.thoughtworks.go.server.service.AgentRuntimeInfo;

import java.util.ArrayList;

public class CommandResult {

    private final ArrayList<CommandResult> children;
    private int exitCode;
    private final String stdout;
    private final String stderr;
    private AgentRuntimeInfo agentRuntimeInfo;

    public CommandResult(int exitCode, String stdout, String stderr, AgentRuntimeInfo agentRuntimeInfo) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.agentRuntimeInfo = agentRuntimeInfo;
        this.children = new ArrayList<>();
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public AgentRuntimeInfo getAgentRuntimeInfo() {
        return agentRuntimeInfo;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public void addChild(CommandResult childResult) {
        children.add(childResult);
    }
}
