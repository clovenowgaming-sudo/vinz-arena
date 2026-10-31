import os, subprocess, sys, time
D = os.path.dirname(os.path.abspath(__file__))
os.chdir(D)
subprocess.run("pkill -f 'server.py'; pkill -f 'node.*server.js'", shell=True)
time.sleep(0.7)
log = open(os.path.join(D, "server.log"), "w")
subprocess.Popen(["node", os.path.join(D, "server.js"), "7777"], stdout=log, stderr=log, start_new_session=True, cwd=D)
time.sleep(1.2)
print(open(os.path.join(D, "server.log")).read())
