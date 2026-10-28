#!/usr/bin/env python3
# Vinz Arena - authoritative game server (pure Python 3, no dependencies)
# Run:  python server.py [port]      (default 7777)
import socket, threading, time, math, random, json, os, re, sys

L = threading.RLock()
DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "vinzmoba-data.json")
HERO_HP = [1300, 850, 950, 1900]
HERO_SPD = [6.4, 5.8, 6.2, 5.4]
# S[hero][k] = (kind, a, b, dmg, cd)  k: 0 basic, 1..3 skills
# kind: 0 melee arc, 1 aoe self, 2 dash, 3 bolt nearest, 4 line, 5 aoe at target, 6 back dash
S = [
    [(0, 3.4, 0, 100, 0.7), (1, 4.6, 0, 160, 4), (2, 8, 2.2, 200, 6), (1, 6.5, 0, 320, 14)],
    [(3, 11, 0, 90, 0.8), (3, 13, 0, 190, 3), (1, 5.5, 0, 140, 6), (5, 14, 5.5, 330, 14)],
    [(3, 13, 0, 85, 0.55), (4, 15, 1.8, 170, 3.5), (6, 7, 0, 0, 5), (5, 14, 5, 260, 13)],
    [(0, 3.2, 0, 70, 0.8), (0, 4.2, 0, 130, 4), (1, 5.5, 0, 150, 7), (1, 6.5, 0, 200, 14)],
]
LANES = [-18.0, 0.0, 18.0]
TOWER_X = [28.0, 14.0]
NEXUS_X = 43.0
BX, BZ = 49.0, 29.0
# kinds: 0 melee minion, 1 ranged minion, 2 small monster, 3 big monster, 4 tower, 5 nexus, 9 hero
CAMPS = [(-22, -9, 2), (-22, 9, 2), (22, -9, 2), (22, 9, 2), (0, -24, 3), (0, 24, 3), (-8, -9, 2), (8, 9, 2)]

online, ranks, friends, queue, matches = {}, {}, {}, [], []


def clean(s):
    s = re.sub(r"[^A-Za-z0-9_#.\-]", "_", s)[:16]
    return s or "Player"


def ang(dx, dz):
    return math.degrees(math.atan2(dx, dz))


def dist(a, b):
    return math.hypot(a.x - b.x, a.z - b.z)


class Client:
    def __init__(s, sock):
        s.sock = sock; s.lock = threading.Lock(); s.name = None; s.m = None; s.idx = -1
        s.party = None; s.qe = None


def send(c, line):
    try:
        with c.lock:
            c.sock.sendall((line + "\n").encode())
    except Exception:
        pass


class Party:
    def __init__(s, leader):
        s.leader = leader; s.members = [leader]


class QE:
    def __init__(s, g, mode):
        s.g = g; s.mode = mode; s.t = time.time()


class E:
    def __init__(s, id, kind, team, x, z, hp, r=0.9):
        s.id = id; s.kind = kind; s.team = team; s.x = float(x); s.z = float(z); s.rot = 0.0
        s.hp = float(hp); s.maxhp = float(hp); s.alive = True; s.anim = 0; s.r = r
        s.atk_t = 0.0; s.target = None; s.home = (float(x), float(z)); s.lane = 0
        s.dmg = 0; s.range = 0; s.speed = 0; s.camp = None


class Hero(E):
    def __init__(s, idx, name, client):
        E.__init__(s, idx, 9, 0 if idx < 5 else 1, 0, 0, 1000, 1.0)
        s.idx = idx; s.name = name; s.client = client; s.hero = 0
        s.cd = [0.0] * 4; s.mx = 0.0; s.mz = 0.0; s.moving = False; s.hold = False; s.last_mv = 0.0
        s.dash_t = 0.0; s.dvx = 0.0; s.dvz = 0.0; s.respawn = 0.0
        s.combo = 0; s.streak = 0; s.last_kill = 0.0; s.buff = 0.0; s.retreat = False
        s.spawn()

    def spawn(s):
        s.x = -42.0 if s.idx < 5 else 42.0
        s.z = ((s.idx % 5) - 2) * 4.0
        s.rot = 90.0 if s.idx < 5 else -90.0

    def set_hero(s, h):
        s.hero = h; s.maxhp = float(HERO_HP[h]); s.hp = s.maxhp


