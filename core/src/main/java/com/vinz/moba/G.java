package com.vinz.moba;

import java.util.ArrayList;

public class G {
    public static final Net net = new Net();
    public static String name = "Guest", host = "127.0.0.1";
    public static int port = 7777;
    public static boolean loggedIn;
    public static int rank;
    public static final ArrayList<String> friendName = new ArrayList<String>();
    public static final ArrayList<Boolean> friendOn = new ArrayList<Boolean>();
    public static int friendsVer;
    public static String invFrom = "";
    public static final ArrayList<String> party = new ArrayList<String>();
    public static boolean queued;
    public static String mode = "classic";
    public static long queueStart;
    public static String toast = "";
    public static long toastAt;
    public static int pickHero = 0;

    public static boolean matchFound, started, ended, respawnPending;
    public static String matchMode = "classic";
    public static int myIdx, winner = -1, rankChange;
    public static long pickEnd, startAt;
    public static final String[] names = new String[10];
    public static final int[] hero = new int[10], anim = new int[10], hp = new int[10], kills = new int[10], deaths = new int[10];
    public static final boolean[] picked = new boolean[10], alive = new boolean[10];
    public static final float[] x = new float[10], z = new float[10], rot = new float[10];
    public static final int[] teamKills = new int[2];

    public static class Msg { public String text = "", sub = ""; public int team, tier; public long at; }
    public static final ArrayList<Msg> announce = new ArrayList<Msg>(), feed = new ArrayList<Msg>();

    public static int team(int i) { return i < 5 ? 0 : 1; }
    public static void say(String s) { toast = s; toastAt = System.currentTimeMillis(); }

    public static String tier(int r) {
        if (r < 100) return "Bronze";
        if (r < 250) return "Silver";
        if (r < 450) return "Gold";
        if (r < 700) return "Platinum";
        if (r < 1000) return "Diamond";
        if (r < 1400) return "Master";
        return "Grandmaster";
    }

    public static void resetMatch() {
        matchFound = false; started = false; ended = false;
        announce.clear(); feed.clear();
    }

    public static void pump() {
        String l;
        while ((l = net.inQ.poll()) != null) {
            try { handle(l); } catch (Exception e) { }
        }
    }

    static void handle(String l) {
        String[] p = l.split(" ");
        String c = p[0];
        long now = System.currentTimeMillis();
        if (c.equals("WELCOME")) { name = p[1]; rank = Integer.parseInt(p[2]); loggedIn = true; }
        else if (c.equals("RANK")) rank = Integer.parseInt(p[1]);
        else if (c.equals("FL")) {
            friendName.clear(); friendOn.clear();
            if (p.length > 1) for (String f : p[1].split(",")) {
                String[] q = f.split(":");
                if (q.length == 2) { friendName.add(q[0]); friendOn.add(q[1].equals("1")); }
            }
            friendsVer++;
        }
        else if (c.equals("INV")) invFrom = p[1];
        else if (c.equals("PARTY")) {
            party.clear();
            if (p.length > 1) for (String s : p[1].split(",")) party.add(s);
            if (party.size() <= 1) party.clear();
        }
        else if (c.equals("QUEUED")) { queued = true; queueStart = now; }
        else if (c.equals("CANCELED")) queued = false;
        else if (c.equals("MATCH")) {
            myIdx = Integer.parseInt(p[1]);
            matchMode = p[2];
            String[] n = p[3].split(",");
            for (int i = 0; i < 10; i++) {
                names[i] = n[i]; hero[i] = 0; picked[i] = false; hp[i] = Heroes.HP[0]; alive[i] = true;
                kills[i] = 0; deaths[i] = 0; anim[i] = 0;
            }
            teamKills[0] = 0; teamKills[1] = 0;
            announce.clear(); feed.clear();
            hero[myIdx] = pickHero;
            pickEnd = now + 15000;
            respawnPending = true;
            matchFound = true; started = false; ended = false; queued = false;
        }
        else if (c.equals("PK")) {
            int i = Integer.parseInt(p[1]);
            hero[i] = Integer.parseInt(p[2]);
            picked[i] = true;
        }
        else if (c.equals("START")) {
            String[] h = p[1].split(",");
            for (int i = 0; i < 10; i++) hero[i] = Integer.parseInt(h[i]);
            started = true;
            startAt = now;
        }
        else if (c.equals("S")) {
            for (int i = 0; i < 10; i++) {
                String[] f = p[i + 1].split(",");
                x[i] = Float.parseFloat(f[0]); z[i] = Float.parseFloat(f[1]); rot[i] = Float.parseFloat(f[2]);
                anim[i] = Integer.parseInt(f[3]); hp[i] = Integer.parseInt(f[4]); alive[i] = f[5].equals("1");
            }
        }
        else if (c.equals("K")) {
            int k = Integer.parseInt(p[1]), v = Integer.parseInt(p[2]);
            StringBuilder sb = new StringBuilder();
            for (int i = 4; i < p.length; i++) { if (i > 4) sb.append(' '); sb.append(p[i]); }
            Msg m = new Msg();
            m.text = sb.toString(); m.tier = Integer.parseInt(p[3]); m.team = team(k); m.at = now;
            m.sub = names[k] + " slew " + names[v];
            announce.add(m);
            Msg fd = new Msg();
            fd.text = names[k] + " > " + names[v]; fd.team = team(k); fd.at = now;
            feed.add(fd);
            if (feed.size() > 6) feed.remove(0);
            kills[k]++; deaths[v]++; teamKills[team(k)]++;
        }
        else if (c.equals("R")) { if (Integer.parseInt(p[1]) == myIdx) respawnPending = true; }
        else if (c.equals("END")) { winner = Integer.parseInt(p[1]); rankChange = Integer.parseInt(p[2]); ended = true; }
        else if (c.equals("ERR")) say(l.length() > 4 ? l.substring(4) : "error");
    }
}
