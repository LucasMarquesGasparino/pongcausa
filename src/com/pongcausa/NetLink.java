package com.pongcausa;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camada de rede para multiplayer: um celular cria a sala (servidor), o outro entra (cliente).
 * Protocolo UDP/IP simples na rede local (Wi-Fi), mantendo o mesmo formato de mensagens:
 * "paddle|<pos>|<t>"; "score|<s1>|<s2>"; "hello"; "role|<A|B>"; "event|<...>".
 */
public class NetLink {
    public interface Listener {
        void onMessage(String msg);
        void onPeerConnected(String who);
    }

    public static final int PORT = 47474;
    private DatagramSocket socket;
    private InetAddress peerAddr;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Listener listener;
    private Thread rxThread;
    public volatile boolean isHost;

    public NetLink(Listener listener) { this.listener = listener; }

    /** HOST: abre porta e aguarda "hello" de um cliente. */
    public void startHost() throws IOException {
        isHost = true;
        socket = new DatagramSocket(PORT);
        socket.setBroadcast(false);
        socket.setSoTimeout(0);
        running.set(true);
        rxThread = new Thread(() -> {
            byte[] buf = new byte[512];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            try {
                // primeiro pacote define o par
                while (running.get()) {
                    socket.receive(pkt);
                    peerAddr = pkt.getAddress();
                    String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8).trim();
                    if (msg.startsWith("hello")) {
                        send("role|A");
                        if (listener != null) listener.onPeerConnected("B");
                        break;
                    }
                }
                loopRx();
            } catch (Exception ignored) {}
        }, "net-host-rx");
        rxThread.start();
    }

    /** CLIENTE: envia "hello" para o host até ser aceito. */
    public void startClient(String hostIp) throws IOException {
        isHost = false;
        socket = new DatagramSocket();
        socket.setSoTimeout(1500);
        peerAddr = InetAddress.getByName(hostIp);
        running.set(true);
        rxThread = new Thread(() -> {
            byte[] buf = new byte[512];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            int tries = 0;
            try {
                while (running.get()) {
                    try {
                        send("hello");
                        socket.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8).trim();
                        if (msg.startsWith("role")) {
                            if (listener != null) listener.onPeerConnected(msg);
                            socket.setSoTimeout(0);
                            break;
                        }
                    } catch (SocketTimeoutException ste) {
                        tries++;
                        if (tries > 40) { running.set(false); return; }
                    }
                }
                loopRx();
            } catch (Exception ignored) {}
        }, "net-client-rx");
        rxThread.start();
    }

    private void loopRx() {
        byte[] buf = new byte[512];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        while (running.get()) {
            try {
                socket.receive(pkt);
                String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8).trim();
                if (listener != null) listener.onMessage(msg);
            } catch (IOException e) {
                if (running.get()) continue; else return;
            }
        }
    }

    public void send(String msg) {
        try {
            if (socket != null && !socket.isClosed() && peerAddr != null) {
                byte[] b = msg.getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(b, b.length, peerAddr, PORT));
            }
        } catch (IOException ignored) {}
    }

    public boolean isRunning() { return running.get(); }
    public boolean isReady() { return peerAddr != null && running.get(); }

    public void close() {
        running.set(false);
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    public static String localIp() {
        try {
            DatagramSocket s = new DatagramSocket();
            s.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = s.getLocalAddress().getHostAddress();
            s.close();
            return ip;
        } catch (Exception e) { return "0.0.0.0"; }
    }
}