class Match:
    def __init__(s, mode):
        s.mode = mode; s.heroes = []; s.ents = {}; s.nextid = 100; s.phase = 0
        s.pick_end = time.time() + 15; s.start = 0.0; s.next_wave = 0.0
        s.team_kills = [0, 0]; s.first_blood = False; s.over = False; s.tick_n = 0
        s.camps = [dict(x=c[0], z=c[1], kind=c[2], mon=None, respawn=0.0) for c in CAMPS]

    def bc(s, line):
        for h in s.heroes:
            if h.client:
                send(h.client, line)

    def add(s, kind, team, x, z, hp, r=0.9):
        e = E(s.nextid, kind, team, x, z, hp, r); s.nextid += 1; s.ents[e.id] = e
        return e


def all_ents(m):
    for h in m.heroes:
        if h.alive:
            yield h
    for e in m.ents.values():
        if e.alive:
            yield e


def foes_of(m, a, minions_only_real=False):
    for e in all_ents(m):
        if e.team != a.team and not (minions_only_real and e.team == 2):
            yield e


def nearest(it, x, z, rng, extra=True):
    best, bd = None, rng * rng
    for e in it:
        d = (e.x - x) ** 2 + (e.z - z) ** 2
        if d < bd:
            bd, best = d, e
    return best


def fx(m, t, x0, z0, x1, z1, r, hero, sk):
    m.bc("FX %d %.1f %.1f %.1f %.1f %.1f %d %d" % (t, x0, z0, x1, z1, r, hero, sk))


def clampxz(e):
    e.x = max(-BX, min(BX, e.x)); e.z = max(-BZ, min(BZ, e.z))


# ---------------------------------------------------------------- combat
def hurt(m, att, tgt, amt, now):
    if not tgt.alive or m.over:
        return
    if att.kind == 9 and att.buff > now:
        amt *= 1.25
    tgt.hp -= amt
    if att.kind == 9 or tgt.kind == 9:
        m.bc("D %.1f %.1f %d %d" % (tgt.x, tgt.z, int(amt), tgt.team))
    if tgt.kind in (2, 3):
        tgt.target = att
    if tgt.hp <= 0:
        die(m, att, tgt, now)


def die(m, att, v, now):
    v.alive = False; v.hp = 0
    if v.kind == 9:
        v.respawn = now + 5 + min(8, (now - m.start) / 60.0)
        vs = v.streak; v.streak = 0; v.combo = 0; v.dash_t = 0
        if att.kind == 9:
            a = att
            a.streak += 1
            a.combo = a.combo + 1 if now - a.last_kill <= 10 else 1
            a.last_kill = now
            m.team_kills[a.team] += 1
            if not m.first_blood:
                m.first_blood = True; t, tier = "FIRST BLOOD", 2
            else:
                names = {1: "SLAIN", 2: "DOUBLE KILL", 3: "TRIPLE KILL", 4: "MANIAC", 5: "SAVAGE"}
                t = names.get(a.combo, "LEGENDARY"); tier = min(a.combo, 6)
            if vs >= 3:
                t += " - SHUTDOWN"
            m.bc("K %d %d %d %s" % (a.idx, v.idx, tier, t))
        else:
            m.team_kills[1 - v.team] += 1
    elif v.kind in (2, 3):
        if v.camp is not None:
            v.camp["respawn"] = now + (90 if v.kind == 3 else 45)
        if att.kind == 9:
            att.hp = min(att.maxhp, att.hp + att.maxhp * 0.25)
            if v.kind == 3:
                att.buff = now + 45
                if att.client:
                    send(att.client, "A %d 3 BUFF ACQUIRED" % att.team)
    elif v.kind == 4:
        m.bc("A %d 4 %s TOWER DESTROYED" % (att.team, "BLUE" if att.team == 0 else "RED"))
        m.bc("FX 0 %.1f %.1f 0 0 6 0 9" % (v.x, v.z))
    elif v.kind == 5:
        end(m, 1 - v.team)


