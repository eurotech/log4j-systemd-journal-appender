package de.bwaldvogel.log4j;

import org.apache.logging.log4j.status.StatusLogger;
import org.newsclub.net.unix.AFUNIXDatagramSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class JournalSocket implements AutoCloseable {

    private static final String DEFAULT_SOCKET_PATH = "/run/systemd/journal/socket";

    private final String socketPath;
    private AFUNIXDatagramSocket socket;
    private final Queue<byte[]> toSendQueue = new LinkedBlockingQueue<>();

    JournalSocket() {
        this(DEFAULT_SOCKET_PATH);
    }

    JournalSocket(String socketPath) {
        this.socketPath = socketPath;
        try {
            init();
        } catch (IOException e) {
            handleSendIOException(e);
        }
    }

    synchronized void send(byte[] data) {
        this.toSendQueue.add(data);

        if (this.socket == null) {
            return;
        }

        try {
            while(!this.toSendQueue.isEmpty()) {
                byte[] toSend = this.toSendQueue.poll();
                AFUNIXSocketAddress target = AFUNIXSocketAddress.of(Path.of(this.socketPath));
                this.socket.getChannel().send(ByteBuffer.wrap(toSend), target);
            }
        } catch (IOException e) {
            handleSendIOException(e);
        } catch (IllegalArgumentException e) {
            handleSendIOException(new IOException("Invalid Unix socket address", e));
        }
    }

    public synchronized void init() throws IOException {
        if (this.socket == null) {
            this.socket = AFUNIXDatagramSocket.newInstance();
        }
    }

    private void handleSendIOException(IOException e) {
        if (!Files.exists(Path.of(this.socketPath))) {
            StatusLogger.getLogger().warn(
                "Systemd journal socket not found at {}, dropping all log events", this.socketPath);
        } else {
            StatusLogger.getLogger().warn(
                "Failed to send to systemd journal, will retry on next event: {}", e.getMessage());
        }

        close();
    }

    @Override
    public synchronized void close() {
        if (this.socket != null) {
            this.socket.close();
            this.socket = null;
        }
    }
}
