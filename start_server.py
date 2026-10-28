import os, subprocess, sys, time, signal
D = os.path.dirname(os.path.abspath(__file__))
os.chdir(D)
# stop old server
subprocess.run("pkill -f 'python.*VinzMOBA/server.py'; pkill -f 'python server.py'", shell=True)
time.sleep(0.5)
port = sys.argv[1] if len(sys.argv) > 1 else "7777"
log = open(os.path.join(D, "server.log"), "w")
subprocess.Popen([sys.executable, os.path.join(D, "server.py"), port], stdout=log, stderr=log,
                 start_new_session=True, cwd=D)
time.sleep(1.0)
print(open(os.path.join(D, "server.log")).read())
