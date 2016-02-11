package com.thoughtworks.go.agent;

import java.util.Arrays;

public class BuildCommand {
    private final String name;
    private final Object[] args;

    private String workingDirectory;

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

    @Override
    public String toString() {
        return "BuildCommand{" +
                "name='" + name + '\'' +
                ", args=" + Arrays.toString(args) +
                ", workingDirectory='" + workingDirectory + '\'' +
                '}';
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }


    public String getWorkingDirectory() {
        return workingDirectory;
    }

}
