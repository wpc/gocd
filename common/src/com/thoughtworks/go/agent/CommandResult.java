package com.thoughtworks.go.agent;

import com.thoughtworks.go.server.service.AgentRuntimeInfo;

import java.util.ArrayList;

public class CommandResult {

    private final ArrayList<CommandResult> children;
    private int exitCode;
    private AgentRuntimeInfo agentRuntimeInfo;
    private String error;

    public CommandResult(int exitCode, AgentRuntimeInfo agentRuntimeInfo) {
        this(exitCode, agentRuntimeInfo, null);
    }

    public CommandResult(int exitCode, AgentRuntimeInfo agentRuntimeInfo, String error) {
        this.exitCode = exitCode;
        this.agentRuntimeInfo = agentRuntimeInfo;
        this.error = error;
        this.children = new ArrayList<>();
    }

    public boolean isSuccess() {
        return exitCode == 0;
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

    @Override
    public String toString() {
        return "CommandResult{" +
                "children=" + children +
                ", exitCode=" + exitCode +
                ", error='" + error + '\'' +
                '}';
    }

    public String getError() {
        return error;
    }
}
