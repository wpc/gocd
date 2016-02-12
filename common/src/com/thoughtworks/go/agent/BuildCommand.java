package com.thoughtworks.go.agent;

import com.thoughtworks.go.config.RunIfConfig;

import java.util.Arrays;
import java.util.Map;

public class BuildCommand {

    public static class Test {
        public final BuildCommand command;
        public final Boolean expectation;

        public Test(BuildCommand command, Boolean expectation) {
            this.command = command;
            this.expectation = expectation;
        }

        @Override
        public String toString() {
            return "Test{" +
                    "command=" + command +
                    ", expectation=" + expectation +
                    '}';
        }
    }

    private final String name;
    private Object[] args;

    private BuildCommand[] subCommands;
    private String workingDirectory;
    private Test test;
    private String runIfConfig = "passed";

    public BuildCommand(String name, String... args) {
        this.name = name;
        this.args = args;
    }

    public BuildCommand(String name, Map args) {
        this.name = name;
        this.args = new Object[] {args};
    }

    public String getName() {
        return name;
    }

    public Object[] getArgs() {
        return args == null ? new String[0] : args;
    }

    @Override
    public String toString() {
        return "BuildCommand{" +
                "name='" + name + '\'' +
                ", args=" + Arrays.toString(args) +
                ", subCommands=" + Arrays.toString(subCommands) +
                ", workingDirectory='" + workingDirectory + '\'' +
                ", test=" + test +
                ", runIfConfig='" + runIfConfig + '\'' +
                '}';
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }


    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public Test getTest() {
        return test;
    }
    public void setTest(BuildCommand command, boolean expectation) {
        this.test = new Test(command, expectation);
    }

    public BuildCommand[] getSubCommands() {
        return subCommands;
    }

    public void setSubCommands(BuildCommand[] subCommands) {
        this.subCommands = subCommands;
    }

    public String getRunIfConfig() {
        return runIfConfig;
    }

    public void setRunIfConfig(String runIfConfig) {
        this.runIfConfig = runIfConfig;
    }

}