def cast(m, h, k, now):
    if not h.alive or h.cd[k] > 0 or h.dash_t > 0 or m.over:
        return False
    kind, a, b, dmg, cd = S[h.hero][k]
    foes = list(foes_of(m, h))
    if not h.moving and kind in (0, 2, 3, 4):
        t = nearest(foes, h.x, h.z, a + 2)
        if t:
            h.rot = ang(t.x - h.x, t.z - h.z)
    fx0, fz0 = math.sin(math.radians(h.rot)), math.cos(math.radians(h.rot))
    h.cd[k] = cd
    h.anim = 2 + k
    h.atk_t = 0.42 if k == 0 else 0.75
    if kind in (0, 1):
        for e in foes:
            dx, dz = e.x - h.x, e.z - h.z
            d = math.hypot(dx, dz)
            ok = d <= a + e.r + 0.3
            if kind == 0 and d > 1.3 and (dx * fx0 + dz * fz0) / d < -0.3:
                ok = False
            if ok:
                hurt(m, h, e, dmg, now)
        if kind == 0:
            fx(m, 0, h.x + fx0 * a * 0.5, h.z + fz0 * a * 0.5, 0, 0, a * 0.7, h.hero, k)
        else:
            fx(m, 0, h.x, h.z, 0, 0, a, h.hero, k)
    elif kind in (2, 4):
        for e in foes:
            dx, dz = e.x - h.x, e.z - h.z
            proj = dx * fx0 + dz * fz0
            lat = abs(dx * fz0 - dz * fx0)
            if -0.5 < proj < a + 1 and lat <= b / 2 + 0.8 + e.r * 0.5 and dmg > 0:
                hurt(m, h, e, dmg, now)
        fx(m, 2, h.x, h.z, h.x + fx0 * a, h.z + fz0 * a, b if kind == 2 else b * 0.5, h.hero, k)
        if kind == 2:
            h.dash_t = 0.22; h.dvx = fx0 * a / 0.22; h.dvz = fz0 * a / 0.22
    elif kind == 3:
        t = nearest(foes, h.x, h.z, a)
        if t:
            h.rot = ang(t.x - h.x, t.z - h.z)
            fx(m, 1, h.x, h.z, t.x, t.z, 0, h.hero, k)
            hurt(m, h, t, dmg, now)
        else:
            fx(m, 1, h.x, h.z, h.x + fx0 * a, h.z + fz0 * a, 0, h.hero, k)
    elif kind == 5:
        t = nearest(foes, h.x, h.z, a)
        tx, tz = (t.x, t.z) if t else (h.x + fx0 * a * 0.6, h.z + fz0 * a * 0.6)
        fx(m, 4, tx, tz, tx, tz, b, h.hero, k)
        for e in foes:
            if math.hypot(e.x - tx, e.z - tz) <= b + 0.6 + e.r * 0.5:
                hurt(m, h, e, dmg, now)
    else:
        h.dash_t = 0.2; h.dvx = -fx0 * a / 0.2; h.dvz = -fz0 * a / 0.2
        fx(m, 0, h.x, h.z, 0, 0, 2, h.hero, k)
    return True


# ---------------------------------------------------------------- AI + updates
def bot_ai(m, h, now):
    lane = LANES[h.idx % 3]
    enemy_x = NEXUS_X if h.team == 0 else -NEXUS_X
    own_x = -NEXUS_X if h.team == 0 else NEXUS_X
    frac = h.hp / h.maxhp
    h.hold = False; h.mx = h.mz = 0.0
    if frac < 0.3:
        h.retreat = True
    if h.retreat:
        dx, dz = own_x - h.x, 0 - h.z
        d = math.hypot(dx, dz)
        if d > 6:
            h.mx, h.mz = dx / d, dz / d
        if frac > 0.85:
            h.retreat = False
        return
    foes = list(foes_of(m, h, True))
    t = nearest(foes, h.x, h.z, 11)
    if t:
        rng = S[h.hero][0][1]
        d = dist(h, t)
        h.rot = ang(t.x - h.x, t.z - h.z)
        if d > rng * 0.85:
            h.mx, h.mz = (t.x - h.x) / d, (t.z - h.z) / d
        else:
            h.hold = True
        for k in (1, 2, 3):
            kind, a = S[h.hero][k][0], S[h.hero][k][1]
            if h.cd[k] <= 0 and kind != 6 and d <= a and random.random() < 0.05:
                cast(m, h, k, now)
    else:
        dx = enemy_x - h.x
        dz = (lane - h.z) * 0.6
        d = math.hypot(dx, dz) or 1.0
        h.mx, h.mz = dx / d, dz / d


