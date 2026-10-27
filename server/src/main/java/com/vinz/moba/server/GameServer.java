package com.vinz.moba.server;

import java.io.*;
import java.net.*;
import java.util.*;

public class GameServer {
    static final Object L = new Object();
    static final int[] HPMAX = {1300, 850, 950, 1900};
    static final Map<String, Client> online = new HashMap<String, Client>();
    static final Map<String, Integer> ranks = new HashMap<String, Integer>();
    static final Map<String, Set<String>> friends = new HashMap<String, Set<String>>();
    static final List<QE> queue = new ArrayList<QE>();
    static final List<Match> matches = new ArrayList<Match>();
    static final Random R = new Random();
    static final File DATA = new File("vinzmoba-data.txt");

    static class Party { Client leader; List<Client> members = new ArrayList<Client>(); }
    static class QE { List<Client> g = new ArrayList<Client>(); String mode; long t; }
    static class Client {
        Socket s; PrintWriter out; String name; Match m; int idx = -1; Party party; QE qe;
        void send(String l) { synchronized (this) { if (out != null) { out.println(l); out.flush(); } } }
    }
    static class Match {
        String mode;
        Client[] slot = new Client[10];
        String[] name = new String[10];
        int[] hero = new int[10], anim = new int[10], hp = new int[10], combo = new int[10], streak = new int[10];
        float[] x = new float[10], z = new float[10], rot = new float[10];
        boolean[] alive = new boolean[10];
        long[] respawnAt = new long[10], lastKill = new long[10], botNext = new long[10];
        int[] teamKills = new int[2];
        int phase = 0;
        long pickEnd, startAt;
        boolean firstBlood;
    }

    public static void main(String[] a) throws Exception {
        int port = 7777;
        if (a.length > 0) port = Integer.parseInt(a[0]);
        else if (System.getenv("PORT") != null) port = Integer.parseInt(System.getenv("PORT"));
        load();
        Thread t = new Thread(new Runnable() { public void run() { tickLoop(); } });
        t.setDaemon(true);
        t.start();
        ServerSocket ss = new ServerSocket(port);
        System.out.println("Vinz Arena server listening on " + port);
        while (true) {
            final Socket s = ss.accept();
            new Thread(new Runnable() { public void run() { handle(s); } }).start();
        }
    }

    static String clean(String s) {
        s = s.replaceAll("[^A-Za-z0-9_#.\\-]", "_");
        if (s.length() > 16) s = s.substring(0, 16);
        return s.isEmpty() ? "Player" : s;
    }

