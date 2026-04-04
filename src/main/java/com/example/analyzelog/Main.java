package com.example.analyzelog;

import com.example.analyzelog.cli.FetchCommand;
import com.example.analyzelog.cli.StatusCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "analyze-logs",
    description = "Fetch and analyze Amazon S3 access logs",
    subcommands = {
        FetchCommand.class,
        StatusCommand.class,
        CommandLine.HelpCommand.class
    },
    mixinStandardHelpOptions = true,
    version = "1.0"
)
public class Main implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