def hero_update(m, h, now, dt):
    if not h.alive:
        if now >= h.respawn:
            h.alive = True; h.hp = h.maxhp; h.spawn(); h.anim = 0
            h.cd = [0.0] * 4
            m.bc("R %d" % h.idx)
        return
    for k in range(4):
        h.cd[k] = max(0.0, h.cd[k] - dt)
    h.atk_t -= dt
    if h.client is None:
        bot_ai(m, h, now)
    elif now - h.last_mv > 0.6:
        h.mx = h.mz = 0.0
    l = math.hypot(h.mx, h.mz)
    h.moving = l > 0.12
    if h.dash_t > 0:
        h.x += h.dvx * dt; h.z += h.dvz * dt; h.dash_t -= dt
    elif h.moving:
        sp = HERO_SPD[h.hero] * (1.1 if h.buff > now else 1.0) * min(1.0, l)
        h.x += h.mx / l * sp * dt; h.z += h.mz / l * sp * dt
        h.rot = ang(h.mx, h.mz)
    clampxz(h)
    if h.atk_t <= 0:
        h.anim = 1 if h.moving else 0
    if h.hold and h.cd[0] <= 0:
        cast(m, h, 0, now)
    if abs(h.x) > 38 and (h.x < 0) == (h.team == 0) and h.hp < h.maxhp:
        h.hp = min(h.maxhp, h.hp + h.maxhp * 0.06 * dt)


def spawn_wave(m):
    for team in (0, 1):
        for li, lz in enumerate(LANES):
            for i in range(4):
                ranged = (i == 3)
                x = (-38 if team == 0 else 38) + (i * -1.6 if team == 0 else i * 1.6)
                e = m.add(1 if ranged else 0, team, x, lz + random.uniform(-0.6, 0.6), 220 if ranged else 320, 0.7)
                e.lane = lz; e.dmg = 18 if ranged else 24; e.range = 6.0 if ranged else 1.9; e.speed = 3.2
                e.rot = 90.0 if team == 0 else -90.0


def minion_update(m, e, now, dt):
    e.atk_t -= dt
    t = nearest(foes_of(m, e, True), e.x, e.z, 7.0)
    e.anim = 0
    if t:
        d = dist(e, t)
        e.rot = ang(t.x - e.x, t.z - e.z)
        if d <= e.range + t.r:
            e.anim = 2
            if e.atk_t <= 0:
                e.atk_t = 1.1
                if e.kind == 1:
                    fx(m, 1, e.x, e.z, t.x, t.z, 0, 0, 8)
                hurt(m, e, t, e.dmg, now)
        else:
            e.x += (t.x - e.x) / d * e.speed * dt; e.z += (t.z - e.z) / d * e.speed * dt; e.anim = 1
    else:
        dx = 1.0 if e.team == 0 else -1.0
        dz = max(-1.0, min(1.0, (e.lane - e.z) * 0.5))
        l = math.hypot(dx, dz)
        e.x += dx / l * e.speed * dt; e.z += dz / l * e.speed * dt
        e.rot = ang(dx, dz); e.anim = 1
    clampxz(e)


def tower_update(m, e, now, dt):
    if e.kind != 4:
        return
    e.atk_t -= dt
    if e.atk_t > 0:
        return
    foes = list(foes_of(m, e, True))
    mins = [f for f in foes if f.kind in (0, 1)]
    t = nearest(mins, e.x, e.z, 11.5) or nearest([f for f in foes if f.kind == 9], e.x, e.z, 11.5)
    if t:
        e.atk_t = 1.3
        fx(m, 1, e.x, e.z, t.x, t.z, 0, 0, 9)
        hurt(m, e, t, 110 if t.kind == 9 else 90, now)


