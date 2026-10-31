#!/usr/bin/env node
// Vinz Arena - authoritative game server (Node.js, no dependencies)
// Run:  node server.js [port]     (default 7777)
const net = require('net');
const fs = require('fs');
const path = require('path');

const DATA = path.join(__dirname, 'vinzmoba-data.json');
const HERO_HP = [1300, 850, 950, 1900];
const HERO_SPD = [6.4, 5.8, 6.2, 5.4];
// S[hero][k] = [kind, a, b, dmg, cd]; kind: 0 melee,1 aoe self,2 dash,3 bolt,4 line,5 aoe target,6 back dash
const S = [
  [[0, 3.4, 0, 100, 0.7], [1, 4.6, 0, 160, 4], [2, 8, 2.2, 200, 6], [1, 6.5, 0, 320, 14]],
  [[3, 11, 0, 90, 0.8], [3, 13, 0, 190, 3], [1, 5.5, 0, 140, 6], [5, 14, 5.5, 330, 14]],
  [[3, 13, 0, 85, 0.55], [4, 15, 1.8, 170, 3.5], [6, 7, 0, 0, 5], [5, 14, 5, 260, 13]],
  [[0, 3.2, 0, 70, 0.8], [0, 4.2, 0, 130, 4], [1, 5.5, 0, 150, 7], [1, 6.5, 0, 200, 14]],
];
const LANES = [-18, 0, 18];
const TOWER_X = [28, 14];
const NEXUS_X = 43;
const BX = 49, BZ = 29;
const CAMPS = [[-22, -9, 2], [-22, 9, 2], [22, -9, 2], [22, 9, 2], [0, -24, 3], [0, 24, 3], [-8, -9, 2], [8, 9, 2]];

