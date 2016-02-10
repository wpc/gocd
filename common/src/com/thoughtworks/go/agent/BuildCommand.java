package com.thoughtworks.go.agent;

public class BuildCommand {
    private final String name;
    private final Object[] args;
    public BuildCommand(String name, Object... args) {
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