def mon_update(m, e, now, dt):
    e.atk_t -= dt
    if e.target is not None and (not e.target.alive or math.hypot(e.target.x - e.home[0], e.target.z - e.home[1]) > 13):
        e.target = None
    if e.target is None:
        for h in m.heroes:
            if h.alive and dist(h, e) < 4.2:
                e.target = h; break
    t = e.target
    e.anim = 0
    if t is not None:
        d = dist(e, t)
        e.rot = ang(t.x - e.x, t.z - e.z)
        if d <= 2.4 + t.r * 0.5:
            e.anim = 2
            if e.atk_t <= 0:
                e.atk_t = 1.2
                hurt(m, e, t, e.dmg, now)
        else:
            e.x += (t.x - e.x) / d * e.speed * dt; e.z += (t.z - e.z) / d * e.speed * dt; e.anim = 1
    else:
        dx, dz = e.home[0] - e.x, e.home[1] - e.z
        d = math.hypot(dx, dz)
        if d > 0.4:
            e.x += dx / d * e.speed * dt; e.z += dz / d * e.speed * dt; e.anim = 1; e.rot = ang(dx, dz)
        e.hp = min(e.maxhp, e.hp + e.maxhp * 0.2 * dt)


def start_match(m, now):
    m.phase = 1; m.start = now; m.next_wave = now + 6
    for t in (0, 1):
        sx = -1 if t == 0 else 1
        for lz in LANES:
            for i, tx in enumerate(TOWER_X):
                e = m.add(4, t, sx * tx, lz, 2200, 1.4); e.range = 11
        n = m.add(5, t, sx * NEXUS_X, 0, 4500, 3.0)
    m.bc("START " + ",".join(str(h.hero) for h in m.heroes))


def tick(m, now, dt):
    m.tick_n += 1
    if m.phase == 0:
        if now >= m.pick_end:
            start_match(m, now)
    else:
        if now >= m.next_wave:
            spawn_wave(m); m.next_wave += 25
        for c in m.camps:
            if (c["mon"] is None or not c["mon"].alive) and now >= c["respawn"] and (c["mon"] is None or c["mon"].id not in m.ents or True):
                if c["mon"] is not None and c["mon"].alive:
                    continue
                if c["mon"] is None or now >= c["respawn"]:
                    big = c["kind"] == 3
                    e = m.add(3 if big else 2, 2, c["x"], c["z"], 1800 if big else 700, 1.4 if big else 0.9)
                    e.dmg = 55 if big else 30; e.speed = 4.0; e.camp = c; c["mon"] = e
        for h in m.heroes:
            hero_update(m, h, now, dt)
        for e in list(m.ents.values()):
            if not e.alive or m.over:
                continue
            if e.kind in (0, 1):
                minion_update(m, e, now, dt)
            elif e.kind in (2, 3):
                mon_update(m, e, now, dt)
            elif e.kind == 4:
                tower_update(m, e, now, dt)
        for i in [i for i, e in m.ents.items() if not e.alive and e.kind in (0, 1, 2, 3)]:
            del m.ents[i]
        if not m.over and now - m.start > 1200:
            hp = [0, 0]
            for e in m.ents.values():
                if e.kind == 5:
                    hp[e.team] = e.hp
            end(m, 0 if hp[0] >= hp[1] else 1)
            return
    if m.over:
        return
    parts = ["S"]
    for h in m.heroes:
        parts.append("%.1f,%.1f,%.1f,%d,%d,%d" % (h.x, h.z, h.rot, h.anim if h.alive else 6, max(0, int(h.hp)), 1 if h.alive else 0))
    m.bc(" ".join(parts))
    if m.phase == 1 and m.tick_n % 2 == 0:
        ep = ["E"]
        for e in m.ents.values():
            if e.alive:
                ep.append("%d,%d,%d,%.1f,%.1f,%.0f,%d,%d,%d" % (e.id, e.kind, e.team, e.x, e.z, e.rot, e.hp, e.maxhp, e.anim))
        m.bc(" ".join(ep))
    for h in m.heroes:
        if h.client:
            send(h.client, "C %.1f %.1f %.1f %.1f" % tuple(h.cd))


