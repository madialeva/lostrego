package dev.ktai.lostrego.spy;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpyCommandTest {

    @Test
    void parsesOptions() {
        SpyCommand command = new SpyCommand();
        new CommandLine(command).parseArgs("-i", "eth0", "-f", "tcp port 80", "-c", "10", "-p", "-x", "-v");
        assertEquals("eth0", command.device);
        assertEquals("tcp port 80", command.filter);
        assertEquals(10, command.count);
        assertTrue(command.promiscuous);
        assertTrue(command.hex);
        assertTrue(command.verbose);
    }

    @Test
    void requiresInterface() {
        SpyCommand command = new SpyCommand();
        assertThrows(CommandLine.ParameterException.class,
                () -> new CommandLine(command).parseArgs());
    }
}
