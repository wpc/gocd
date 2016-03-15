package com.thoughtworks.go.plugin.api;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    public static Map<String, String> toMap(String... args) {
        Map<String, String> ret = new HashMap<>();
        for (int i=0; i<args.length; i+=2) {
            ret.put(args[i], args[i+1]);
        }
        return ret;
    }

    public static Map<String, String> toList(String... args) {
        Map<String, String> ret = new HashMap<>();
        for(int i=0; i<args.length; i++) {
            ret.put(String.valueOf(i), args[i]);
        }
        return ret;
    }

    public static BuildCommand exec(String command, String[] args) {
        Map<String, String> cmdArgs = new HashMap<>();
        cmdArgs.put("command", command);
        cmdArgs.putAll(toList(args));
        return new BuildCommand("exec", cmdArgs);
    }

    public static BuildCommand echo(String... content) {
        return new BuildCommand("echo", toList(content));
    }

    private final String name;
    private Map<String, String> args = new HashMap<>();
    private BuildCommand[] subCommands;
    private String workingDirectory;
    private Test test;
    private String runIfConfig = "passed";

    public BuildCommand(String name) {
        this.name = name;
        this.subCommands = new BuildCommand[0];
    }

    public BuildCommand(String name, List<BuildCommand> subCommands) {
        this(name);
        this.subCommands = subCommands.toArray(new BuildCommand[subCommands.size()]);
    }

    public BuildCommand(String name, BuildCommand... subCommand) {
        this(name);
        this.subCommands = subCommand;
    }

    public BuildCommand(String name, Map<String, String> args) {
        this(name);
        this.args = args;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public String dump(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
        sb.append(name);

        for (String argName : args.keySet()) {
            sb.append(" ").append(argName).append("='" + args.get(argName)).append("'");
        }

        if(!"passed".equals(runIfConfig)) {
            sb.append(" ").append("(runIf:").append(runIfConfig).append(")");
        }
        for (BuildCommand subCommand : subCommands) {
            sb.append("\n").append(subCommand.dump(indent + 1));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "BuildCommand{" +
                "name='" + name + '\'' +
                ", args=" + args +
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
        return workingDirectory == null ? "." : workingDirectory;
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

    public String getRunIfConfig() {
        return runIfConfig;
    }

    public void setRunIfConfig(String runIfConfig) {
        this.runIfConfig = runIfConfig;
    }

    public BuildCommand runIf(String any) {
        setRunIfConfig(any);
        return this;
    }

    public String[] getArgsList(int size) {
        String[] sources = new String[size];
        for (int i=0; i<size; i++) {
            sources[i] = this.getArgs().get(String.valueOf(i));
        }
        return sources;
    }
}