def end(m, win):
    if m.over:
        return
    m.over = True
    if m in matches:
        matches.remove(m)
    for h in m.heroes:
        c = h.client
        if not c:
            continue
        d = 0
        if m.mode == "rank":
            old = ranks.get(c.name, 0)
            nr = max(0, old + (25 if h.team == win else -15))
            d = nr - old; ranks[c.name] = nr
        send(c, "END %d %d" % (win, d))
        send(c, "RANK %d" % ranks.get(c.name, 0))
        c.m = None; c.idx = -1
    save()


# ---------------------------------------------------------------- lobby / social
def save():
    try:
        with open(DATA, "w") as f:
            json.dump({"ranks": ranks, "friends": {k: sorted(v) for k, v in friends.items()}}, f)
    except Exception:
        pass


def load():
    try:
        d = json.load(open(DATA))
        ranks.update(d.get("ranks", {}))
        for k, v in d.get("friends", {}).items():
            friends[k] = set(v)
    except Exception:
        pass


def send_friends(c):
    fl = ",".join("%s:%d" % (f, 1 if f in online else 0) for f in sorted(friends.get(c.name, [])))
    send(c, "FL " + fl)


def broadcast_party(p):
    line = "PARTY " + ",".join(x.name for x in p.members)
    for x in p.members:
        send(x, line)


def cancel_queue(e):
    if e in queue:
        queue.remove(e)
    for x in e.g:
        x.qe = None
        send(x, "CANCELED")


def leave_party(c):
    p = c.party
    if not p:
        return
    if c.qe:
        cancel_queue(c.qe)
    p.members.remove(c); c.party = None
    send(c, "PARTY " + c.name)
    if len(p.members) <= 1:
        for x in p.members:
            x.party = None; send(x, "PARTY " + x.name)
        p.members = []
    else:
        if p.leader is c:
            p.leader = p.members[0]
        broadcast_party(p)


def disconnect(c):
    if c.name and online.get(c.name) is c:
        del online[c.name]
    if c.qe:
        cancel_queue(c.qe)
    leave_party(c)
    if c.m:
        m = c.m
        h = m.heroes[c.idx]
        h.client = None
        c.m = None
        if not any(x.client for x in m.heroes):
            m.over = True
            if m in matches:
                matches.remove(m)


def matchmake(now):
    for mode in ("rank", "classic"):
        q = [e for e in queue if e.mode == mode]
        if not q:
            continue
        humans = sum(len(e.g) for e in q)
        oldest = min(e.t for e in q)
        if humans < 10 and now - oldest < 12:
            continue
        q.sort(key=lambda e: -len(e.g))
        t = [[], []]; cnt = [0, 0]
        for e in q:
            sz = len(e.g)
            if cnt[0] <= cnt[1] and cnt[0] + sz <= 5:
                t[0].append(e); cnt[0] += sz
            elif cnt[1] + sz <= 5:
                t[1].append(e); cnt[1] += sz
            elif cnt[0] + sz <= 5:
                t[0].append(e); cnt[0] += sz
        if cnt[0] + cnt[1] == 0:
            continue
        create_match(mode, t)


def create_match(mode, teams):
    m = Match(mode)
    slots = [None] * 10
    nxt = [0, 5]
    for ti in (0, 1):
        for e in teams[ti]:
            if e in queue:
                queue.remove(e)
            for c in e.g:
                slots[nxt[ti]] = c; nxt[ti] += 1; c.qe = None
    bn = 1
    for i in range(10):
        c = slots[i]
        name = c.name if c else "Bot%d" % bn
        if not c:
            bn += 1
        h = Hero(i, name, c)
        h.set_hero(random.randrange(4))
        m.heroes.append(h)
        if c:
            c.m = m; c.idx = i
    matches.append(m)
    names = ",".join(h.name for h in m.heroes)
    for h in m.heroes:
        if h.client:
            send(h.client, "MATCH %d %s %s" % (h.idx, mode, names))
    for h in m.heroes:
        if not h.client:
            m.bc("PK %d %d" % (h.idx, h.hero))


