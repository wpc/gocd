package com.thoughtworks.go.agent;

public class AgentCommand {
    private final String name;
    private final Object[] args;
    public AgentCommand(String name, Object... args) {
        this.name = name;
        this.args = args;
    }

    public String getName() {
        return name;
    }

    public Object[] getArgs() {
        return args;
    }
}
