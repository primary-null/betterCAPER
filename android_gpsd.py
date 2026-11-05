import socket
import json
import threading
import time

# Bettercap gpsd emulator
# Listens on port 2947 (gpsd default) for bettercap
# Listens on port 2948 for android app GPS coordinates

current_gps = {"class": "TPV", "lat": 0.0, "lon": 0.0, "alt": 0.0, "mode": 3}

def handle_bettercap(conn):
    try:
        # Send version string required by gps.recon
        version = {"class":"VERSION", "release":"3.20", "rev":"3.20", "proto_major":3, "proto_minor":14}
        conn.sendall((json.dumps(version) + "\n").encode())
        
        while True:
            # Send current GPS
            conn.sendall((json.dumps(current_gps) + "\n").encode())
            time.sleep(1)
    except Exception as e:
        print("Bettercap connection closed:", e)
    finally:
        conn.close()

def bettercap_listener():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('127.0.0.1', 2947))
    s.listen(1)
    print("Listening for bettercap on port 2947")
    while True:
        conn, addr = s.accept()
        print("Bettercap connected from", addr)
        threading.Thread(target=handle_bettercap, args=(conn,)).start()

def android_listener():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('127.0.0.1', 2948))
    s.listen(1)
    print("Listening for Android GPS on port 2948")
    while True:
        conn, addr = s.accept()
        print("Android connected from", addr)
        try:
            while True:
                data = conn.recv(1024)
                if not data: break
                try:
                    # Expecting "lat,lon,alt"
                    parts = data.decode().strip().split(",")
                    if len(parts) >= 2:
                        current_gps["lat"] = float(parts[0])
                        current_gps["lon"] = float(parts[1])
                        if len(parts) >= 3:
                            current_gps["alt"] = float(parts[2])
                        print("Updated GPS:", current_gps)
                except Exception as e:
                    pass
        except:
            pass
        finally:
            conn.close()

if __name__ == "__main__":
    t1 = threading.Thread(target=bettercap_listener)
    t1.daemon = True
    t1.start()
    android_listener()