def process(c, line):
    p = line.split(" ")
    cmd = p[0]
    if cmd == "HELLO" and len(p) > 1:
        n = clean(p[1])
        old = online.get(n)
        if old and old is not c:
            send(old, "ERR logged in from another device")
            disconnect(old)
            try: old.sock.close()
            except Exception: pass
        c.name = n; online[n] = c
        if n not in ranks:
            ranks[n] = 0; save()
        send(c, "WELCOME %s %d" % (n, ranks[n]))
        send_friends(c)
        return
    if not c.name:
        return
    m = c.m
    if cmd == "MV" and m and m.phase == 1 and len(p) >= 3:
        h = m.heroes[c.idx]
        mx, mz = float(p[1]), float(p[2])
        l = math.hypot(mx, mz)
        if l > 1: mx, mz = mx / l, mz / l
        h.mx, h.mz = mx, mz; h.last_mv = time.time()
    elif cmd == "CAST" and m and m.phase == 1 and len(p) > 1:
        k = int(p[1])
        if 0 <= k <= 3:
            cast(m, m.heroes[c.idx], k, time.time())
    elif cmd == "ATK" and m and len(p) > 1:
        m.heroes[c.idx].hold = p[1] == "1"
    elif cmd == "PICK" and m and m.phase == 0 and len(p) > 1:
        h = m.heroes[c.idx]
        h.set_hero(max(0, min(3, int(p[1]))))
        m.bc("PK %d %d" % (c.idx, h.hero))
    elif cmd == "FRIEND" and len(p) > 1:
        f = clean(p[1])
        if f == c.name:
            send(c, "ERR cannot add yourself"); return
        if f not in ranks:
            send(c, "ERR player not found (must login once)"); return
        friends.setdefault(c.name, set()).add(f); friends.setdefault(f, set()).add(c.name); save()
        send_friends(c)
        if f in online: send_friends(online[f])
    elif cmd == "FL":
        send_friends(c)
    elif cmd == "INVITE" and len(p) > 1:
        t = online.get(clean(p[1]))
        if not t:
            send(c, "ERR player offline"); return
        send(t, "INV " + c.name); send(c, "ERR invite sent")
    elif cmd == "JOIN" and len(p) > 1:
        ld = online.get(clean(p[1]))
        if not ld or ld is c or c.m:
            return
        if not ld.party:
            ld.party = Party(ld)
        if len(ld.party.members) >= 5:
            send(c, "ERR party full"); return
        leave_party(c)
        ld.party.members.append(c); c.party = ld.party
        broadcast_party(ld.party)
    elif cmd == "LEAVEP":
        leave_party(c)
    elif cmd == "QUEUE" and len(p) > 1:
        if c.m or c.qe:
            return
        if c.party and c.party.leader is not c:
            send(c, "ERR only party leader can start"); return
        g = list(c.party.members) if c.party else [c]
        if any(x.m or x.qe for x in g):
            return
        e = QE(g, "rank" if p[1] == "rank" else "classic")
        for x in g:
            x.qe = e; send(x, "QUEUED")
        queue.append(e)
    elif cmd == "CANCEL":
        if c.qe: cancel_queue(c.qe)


def handle(sock):
    c = Client(sock)
    try:
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        f = sock.makefile("r", encoding="utf-8", newline="\n")
        for line in f:
            line = line.strip()
            if line:
                with L:
                    try:
                        process(c, line)
                    except Exception as ex:
                        print("process error", ex)
    except Exception:
        pass
    with L:
        disconnect(c)
    try: sock.close()
    except Exception: pass


def tick_loop():
    last = time.time()
    while True:
        time.sleep(0.05)
        now = time.time(); dt = min(0.1, now - last); last = now
        with L:
            try:
                matchmake(now)
                for m in list(matches):
                    tick(m, now, dt)
            except Exception as ex:
                import traceback; traceback.print_exc()


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else int(os.environ.get("PORT", "7777"))
    load()
    threading.Thread(target=tick_loop, daemon=True).start()
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", port)); srv.listen(50)
    print("Vinz Arena server on port", port, flush=True)
    while True:
        s, _ = srv.accept()
        threading.Thread(target=handle, args=(s,), daemon=True).start()


if __name__ == "__main__":
    main()
