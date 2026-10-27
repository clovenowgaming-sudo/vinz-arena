package com.vinz.moba;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Net {
    public final ConcurrentLinkedQueue<String> inQ = new ConcurrentLinkedQueue<String>();
    private final LinkedBlockingQueue<String> outQ = new LinkedBlockingQueue<String>();
    public volatile int state = 0; // 0 idle, 1 connecting, 2 ok, 3 failed, 4 closed
    public volatile String err = "";
    private Socket sock;

    public void connect(final String host, final int port, final String hello) {
        state = 1;
        err = "";
        outQ.clear();
        inQ.clear();
        new Thread(new Runnable() {
            public void run() {
                try {
                    sock = new Socket();
                    sock.connect(new InetSocketAddress(host, port), 7000);
                    sock.setTcpNoDelay(true);
                    final BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
                    final PrintWriter out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), "UTF-8"));
                    state = 2;
                    out.println(hello);
                    out.flush();
                    Thread w = new Thread(new Runnable() {
                        public void run() {
                            try {
                                while (state == 2) {
                                    String m = outQ.poll(200, TimeUnit.MILLISECONDS);
                                    if (m != null) { out.println(m); out.flush(); }
                                }
                            } catch (Exception e) { }
                        }
                    });
                    w.setDaemon(true);
                    w.start();
                    String l;
                    while ((l = in.readLine()) != null) inQ.add(l);
                } catch (Exception e) {
                    err = String.valueOf(e.getMessage());
                }
                state = (state == 2) ? 4 : 3;
            }
        }).start();
    }

    public void send(String s) { if (state == 2) outQ.add(s); }

    public void close() {
        try { if (sock != null) sock.close(); } catch (Exception e) { }
    }
}
