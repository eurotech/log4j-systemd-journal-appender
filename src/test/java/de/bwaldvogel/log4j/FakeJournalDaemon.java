package de.bwaldvogel.log4j;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.newsclub.net.unix.AFUNIXDatagramSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * Test helper that emulates the receiving side of systemd-journald.
 * <p>
 * It binds an {@code AF_UNIX} datagram socket to a temporary path so that tests can run on any
 * platform supported by junixsocket (Linux, macOS, ...), without requiring a real journal daemon.
 */
final class FakeJournalDaemon implements AutoCloseable {

    private static final int RECEIVE_TIMEOUT_MILLIS = 3000;
    private static final int POLL_TIMEOUT_MILLIS = 300;
    private static final int RECEIVE_BUFFER_SIZE = 128 * 1024;

    private final Path socketPath;
    private final AFUNIXDatagramSocket socket;

    FakeJournalDaemon() throws IOException {
        this(newTemporarySocketPath());
    }

    FakeJournalDaemon(Path socketPath) throws IOException {
        this.socketPath = socketPath;
        this.socket = AFUNIXDatagramSocket.newInstance();
        this.socket.bind(AFUNIXSocketAddress.of(socketPath));
        this.socket.setSoTimeout(RECEIVE_TIMEOUT_MILLIS);
    }

    /**
     * Short directory and file names keep the path below the {@code AF_UNIX} {@code sun_path}
     * limit (104 characters on macOS).
     */
    static Path newTemporarySocketPath() throws IOException {
        Path directory = Files.createTempDirectory("jrnl");
        directory.toFile().deleteOnExit();
        Path socketPath = directory.resolve("j.sock");
        socketPath.toFile().deleteOnExit();
        return socketPath;
    }

    String getSocketPath() {
        return socketPath.toString();
    }

    Map<String, String> receiveEntry() throws IOException {
        return JournalProtocol.parse(receiveDatagram(RECEIVE_TIMEOUT_MILLIS));
    }

    byte[] receiveDatagram(int timeoutMillis) throws IOException {
        socket.setSoTimeout(timeoutMillis);
        byte[] buffer = new byte[RECEIVE_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return Arrays.copyOf(packet.getData(), packet.getLength());
    }

    boolean receivesNothing() {
        try {
            receiveDatagram(POLL_TIMEOUT_MILLIS);
            return false;
        } catch (SocketTimeoutException e) {
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected error while polling the journal socket", e);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
        Files.deleteIfExists(socketPath);
    }
}