    static void handle(Socket s) {
        Client c = new Client();
        c.s = s;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            c.out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                synchronized (L) { try { process(c, line.trim()); } catch (Exception e) { e.printStackTrace(); } }
            }
        } catch (Exception e) { }
        synchronized (L) { disconnect(c); }
        try { s.close(); } catch (Exception e) { }
    }

    static void process(Client c, String l) {
        if (l.isEmpty()) return;
        String[] p = l.split(" ");
        String cmd = p[0];
        if (cmd.equals("HELLO")) {
            if (p.length < 2) return;
            String n = clean(p[1]);
            Client old = online.get(n);
            if (old != null && old != c) {
                old.send("ERR logged in from another device");
                disconnect(old);
                try { old.s.close(); } catch (Exception e) { }
            }
            c.name = n;
            online.put(n, c);
            if (!ranks.containsKey(n)) { ranks.put(n, 0); save(); }
            c.send("WELCOME " + n + " " + ranks.get(n));
            sendFriends(c);
            return;
        }
        if (c.name == null) return;
        if (cmd.equals("FRIEND") && p.length > 1) {
            String f = clean(p[1]);
            if (f.equals(c.name)) { c.send("ERR cannot add yourself"); return; }
            if (!ranks.containsKey(f)) { c.send("ERR player not found (must login once)"); return; }
            addFriend(c.name, f);
            addFriend(f, c.name);
            save();
            sendFriends(c);
            Client o = online.get(f);
            if (o != null) sendFriends(o);
        } else if (cmd.equals("FL")) {
            sendFriends(c);
        } else if (cmd.equals("INVITE") && p.length > 1) {
            Client t = online.get(clean(p[1]));
            if (t == null) { c.send("ERR player offline"); return; }
            t.send("INV " + c.name);
            c.send("ERR invite sent");
        } else if (cmd.equals("JOIN") && p.length > 1) {
            Client ld = online.get(clean(p[1]));
            if (ld == null || ld == c || c.m != null) return;
            if (ld.party == null) { ld.party = new Party(); ld.party.leader = ld; ld.party.members.add(ld); }
            if (ld.party.members.size() >= 5) { c.send("ERR party full"); return; }
            leaveParty(c);
            ld.party.members.add(c);
            c.party = ld.party;
            broadcastParty(ld.party);
        } else if (cmd.equals("LEAVEP")) {
            leaveParty(c);
        } else if (cmd.equals("QUEUE") && p.length > 1) {
            if (c.m != null || c.qe != null) return;
            if (c.party != null && c.party.leader != c) { c.send("ERR only party leader can start"); return; }
            QE e = new QE();
            e.mode = p[1].equals("rank") ? "rank" : "classic";
            e.t = System.currentTimeMillis();
            if (c.party != null) e.g.addAll(c.party.members); else e.g.add(c);
            for (Client x : e.g) { if (x.m != null || x.qe != null) return; }
            for (Client x : e.g) { x.qe = e; x.send("QUEUED"); }
            queue.add(e);
        } else if (cmd.equals("CANCEL")) {
            if (c.qe != null) cancelQueue(c.qe);
        } else if (cmd.equals("PICK") && p.length > 1) {
            Match m = c.m;
            if (m == null || m.phase != 0) return;
            int h = Math.max(0, Math.min(3, Integer.parseInt(p[1])));
            m.hero[c.idx] = h;
            m.hp[c.idx] = HPMAX[h];
            broadcast(m, "PK " + c.idx + " " + h);
        } else if (cmd.equals("POS") && p.length >= 5) {
            Match m = c.m;
            if (m == null || m.phase != 1 || !m.alive[c.idx]) return;
            m.x[c.idx] = clamp(Float.parseFloat(p[1]), -49, 49);
            m.z[c.idx] = clamp(Float.parseFloat(p[2]), -29, 29);
            m.rot[c.idx] = Float.parseFloat(p[3]);
            m.anim[c.idx] = Integer.parseInt(p[4]);
        } else if (cmd.equals("HIT") && p.length >= 3) {
            Match m = c.m;
            if (m == null || m.phase != 1) return;
            int a = c.idx, t = Integer.parseInt(p[1]);
            int dmg = Math.max(1, Math.min(400, Integer.parseInt(p[2])));
            if (t < 0 || t > 9 || (a < 5) == (t < 5) || !m.alive[a] || !m.alive[t]) return;
            float dx = m.x[a] - m.x[t], dz = m.z[a] - m.z[t];
            if (dx * dx + dz * dz > 20 * 20) return;
            damage(m, a, t, dmg, System.currentTimeMillis());
        }
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static void addFriend(String a, String b) {
        Set<String> s = friends.get(a);
        if (s == null) { s = new HashSet<String>(); friends.put(a, s); }
        s.add(b);
    }

    static void sendFriends(Client c) {
        StringBuilder sb = new StringBuilder("FL ");
        Set<String> s = friends.get(c.name);
        if (s != null) {
            boolean first = true;
            for (String f : s) {
                if (!first) sb.append(',');
                first = false;
                sb.append(f).append(':').append(online.containsKey(f) ? 1 : 0);
            }
        }
        c.send(sb.toString());
    }

    static void leaveParty(Client c) {
        Party p = c.party;
        if (p == null) return;
        if (c.qe != null) cancelQueue(c.qe);
        p.members.remove(c);
        c.party = null;
        c.send("PARTY " + c.name);
        if (p.members.size() <= 1) {
            for (Client x : p.members) { x.party = null; x.send("PARTY " + x.name); }
            p.members.clear();
        } else {
            if (p.leader == c) p.leader = p.members.get(0);
            broadcastParty(p);
        }
    }

    static void broadcastParty(Party p) {
        StringBuilder sb = new StringBuilder("PARTY ");
        for (int i = 0; i < p.members.size(); i++) { if (i > 0) sb.append(','); sb.append(p.members.get(i).name); }
        for (Client x : p.members) x.send(sb.toString());
    }

    static void cancelQueue(QE e) {
        queue.remove(e);
        for (Client x : e.g) { x.qe = null; x.send("CANCELED"); }
    }

    static void disconnect(Client c) {
        if (c.name != null && online.get(c.name) == c) online.remove(c.name);
        if (c.qe != null) cancelQueue(c.qe);
        leaveParty(c);
        if (c.m != null) {
            Match m = c.m;
            m.slot[c.idx] = null;
            c.m = null;
            boolean any = false;
            for (Client x : m.slot) if (x != null) any = true;
            if (!any) matches.remove(m);
        }
        c.out = null;
    }

    static void tickLoop() {
        long last = System.currentTimeMillis();
        while (true) {
            try { Thread.sleep(50); } catch (Exception e) { }
            long now = System.currentTimeMillis();
            float dt = (now - last) / 1000f;
            last = now;
            synchronized (L) {
                try {
                    matchmake(now);
                    for (Match m : new ArrayList<Match>(matches)) tick(m, now, dt);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    static void matchmake(long now) {
        String[] modes = {"rank", "classic"};
        for (String mode : modes) {
            List<QE> q = new ArrayList<QE>();
            int humans = 0;
            long oldest = now;
            for (QE e : queue) if (e.mode.equals(mode)) { q.add(e); humans += e.g.size(); oldest = Math.min(oldest, e.t); }
            if (q.isEmpty()) continue;
            if (humans < 10 && now - oldest < 12000) continue;
            Collections.sort(q, new Comparator<QE>() { public int compare(QE a, QE b) { return b.g.size() - a.g.size(); } });
            List<QE> t0 = new ArrayList<QE>(), t1 = new ArrayList<QE>();
            int c0 = 0, c1 = 0;
            for (QE e : q) {
                int sz = e.g.size();
                if (c0 <= c1 && c0 + sz <= 5) { t0.add(e); c0 += sz; }
                else if (c1 + sz <= 5) { t1.add(e); c1 += sz; }
                else if (c0 + sz <= 5) { t0.add(e); c0 += sz; }
            }
            if (c0 + c1 == 0) continue;
            createMatch(mode, t0, t1, now);
        }
    }

    static void createMatch(String mode, List<QE> t0, List<QE> t1, long now) {
        Match m = new Match();
        m.mode = mode;
        int[] next = {0, 5};
        List<List<QE>> teams = new ArrayList<List<QE>>();
        teams.add(t0);
        teams.add(t1);
        for (int t = 0; t < 2; t++) {
            for (QE e : teams.get(t)) {
                queue.remove(e);
                for (Client c : e.g) {
                    int i = next[t]++;
                    m.slot[i] = c;
                    m.name[i] = c.name;
                    c.m = m;
                    c.idx = i;
                    c.qe = null;
                }
            }
        }
        int bn = 1;
        for (int i = 0; i < 10; i++) {
            if (m.slot[i] == null) { m.name[i] = "Bot" + (bn++); m.hero[i] = R.nextInt(4); }
            m.hp[i] = HPMAX[m.hero[i]];
            m.alive[i] = true;
            spawn(m, i);
        }
        m.pickEnd = now + 15000;
        matches.add(m);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < 10; i++) { if (i > 0) names.append(','); names.append(m.name[i]); }
        for (int i = 0; i < 10; i++) if (m.slot[i] != null) m.slot[i].send("MATCH " + i + " " + mode + " " + names);
        for (int i = 0; i < 10; i++) if (m.slot[i] == null) broadcast(m, "PK " + i + " " + m.hero[i]);
    }

    static void spawn(Match m, int i) {
        m.x[i] = i < 5 ? -42 : 42;
        m.z[i] = ((i % 5) - 2) * 4f;
        m.rot[i] = i < 5 ? 90 : -90;
        m.anim[i] = 0;
    }

    static void broadcast(Match m, String l) {
        for (Client c : m.slot) if (c != null) c.send(l);
    }

    static String f(float v) { return String.valueOf(Math.round(v * 10) / 10.0); }

    static void tick(Match m, long now, float dt) {
        if (m.phase == 0 && now >= m.pickEnd) {
            m.phase = 1;
            m.startAt = now;
            StringBuilder sb = new StringBuilder("START ");
            for (int i = 0; i < 10; i++) { if (i > 0) sb.append(','); sb.append(m.hero[i]); }
            broadcast(m, sb.toString());
        }
        if (m.phase == 1) {
            for (int i = 0; i < 10; i++) {
                if (!m.alive[i] && now >= m.respawnAt[i]) {
                    m.alive[i] = true;
                    m.hp[i] = HPMAX[m.hero[i]];
                    spawn(m, i);
                    broadcast(m, "R " + i);
                }
            }
            for (int i = 0; i < 10; i++) {
                if (m.slot[i] != null || !m.alive[i]) continue;
                int best = -1;
                float bd = 1e9f;
                for (int j = 0; j < 10; j++) {
                    if ((i < 5) == (j < 5) || !m.alive[j]) continue;
                    float dx = m.x[j] - m.x[i], dz = m.z[j] - m.z[i], d = dx * dx + dz * dz;
                    if (d < bd) { bd = d; best = j; }
                }
                if (best < 0) continue;
                float d = (float) Math.sqrt(bd);
                float dx = m.x[best] - m.x[i], dz = m.z[best] - m.z[i];
                m.rot[i] = (float) Math.toDegrees(Math.atan2(dx, dz));
                if (d > 2.8f) {
                    m.x[i] = clamp(m.x[i] + dx / d * 4.2f * dt, -49, 49);
                    m.z[i] = clamp(m.z[i] + dz / d * 4.2f * dt, -29, 29);
                    m.anim[i] = 1;
                } else {
                    m.anim[i] = 2;
                    if (now >= m.botNext[i]) {
                        m.botNext[i] = now + 1100;
                        damage(m, i, best, 55 + R.nextInt(35), now);
                    }
                }
            }
            int win = -1;
            if (m.teamKills[0] >= 30) win = 0;
            else if (m.teamKills[1] >= 30) win = 1;
            else if (now - m.startAt > 600000) win = m.teamKills[0] >= m.teamKills[1] ? 0 : 1;
            if (win >= 0) { end(m, win); return; }
        }
        StringBuilder sb = new StringBuilder("S");
        for (int i = 0; i < 10; i++) {
            sb.append(' ').append(f(m.x[i])).append(',').append(f(m.z[i])).append(',').append(f(m.rot[i])).append(',')
                    .append(m.alive[i] ? m.anim[i] : 6).append(',').append(m.hp[i]).append(',').append(m.alive[i] ? 1 : 0);
        }
        broadcast(m, sb.toString());
    }

    static void damage(Match m, int a, int t, int dmg, long now) {
        if (!m.alive[t]) return;
        m.hp[t] -= dmg;
        if (m.hp[t] <= 0) kill(m, a, t, now);
    }

    static void kill(Match m, int a, int v, long now) {
        m.alive[v] = false;
        m.hp[v] = 0;
        m.respawnAt[v] = now + 5000 + Math.min(8000, (now - m.startAt) / 60);
        int vs = m.streak[v];
        m.streak[v] = 0;
        m.combo[v] = 0;
        m.streak[a]++;
        if (now - m.lastKill[a] <= 10000) m.combo[a]++; else m.combo[a] = 1;
        m.lastKill[a] = now;
        m.teamKills[a < 5 ? 0 : 1]++;
        String t;
        int tier;
        if (!m.firstBlood) { m.firstBlood = true; t = "FIRST BLOOD"; tier = 2; }
        else {
            switch (m.combo[a]) {
                case 1: t = "SLAIN"; tier = 1; break;
                case 2: t = "DOUBLE KILL"; tier = 2; break;
                case 3: t = "TRIPLE KILL"; tier = 3; break;
                case 4: t = "MANIAC"; tier = 4; break;
                case 5: t = "SAVAGE"; tier = 5; break;
                default: t = "LEGENDARY"; tier = 6;
            }
        }
        if (vs >= 3) t += " - SHUTDOWN";
        broadcast(m, "K " + a + " " + v + " " + tier + " " + t);
    }

    static void end(Match m, int win) {
        matches.remove(m);
        for (int i = 0; i < 10; i++) {
            Client c = m.slot[i];
            if (c == null) continue;
            int team = i < 5 ? 0 : 1, d = 0;
            if (m.mode.equals("rank")) {
                int old = ranks.get(c.name);
                int nr = Math.max(0, old + (team == win ? 25 : -15));
                d = nr - old;
                ranks.put(c.name, nr);
            }
            c.send("END " + win + " " + d);
            c.send("RANK " + ranks.get(c.name));
            c.m = null;
            c.idx = -1;
        }
        save();
    }

    static void save() {
        try {
            PrintWriter w = new PrintWriter(new FileWriter(DATA));
            for (Map.Entry<String, Integer> e : ranks.entrySet()) w.println("R " + e.getKey() + " " + e.getValue());
            for (Map.Entry<String, Set<String>> e : friends.entrySet())
                for (String f : e.getValue()) w.println("F " + e.getKey() + " " + f);
            w.close();
        } catch (Exception e) { }
    }

    static void load() {
        try {
            if (!DATA.exists()) return;
            BufferedReader r = new BufferedReader(new FileReader(DATA));
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.split(" ");
                if (p[0].equals("R") && p.length == 3) ranks.put(p[1], Integer.parseInt(p[2]));
                else if (p[0].equals("F") && p.length == 3) addFriend(p[1], p[2]);
            }
            r.close();
        } catch (Exception e) { }
    }
}
