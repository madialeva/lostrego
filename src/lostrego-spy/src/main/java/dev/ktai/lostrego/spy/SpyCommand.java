package dev.ktai.lostrego.spy;

import dev.ktai.lostrego.core.CaptureException;
import dev.ktai.lostrego.core.CaptureStatistics;
import dev.ktai.lostrego.spy.common.CaptureRunner;
import dev.ktai.lostrego.spy.common.CaptureSettings;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * The picocli command: parses CLI options, opens a capture through
 * {@link CaptureRunner}, renders analyzed packets and stops cleanly.
 */
@Command(
        name = "lostrego-spy",
        mixinStandardHelpOptions = true,
        version = "lostrego-spy 0.1.0",
        description = "Capture and analyze network traffic using the lostrego library.")
public final class SpyCommand implements Callable<Integer> {

    @Option(names = {"-i", "--interface"}, required = true, description = "network device to capture from")
    String device;

    @Option(names = {"-f", "--filter"}, description = "BPF filter expression")
    String filter;

    @Option(names = {"-c", "--count"}, defaultValue = "0", description = "stop after N packets (0 = unlimited)")
    long count;

    @Option(names = {"-p", "--promiscuous"}, description = "enable promiscuous mode")
    boolean promiscuous;

    @Option(names = {"-x", "--hex"}, description = "print a hex/ascii dump of each packet")
    boolean hex;

    @Option(names = {"-v", "--verbose"}, description = "include the link type in each line")
    boolean verbose;

    @Override
    public Integer call() {
        CaptureSettings settings = CaptureSettings.builder()
                .device(device)
                .filter(filter)
                .promiscuous(promiscuous)
                .packetCount(count)
                .build();

        try (CaptureRunner runner = CaptureRunner.open(settings)) {
            ConsoleRenderer renderer = new ConsoleRenderer(System.out, verbose, hex);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> runner.stop(), "spy-shutdown"));
            printStatistics(runner.run(renderer));
            return 0;
        } catch (CaptureException e) {
            System.err.println("lostrego-spy: " + e.getMessage());
            return 1;
        }
    }

    private static void printStatistics(CaptureStatistics stats) {
        System.out.printf("received=%d dropped=%d ifdropped=%d%n",
                stats.received(), stats.dropped(), stats.interfaceDropped());
    }
}