const online = new Map(), ranks = {}, friends = new Map();
let queue = [], matches = [];
const now = () => Date.now() / 1000;
const rnd = (a, b) => a + Math.random() * (b - a);
const clean = (s) => (s.replace(/[^A-Za-z0-9_#.\-]/g, '_').slice(0, 16) || 'Player');
const ang = (dx, dz) => Math.atan2(dx, dz) * 180 / Math.PI;
const dist = (a, b) => Math.hypot(a.x - b.x, a.z - b.z);
const f1 = (v) => v.toFixed(1);

function send(c, line) {
  try { if (c && c.sock && !c.sock.destroyed) c.sock.write(line + '\n'); } catch (e) { }
}

class E {
  constructor(id, kind, team, x, z, hp, r = 0.9) {
    this.id = id; this.kind = kind; this.team = team; this.x = x; this.z = z; this.rot = 0;
    this.hp = hp; this.maxhp = hp; this.alive = true; this.anim = 0; this.r = r;
    this.atk_t = 0; this.target = null; this.home = [x, z]; this.lane = 0;
    this.dmg = 0; this.range = 0; this.speed = 0; this.camp = null;
  }
}

class Hero extends E {
  constructor(idx, name, client) {
    super(idx, 9, idx < 5 ? 0 : 1, 0, 0, 1000, 1.0);
    this.idx = idx; this.name = name; this.client = client; this.hero = 0;
    this.cd = [0, 0, 0, 0]; this.mx = 0; this.mz = 0; this.moving = false; this.hold = false; this.last_mv = 0;
    this.dash_t = 0; this.dvx = 0; this.dvz = 0; this.respawn = 0;
    this.combo = 0; this.streak = 0; this.last_kill = 0; this.buff = 0; this.retreat = false;
    this.spawn();
  }
  spawn() {
    this.x = this.idx < 5 ? -42 : 42;
    this.z = ((this.idx % 5) - 2) * 4;
    this.rot = this.idx < 5 ? 90 : -90;
  }
  setHero(h) { this.hero = h; this.maxhp = HERO_HP[h]; this.hp = this.maxhp; }
}

class Match {
  constructor(mode) {
    this.mode = mode; this.heroes = []; this.ents = new Map(); this.nextid = 100; this.phase = 0;
    this.pick_end = now() + 15; this.start = 0; this.next_wave = 0;
    this.team_kills = [0, 0]; this.first_blood = false; this.over = false; this.tick_n = 0;
    this.camps = CAMPS.map(c => ({ x: c[0], z: c[1], kind: c[2], mon: null, respawn: 0 }));
  }
  bc(line) { for (const h of this.heroes) if (h.client) send(h.client, line); }
  add(kind, team, x, z, hp, r = 0.9) {
    const e = new E(this.nextid++, kind, team, x, z, hp, r);
    this.ents.set(e.id, e);
    return e;
  }
}

function* allEnts(m) {
  for (const h of m.heroes) if (h.alive) yield h;
  for (const e of m.ents.values()) if (e.alive) yield e;
}
function foesOf(m, a, realOnly = false) {
  const out = [];
  for (const e of allEnts(m)) if (e.team !== a.team && !(realOnly && e.team === 2)) out.push(e);
  return out;
}
function nearest(list, x, z, rng) {
  let best = null, bd = rng * rng;
  for (const e of list) {
    const d = (e.x - x) ** 2 + (e.z - z) ** 2;
    if (d < bd) { bd = d; best = e; }
  }
  return best;
}
function fx(m, t, x0, z0, x1, z1, r, hero, sk) {
  m.bc(`FX ${t} ${f1(x0)} ${f1(z0)} ${f1(x1)} ${f1(z1)} ${f1(r)} ${hero} ${sk}`);
}
function clampxz(e) {
  e.x = Math.max(-BX, Math.min(BX, e.x));
  e.z = Math.max(-BZ, Math.min(BZ, e.z));
}

// ------------------------------------------------------------ combat
function hurt(m, att, tgt, amt, t) {
  if (!tgt.alive || m.over) return;
  if (att.kind === 9 && att.buff > t) amt *= 1.25;
  tgt.hp -= amt;
  if (att.kind === 9 || tgt.kind === 9) m.bc(`D ${f1(tgt.x)} ${f1(tgt.z)} ${Math.floor(amt)} ${tgt.team}`);
  if (tgt.kind === 2 || tgt.kind === 3) tgt.target = att;
  if (tgt.hp <= 0) die(m, att, tgt, t);
}

function die(m, att, v, t) {
  v.alive = false; v.hp = 0;
  if (v.kind === 9) {
    v.respawn = t + 5 + Math.min(8, (t - m.start) / 60);
    const vs = v.streak; v.streak = 0; v.combo = 0; v.dash_t = 0;
    if (att.kind === 9) {
      const a = att;
      a.streak++;
      a.combo = (t - a.last_kill <= 10) ? a.combo + 1 : 1;
      a.last_kill = t;
      m.team_kills[a.team]++;
      let txt, tier;
      if (!m.first_blood) { m.first_blood = true; txt = 'FIRST BLOOD'; tier = 2; }
      else {
        const names = { 1: 'SLAIN', 2: 'DOUBLE KILL', 3: 'TRIPLE KILL', 4: 'MANIAC', 5: 'SAVAGE' };
        txt = names[a.combo] || 'LEGENDARY'; tier = Math.min(a.combo, 6);
      }
      if (vs >= 3) txt += ' - SHUTDOWN';
      m.bc(`K ${a.idx} ${v.idx} ${tier} ${txt}`);
    } else {
      m.team_kills[1 - v.team]++;
    }
  } else if (v.kind === 2 || v.kind === 3) {
    if (v.camp) v.camp.respawn = t + (v.kind === 3 ? 90 : 45);
    if (att.kind === 9) {
      att.hp = Math.min(att.maxhp, att.hp + att.maxhp * 0.25);
      if (v.kind === 3) {
        att.buff = t + 45;
        if (att.client) send(att.client, `A ${att.team} 3 BUFF ACQUIRED`);
      }
    }
  } else if (v.kind === 4) {
    m.bc(`A ${att.team} 4 ${att.team === 0 ? 'BLUE' : 'RED'} TOWER DESTROYED`);
    m.bc(`FX 0 ${f1(v.x)} ${f1(v.z)} 0 0 6 0 9`);
  } else if (v.kind === 5) {
    end(m, 1 - v.team);
  }
}

function cast(m, h, k, t) {
  if (!h.alive || h.cd[k] > 0 || h.dash_t > 0 || m.over) return false;
  const [kind, a, b, dmg, cd] = S[h.hero][k];
  const foes = foesOf(m, h);
  if (!h.moving && (kind === 0 || kind === 2 || kind === 3 || kind === 4)) {
    const tg = nearest(foes, h.x, h.z, a + 2);
    if (tg) h.rot = ang(tg.x - h.x, tg.z - h.z);
  }
  const fx0 = Math.sin(h.rot * Math.PI / 180), fz0 = Math.cos(h.rot * Math.PI / 180);
  h.cd[k] = cd; h.anim = 2 + k; h.atk_t = k === 0 ? 0.42 : 0.75;
  if (kind === 0 || kind === 1) {
    for (const e of foes) {
      const dx = e.x - h.x, dz = e.z - h.z, d = Math.hypot(dx, dz);
      let ok = d <= a + e.r + 0.3;
      if (kind === 0 && d > 1.3 && (dx * fx0 + dz * fz0) / d < -0.3) ok = false;
      if (ok) hurt(m, h, e, dmg, t);
    }
    if (kind === 0) fx(m, 0, h.x + fx0 * a * 0.5, h.z + fz0 * a * 0.5, 0, 0, a * 0.7, h.hero, k);
    else fx(m, 0, h.x, h.z, 0, 0, a, h.hero, k);
  } else if (kind === 2 || kind === 4) {
    for (const e of foes) {
      const dx = e.x - h.x, dz = e.z - h.z;
      const proj = dx * fx0 + dz * fz0, lat = Math.abs(dx * fz0 - dz * fx0);
      if (proj > -0.5 && proj < a + 1 && lat <= b / 2 + 0.8 + e.r * 0.5 && dmg > 0) hurt(m, h, e, dmg, t);
    }
    fx(m, 2, h.x, h.z, h.x + fx0 * a, h.z + fz0 * a, kind === 2 ? b : b * 0.5, h.hero, k);
    if (kind === 2) { h.dash_t = 0.22; h.dvx = fx0 * a / 0.22; h.dvz = fz0 * a / 0.22; }
  } else if (kind === 3) {
    const tg = nearest(foes, h.x, h.z, a);
    if (tg) {
      h.rot = ang(tg.x - h.x, tg.z - h.z);
      fx(m, 1, h.x, h.z, tg.x, tg.z, 0, h.hero, k);
      hurt(m, h, tg, dmg, t);
    } else fx(m, 1, h.x, h.z, h.x + fx0 * a, h.z + fz0 * a, 0, h.hero, k);
  } else if (kind === 5) {
    const tg = nearest(foes, h.x, h.z, a);
    const tx = tg ? tg.x : h.x + fx0 * a * 0.6, tz = tg ? tg.z : h.z + fz0 * a * 0.6;
    fx(m, 4, tx, tz, tx, tz, b, h.hero, k);
    for (const e of foes) if (Math.hypot(e.x - tx, e.z - tz) <= b + 0.6 + e.r * 0.5) hurt(m, h, e, dmg, t);
  } else {
    h.dash_t = 0.2; h.dvx = -fx0 * a / 0.2; h.dvz = -fz0 * a / 0.2;
    fx(m, 0, h.x, h.z, 0, 0, 2, h.hero, k);
  }
  return true;
}

// ------------------------------------------------------------ AI + updates
function botAi(m, h, t) {
  const lane = LANES[h.idx % 3];
  const enemyX = h.team === 0 ? NEXUS_X : -NEXUS_X, ownX = h.team === 0 ? -NEXUS_X : NEXUS_X;
  const frac = h.hp / h.maxhp;
  h.hold = false; h.mx = 0; h.mz = 0;
  if (frac < 0.3) h.retreat = true;
  if (h.retreat) {
    const dx = ownX - h.x, dz = -h.z, d = Math.hypot(dx, dz);
    if (d > 6) { h.mx = dx / d; h.mz = dz / d; }
    if (frac > 0.85) h.retreat = false;
    return;
  }
  const tg = nearest(foesOf(m, h, true), h.x, h.z, 11);
  if (tg) {
    const rng = S[h.hero][0][1], d = dist(h, tg);
    h.rot = ang(tg.x - h.x, tg.z - h.z);
    if (d > rng * 0.85) { h.mx = (tg.x - h.x) / d; h.mz = (tg.z - h.z) / d; }
    else h.hold = true;
    for (let k = 1; k <= 3; k++) {
      const kind = S[h.hero][k][0], a = S[h.hero][k][1];
      if (h.cd[k] <= 0 && kind !== 6 && d <= a && Math.random() < 0.05) cast(m, h, k, t);
    }
  } else {
    const dx = enemyX - h.x, dz = (lane - h.z) * 0.6, d = Math.hypot(dx, dz) || 1;
    h.mx = dx / d; h.mz = dz / d;
  }
}

function heroUpdate(m, h, t, dt) {
  if (!h.alive) {
    if (t >= h.respawn) {
      h.alive = true; h.hp = h.maxhp; h.spawn(); h.anim = 0; h.cd = [0, 0, 0, 0];
      m.bc(`R ${h.idx}`);
    }
    return;
  }
  for (let k = 0; k < 4; k++) h.cd[k] = Math.max(0, h.cd[k] - dt);
  h.atk_t -= dt;
  if (!h.client) botAi(m, h, t);
  else if (t - h.last_mv > 0.6) { h.mx = 0; h.mz = 0; }
  const l = Math.hypot(h.mx, h.mz);
  h.moving = l > 0.12;
  if (h.dash_t > 0) { h.x += h.dvx * dt; h.z += h.dvz * dt; h.dash_t -= dt; }
  else if (h.moving) {
    const sp = HERO_SPD[h.hero] * (h.buff > t ? 1.1 : 1) * Math.min(1, l);
    h.x += h.mx / l * sp * dt; h.z += h.mz / l * sp * dt;
    h.rot = ang(h.mx, h.mz);
  }
  clampxz(h);
  if (h.atk_t <= 0) h.anim = h.moving ? 1 : 0;
  if (h.hold && h.cd[0] <= 0) cast(m, h, 0, t);
  if (Math.abs(h.x) > 38 && (h.x < 0) === (h.team === 0) && h.hp < h.maxhp) h.hp = Math.min(h.maxhp, h.hp + h.maxhp * 0.06 * dt);
}

function spawnWave(m) {
  for (const team of [0, 1]) {
    for (const lz of LANES) {
      for (let i = 0; i < 4; i++) {
        const ranged = i === 3;
        const x = (team === 0 ? -38 : 38) + (team === 0 ? -i * 1.6 : i * 1.6);
        const e = m.add(ranged ? 1 : 0, team, x, lz + rnd(-0.6, 0.6), ranged ? 220 : 320, 0.7);
        e.lane = lz; e.dmg = ranged ? 18 : 24; e.range = ranged ? 6 : 1.9; e.speed = 3.2; e.rot = team === 0 ? 90 : -90;
      }
    }
  }
}

function minionUpdate(m, e, t, dt) {
  e.atk_t -= dt;
  const tg = nearest(foesOf(m, e, true), e.x, e.z, 7);
  e.anim = 0;
  if (tg) {
    const d = dist(e, tg);
    e.rot = ang(tg.x - e.x, tg.z - e.z);
    if (d <= e.range + tg.r) {
      e.anim = 2;
      if (e.atk_t <= 0) {
        e.atk_t = 1.1;
        if (e.kind === 1) fx(m, 1, e.x, e.z, tg.x, tg.z, 0, 0, 8);
        hurt(m, e, tg, e.dmg, t);
      }
    } else { e.x += (tg.x - e.x) / d * e.speed * dt; e.z += (tg.z - e.z) / d * e.speed * dt; e.anim = 1; }
  } else {
    const dx = e.team === 0 ? 1 : -1, dz = Math.max(-1, Math.min(1, (e.lane - e.z) * 0.5)), l = Math.hypot(dx, dz);
    e.x += dx / l * e.speed * dt; e.z += dz / l * e.speed * dt; e.rot = ang(dx, dz); e.anim = 1;
  }
  clampxz(e);
}

function towerUpdate(m, e, t, dt) {
  if (e.kind !== 4) return;
  e.atk_t -= dt;
  if (e.atk_t > 0) return;
  const foes = foesOf(m, e, true);
  const tg = nearest(foes.filter(f => f.kind === 0 || f.kind === 1), e.x, e.z, 11.5) ||
    nearest(foes.filter(f => f.kind === 9), e.x, e.z, 11.5);
  if (tg) {
    e.atk_t = 1.3;
    fx(m, 1, e.x, e.z, tg.x, tg.z, 0, 0, 9);
    hurt(m, e, tg, tg.kind === 9 ? 110 : 90, t);
  }
}

function monUpdate(m, e, t, dt) {
  e.atk_t -= dt;
  if (e.target && (!e.target.alive || Math.hypot(e.target.x - e.home[0], e.target.z - e.home[1]) > 13)) e.target = null;
  if (!e.target) for (const h of m.heroes) if (h.alive && dist(h, e) < 4.2) { e.target = h; break; }
  const tg = e.target;
  e.anim = 0;
  if (tg) {
    const d = dist(e, tg);
    e.rot = ang(tg.x - e.x, tg.z - e.z);
    if (d <= 2.4 + tg.r * 0.5) {
      e.anim = 2;
      if (e.atk_t <= 0) { e.atk_t = 1.2; hurt(m, e, tg, e.dmg, t); }
    } else { e.x += (tg.x - e.x) / d * e.speed * dt; e.z += (tg.z - e.z) / d * e.speed * dt; e.anim = 1; }
  } else {
    const dx = e.home[0] - e.x, dz = e.home[1] - e.z, d = Math.hypot(dx, dz);
    if (d > 0.4) { e.x += dx / d * e.speed * dt; e.z += dz / d * e.speed * dt; e.anim = 1; e.rot = ang(dx, dz); }
    e.hp = Math.min(e.maxhp, e.hp + e.maxhp * 0.2 * dt);
  }
}

function startMatch(m, t) {
  m.phase = 1; m.start = t; m.next_wave = t + 6;
  for (const team of [0, 1]) {
    const sx = team === 0 ? -1 : 1;
    for (const lz of LANES) for (const tx of TOWER_X) { const e = m.add(4, team, sx * tx, lz, 2200, 1.4); e.range = 11; }
    m.add(5, team, sx * NEXUS_X, 0, 4500, 3.0);
  }
  m.bc('START ' + m.heroes.map(h => h.hero).join(','));
}

function tick(m, t, dt) {
  m.tick_n++;
  if (m.phase === 0) {
    if (t >= m.pick_end) startMatch(m, t);
  } else {
    if (t >= m.next_wave) { spawnWave(m); m.next_wave += 25; }
    for (const c of m.camps) {
      if ((!c.mon || !c.mon.alive) && t >= c.respawn) {
        const big = c.kind === 3;
        const e = m.add(big ? 3 : 2, 2, c.x, c.z, big ? 1800 : 700, big ? 1.4 : 0.9);
        e.dmg = big ? 55 : 30; e.speed = 4; e.camp = c; c.mon = e;
      }
    }
    for (const h of m.heroes) heroUpdate(m, h, t, dt);
    for (const e of [...m.ents.values()]) {
      if (!e.alive || m.over) continue;
      if (e.kind <= 1) minionUpdate(m, e, t, dt);
      else if (e.kind <= 3) monUpdate(m, e, t, dt);
      else if (e.kind === 4) towerUpdate(m, e, t, dt);
    }
    for (const [id, e] of [...m.ents]) if (!e.alive && e.kind <= 3) m.ents.delete(id);
    if (!m.over && t - m.start > 1200) {
      const hp = [0, 0];
      for (const e of m.ents.values()) if (e.kind === 5) hp[e.team] = e.hp;
      end(m, hp[0] >= hp[1] ? 0 : 1);
      return;
    }
  }
  if (m.over) return;
  const parts = ['S'];
  for (const h of m.heroes) parts.push(`${f1(h.x)},${f1(h.z)},${f1(h.rot)},${h.alive ? h.anim : 6},${Math.max(0, Math.floor(h.hp))},${h.alive ? 1 : 0}`);
  m.bc(parts.join(' '));
  if (m.phase === 1 && m.tick_n % 2 === 0) {
    const ep = ['E'];
    for (const e of m.ents.values()) if (e.alive) ep.push(`${e.id},${e.kind},${e.team},${f1(e.x)},${f1(e.z)},${Math.round(e.rot)},${Math.floor(e.hp)},${Math.floor(e.maxhp)},${e.anim}`);
    m.bc(ep.join(' '));
  }
  for (const h of m.heroes) if (h.client) send(h.client, `C ${h.cd.map(f1).join(' ')}`);
}

function end(m, win) {
  if (m.over) return;
  m.over = true;
  matches = matches.filter(x => x !== m);
  for (const h of m.heroes) {
    const c = h.client;
    if (!c) continue;
    let d = 0;
    if (m.mode === 'rank') {
      const old = ranks[c.name] || 0, nr = Math.max(0, old + (h.team === win ? 25 : -15));
      d = nr - old; ranks[c.name] = nr;
    }
    send(c, `END ${win} ${d}`);
    send(c, `RANK ${ranks[c.name] || 0}`);
    c.m = null; c.idx = -1;
  }
  save();
}

// ------------------------------------------------------------ lobby / social
function save() {
  try {
    const fr = {};
    for (const [k, v] of friends) fr[k] = [...v];
    fs.writeFileSync(DATA, JSON.stringify({ ranks, friends: fr }));
  } catch (e) { }
}
function load() {
  try {
    const d = JSON.parse(fs.readFileSync(DATA, 'utf8'));
    Object.assign(ranks, d.ranks || {});
    for (const [k, v] of Object.entries(d.friends || {})) friends.set(k, new Set(v));
  } catch (e) { }
}
function sendFriends(c) {
  const fl = [...(friends.get(c.name) || [])].sort().map(f => `${f}:${online.has(f) ? 1 : 0}`).join(',');
  send(c, 'FL ' + fl);
}
function broadcastParty(p) {
  const line = 'PARTY ' + p.members.map(x => x.name).join(',');
  for (const x of p.members) send(x, line);
}
function cancelQueue(e) {
  queue = queue.filter(x => x !== e);
  for (const x of e.g) { x.qe = null; send(x, 'CANCELED'); }
}
function leaveParty(c) {
  const p = c.party;
  if (!p) return;
  if (c.qe) cancelQueue(c.qe);
  p.members = p.members.filter(x => x !== c);
  c.party = null;
  send(c, 'PARTY ' + c.name);
  if (p.members.length <= 1) {
    for (const x of p.members) { x.party = null; send(x, 'PARTY ' + x.name); }
    p.members = [];
  } else {
    if (p.leader === c) p.leader = p.members[0];
    broadcastParty(p);
  }
}
function disconnect(c) {
  if (c.name && online.get(c.name) === c) online.delete(c.name);
  if (c.qe) cancelQueue(c.qe);
  leaveParty(c);
  if (c.m) {
    const m = c.m;
    m.heroes[c.idx].client = null;
    c.m = null;
    if (!m.heroes.some(h => h.client)) { m.over = true; matches = matches.filter(x => x !== m); }
  }
}

function matchmake(t) {
  for (const mode of ['rank', 'classic']) {
    const q = queue.filter(e => e.mode === mode);
    if (!q.length) continue;
    const humans = q.reduce((s, e) => s + e.g.length, 0);
    const oldest = Math.min(...q.map(e => e.t));
    if (humans < 10 && t - oldest < 12) continue;
    q.sort((a, b) => b.g.length - a.g.length);
    const tm = [[], []], cnt = [0, 0];
    for (const e of q) {
      const sz = e.g.length;
      if (cnt[0] <= cnt[1] && cnt[0] + sz <= 5) { tm[0].push(e); cnt[0] += sz; }
      else if (cnt[1] + sz <= 5) { tm[1].push(e); cnt[1] += sz; }
      else if (cnt[0] + sz <= 5) { tm[0].push(e); cnt[0] += sz; }
    }
    if (cnt[0] + cnt[1] === 0) continue;
    createMatch(mode, tm);
  }
}

function createMatch(mode, teams) {
  const m = new Match(mode);
  const slots = new Array(10).fill(null), nxt = [0, 5];
  for (const ti of [0, 1]) for (const e of teams[ti]) {
    queue = queue.filter(x => x !== e);
    for (const c of e.g) { slots[nxt[ti]++] = c; c.qe = null; }
  }
  let bn = 1;
  for (let i = 0; i < 10; i++) {
    const c = slots[i];
    const h = new Hero(i, c ? c.name : 'Bot' + (bn++), c);
    h.setHero(Math.floor(Math.random() * 4));
    m.heroes.push(h);
    if (c) { c.m = m; c.idx = i; }
  }
  matches.push(m);
  const names = m.heroes.map(h => h.name).join(',');
  for (const h of m.heroes) if (h.client) send(h.client, `MATCH ${h.idx} ${mode} ${names}`);
  for (const h of m.heroes) if (!h.client) m.bc(`PK ${h.idx} ${h.hero}`);
}

function processLine(c, line) {
  const p = line.split(' '), cmd = p[0];
  if (cmd === 'HELLO' && p.length > 1) {
    const n = clean(p[1]);
    const old = online.get(n);
    if (old && old !== c) { send(old, 'ERR logged in from another device'); disconnect(old); try { old.sock.destroy(); } catch (e) { } }
    c.name = n; online.set(n, c);
    if (!(n in ranks)) { ranks[n] = 0; save(); }
    send(c, `WELCOME ${n} ${ranks[n]}`);
    sendFriends(c);
    return;
  }
  if (!c.name) return;
  const m = c.m;
  if (cmd === 'MV' && m && m.phase === 1 && p.length >= 3) {
    const h = m.heroes[c.idx];
    let mx = parseFloat(p[1]), mz = parseFloat(p[2]);
    if (isNaN(mx) || isNaN(mz)) return;
    const l = Math.hypot(mx, mz);
    if (l > 1) { mx /= l; mz /= l; }
    h.mx = mx; h.mz = mz; h.last_mv = now();
  } else if (cmd === 'CAST' && m && m.phase === 1 && p.length > 1) {
    const k = parseInt(p[1]);
    if (k >= 0 && k <= 3) cast(m, m.heroes[c.idx], k, now());
  } else if (cmd === 'ATK' && m && p.length > 1) {
    m.heroes[c.idx].hold = p[1] === '1';
  } else if (cmd === 'PICK' && m && m.phase === 0 && p.length > 1) {
    const h = m.heroes[c.idx];
    h.setHero(Math.max(0, Math.min(3, parseInt(p[1]) || 0)));
    m.bc(`PK ${c.idx} ${h.hero}`);
  } else if (cmd === 'FRIEND' && p.length > 1) {
    const f = clean(p[1]);
    if (f === c.name) return send(c, 'ERR cannot add yourself');
    if (!(f in ranks)) return send(c, 'ERR player not found (must login once)');
    if (!friends.has(c.name)) friends.set(c.name, new Set());
    if (!friends.has(f)) friends.set(f, new Set());
    friends.get(c.name).add(f); friends.get(f).add(c.name); save();
    sendFriends(c);
    if (online.has(f)) sendFriends(online.get(f));
  } else if (cmd === 'FL') {
    sendFriends(c);
  } else if (cmd === 'INVITE' && p.length > 1) {
    const t = online.get(clean(p[1]));
    if (!t) return send(c, 'ERR player offline');
    send(t, 'INV ' + c.name); send(c, 'ERR invite sent');
  } else if (cmd === 'JOIN' && p.length > 1) {
    const ld = online.get(clean(p[1]));
    if (!ld || ld === c || c.m) return;
    if (!ld.party) ld.party = { leader: ld, members: [ld] };
    if (ld.party.members.length >= 5) return send(c, 'ERR party full');
    leaveParty(c);
    ld.party.members.push(c); c.party = ld.party;
    broadcastParty(ld.party);
  } else if (cmd === 'LEAVEP') {
    leaveParty(c);
  } else if (cmd === 'QUEUE' && p.length > 1) {
    if (c.m || c.qe) return;
    if (c.party && c.party.leader !== c) return send(c, 'ERR only party leader can start');
    const g = c.party ? [...c.party.members] : [c];
    if (g.some(x => x.m || x.qe)) return;
    const e = { g, mode: p[1] === 'rank' ? 'rank' : 'classic', t: now() };
    for (const x of g) { x.qe = e; send(x, 'QUEUED'); }
    queue.push(e);
  } else if (cmd === 'CANCEL') {
    if (c.qe) cancelQueue(c.qe);
  }
}

// ------------------------------------------------------------ network
const port = parseInt(process.argv[2] || process.env.PORT || '7777');
load();
const server = net.createServer((sock) => {
  sock.setNoDelay(true);
  const c = { sock, name: null, m: null, idx: -1, party: null, qe: null };
  let buf = '';
  sock.setEncoding('utf8');
  sock.on('data', (d) => {
    buf += d;
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      if (line) { try { processLine(c, line); } catch (e) { console.log('process error', e.message); } }
    }
    if (buf.length > 4096) sock.destroy();
  });
  const gone = () => { try { disconnect(c); } catch (e) { } };
  sock.on('close', gone);
  sock.on('error', () => { try { sock.destroy(); } catch (e) { } });
});
let last = now();
setInterval(() => {
  const t = now(), dt = Math.min(0.1, t - last);
  last = t;
  try {
    matchmake(t);
    for (const m of [...matches]) tick(m, t, dt);
  } catch (e) { console.log('tick error', e.stack); }
}, 50);
server.listen(port, '0.0.0.0', () => console.log('Vinz Arena server (node) on port ' + port));
