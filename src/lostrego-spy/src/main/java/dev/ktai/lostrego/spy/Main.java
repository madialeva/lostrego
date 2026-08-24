package dev.ktai.lostrego.spy;

import picocli.CommandLine;

/**
 * Entry point of the lostrego-spy console application.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SpyCommand()).execute(args);
        System.exit(exitCode);
    }
}
