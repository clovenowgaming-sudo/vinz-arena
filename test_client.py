import socket, time, os, collections, math, threading
D = os.path.dirname(os.path.abspath(__file__))
out = open(os.path.join(D, "test.log"), "w")
def log(*a):
    out.write(" ".join(str(x) for x in a) + "\n"); out.flush()
s = socket.create_connection(("127.0.0.1", 7777))
f = s.makefile("r")
def tx(l): s.sendall((l + "\n").encode())
tx("HELLO Tester")
tx("QUEUE classic")
cnt = collections.Counter(); last = {}
start = time.time(); my = -1; started = False
def mover():
    while time.time() - start < 70:
        if started:
            tx("MV 1 0"); tx("ATK 1")
            for k in (1, 2, 3): tx("CAST %d" % k)
        time.sleep(0.2)
threading.Thread(target=mover, daemon=True).start()
s.settimeout(80)
try:
    for line in f:
        p = line.split()
        cnt[p[0]] += 1
        last[p[0]] = line.strip()[:300]
        if p[0] == "MATCH": my = int(p[1]); log("MATCH", line.strip())
        if p[0] == "START": started = True; log("START", line.strip())
        if p[0] in ("K", "A", "END"): log(line.strip())
        if p[0] == "E" and cnt["E"] % 40 == 1: log("E", len(p) - 1, "ents", p[1:4])
        if time.time() - start > 70: break
except Exception as e:
    log("ERR", e)
log("COUNTS", dict(cnt))
for k, v in last.items(): log("LAST", k, v)
log("DONE")
