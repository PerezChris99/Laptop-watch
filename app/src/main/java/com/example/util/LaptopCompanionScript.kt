package com.example.util

object LaptopCompanionScript {
    val PYTHON_SCRIPT = """
# ==============================================================================
#  LAPTOP GUARD COMPANION SERVER (For Windows, macOS, Linux)
#  Surveillance, Automated Motion Detection, Remote Lockdown & Voice Intercom
# ==============================================================================
# Quick Setup on your laptop:
#   1. Install Python 3 (from python.org)
#   2. Install requirements in Terminal or PowerShell:
#        pip install flask opencv-python pyttsx3 psutil
#   3. Run this script:
#        python laptop_guard.py
# ==============================================================================

import os
import sys
import time
import socket
import ctypes
import platform
import subprocess
import threading
from flask import Flask, request, jsonify, Response

app = Flask(__name__)

# Security PIN (must match the PIN on your phone app)
SECURITY_PIN = "7890"
PORT = 5000

# Global states
is_locked = False
logs_history = []
motion_armed = True
motion_sensitivity = "MEDIUM" # LOW (threshold 30), MEDIUM (threshold 15), HIGH (threshold 5)
auto_lock_on_motion = True
motion_detected = False
motion_intensity = 0
last_motion_time = 0
latest_frame = None
camera_lock = threading.Lock()

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

# Initialize Camera
try:
    import cv2
    camera = cv2.VideoCapture(0)
except Exception as e:
    camera = None
    print("[WARNING] OpenCV or Camera not initialized:", e)

def authenticate():
    auth_header = request.headers.get("X-Auth-Token") or request.args.get("pin")
    return auth_header == SECURITY_PIN

def execute_lockdown():
    global is_locked
    os_name = platform.system()
    print(f"[ACTION] Locking laptop screen on {os_name}...")
    try:
        if os_name == "Windows":
            ctypes.windll.user32.LockWorkStation()
        elif os_name == "Darwin": # macOS
            subprocess.run(["pmset", "displaysleepnow"])
        elif os_name == "Linux":
            subprocess.run(["loginctl", "lock-session"])
        is_locked = True
        logs_history.insert(0, {
            "id": str(int(time.time())),
            "timestamp": int(time.time() * 1000),
            "event_type": "Screen Locked",
            "description": "Lockdown executed successfully"
        })
        return True
    except Exception as e:
        print("[ERROR] Failed to lock screen:", e)
        return False

# Background Motion Detection Engine
def motion_detection_worker():
    global motion_detected, motion_intensity, last_motion_time, latest_frame
    if not camera or not camera.isOpened():
        print("[MOTION ENGINE] Camera unavailable; motion loop standing by.")
        return

    prev_gray = None
    print("[MOTION ENGINE] Live motion detection active.")

    while True:
        try:
            success, frame = camera.read()
            if not success:
                time.sleep(0.1)
                continue

            with camera_lock:
                latest_frame = frame.copy()

            if not motion_armed:
                motion_detected = False
                motion_intensity = 0
                time.sleep(0.2)
                continue

            # Downsample and grayscale for fast diff calculation
            small = cv2.resize(frame, (320, 240))
            gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
            gray = cv2.GaussianBlur(gray, (21, 21), 0)

            if prev_gray is None:
                prev_gray = gray
                continue

            # Compute difference between frames
            frame_delta = cv2.absdiff(prev_gray, gray)
            thresh = cv2.threshold(frame_delta, 25, 255, cv2.THRESH_BINARY)[1]
            thresh = cv2.dilate(thresh, None, iterations=2)

            # Calculate change intensity %
            changed_pixels = cv2.countNonZero(thresh)
            total_pixels = 320 * 240
            pct = int((changed_pixels / total_pixels) * 100)
            motion_intensity = min(100, pct * 4)

            # Threshold according to sensitivity
            threshold_cutoff = 15
            if motion_sensitivity == "LOW":
                threshold_cutoff = 25
            elif motion_sensitivity == "HIGH":
                threshold_cutoff = 5

            now = int(time.time() * 1000)
            if motion_intensity >= threshold_cutoff:
                if not motion_detected:
                    print(f"[ALERT] Motion detected! Intensity: {motion_intensity}%")
                    motion_detected = True
                    last_motion_time = now
                    logs_history.insert(0, {
                        "id": str(int(time.time())),
                        "timestamp": now,
                        "event_type": "Motion Detected",
                        "description": f"Webcam motion detected ({motion_intensity}% intensity)"
                    })
                    if auto_lock_on_motion and not is_locked:
                        print("[DEFENSE] Auto-locking laptop due to detected intruder motion!")
                        execute_lockdown()
            else:
                if motion_detected and (now - last_motion_time > 6000):
                    motion_detected = False

            prev_gray = gray
            time.sleep(0.08) # ~12 FPS motion scan loop
        except Exception as e:
            print("[MOTION ERROR]:", e)
            time.sleep(0.5)

threading.Thread(target=motion_detection_worker, daemon=True).start()

@app.route('/api/status', methods=['GET'])
def status():
    if not authenticate():
        return jsonify({"error": "Unauthorized"}), 401
    
    battery_level = "100%"
    try:
        import psutil
        battery = psutil.sensors_battery()
        if battery:
            battery_level = f"{int(battery.percent)}%"
    except Exception:
        pass

    now = int(time.time() * 1000)
    threat = "SECURE"
    if motion_detected:
        threat = "ALERT"
    elif (now - last_motion_time) < 180000:
        threat = "ELEVATED"

    return jsonify({
        "online": True,
        "hostname": platform.node() or "My Laptop",
        "battery": battery_level,
        "is_locked": is_locked,
        "camera_active": camera is not None and camera.isOpened() if camera else False,
        "motion_detected": motion_detected,
        "motion_intensity": motion_intensity,
        "last_motion_time": last_motion_time,
        "threat_level": threat,
        "timestamp": now
    })

@app.route('/api/camera/frame', methods=['GET'])
def camera_frame():
    if not authenticate():
        return "Unauthorized", 401
    
    with camera_lock:
        if latest_frame is not None:
            _, buffer = cv2.imencode('.jpg', latest_frame)
            return Response(buffer.tobytes(), mimetype='image/jpeg')
    return "Camera unavailable", 503

@app.route('/api/motion/config', methods=['POST'])
def motion_config():
    global motion_armed, motion_sensitivity, auto_lock_on_motion
    if not authenticate():
        return jsonify({"error": "Unauthorized"}), 401
    data = request.get_json(force=True) or {}
    if "armed" in data:
        motion_armed = bool(data["armed"])
    if "sensitivity" in data:
        motion_sensitivity = str(data["sensitivity"]).upper()
    if "auto_lock" in data:
        auto_lock_on_motion = bool(data["auto_lock"])
    return jsonify({
        "success": True,
        "motion_armed": motion_armed,
        "motion_sensitivity": motion_sensitivity,
        "auto_lock_on_motion": auto_lock_on_motion
    })

@app.route('/api/lock', methods=['POST'])
def lock_laptop():
    if not authenticate():
        return jsonify({"error": "Unauthorized"}), 401
    success = execute_lockdown()
    return jsonify({"success": success})

@app.route('/api/warning/tts', methods=['POST'])
def warning_tts():
    if not authenticate():
        return jsonify({"error": "Unauthorized"}), 401
    data = request.get_json(force=True) or {}
    msg = data.get("message", "Step away from this computer! You are being recorded!")
    print(f"[AUDIO WARNING] Speaking out loud: {msg}")

    def speak():
        try:
            import pyttsx3
            engine = pyttsx3.init()
            engine.say(msg)
            engine.runAndWait()
        except Exception:
            if platform.system() == "Darwin":
                subprocess.run(["say", msg])
            elif platform.system() == "Windows":
                ps_cmd = f"Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('{msg}')"
                subprocess.run(["powershell", "-Command", ps_cmd])
            elif platform.system() == "Linux":
                subprocess.run(["espeak", msg])

    threading.Thread(target=speak, daemon=True).start()
    logs_history.insert(0, {
        "id": str(int(time.time())),
        "timestamp": int(time.time() * 1000),
        "event_type": "Voice Warning",
        "description": f"Spoke: {msg}"
    })
    return jsonify({"success": True, "spoken": msg})

@app.route('/api/warning/audio', methods=['POST'])
def warning_audio():
    if not authenticate():
        return jsonify({"error": "Unauthorized"}), 401
    file = request.files.get('audio')
    if not file:
        return jsonify({"error": "No audio file"}), 400
    temp_path = "incoming_warning.m4a"
    file.save(temp_path)
    print(f"[AUDIO WARNING] Playing incoming voice warning from phone mic...")
    
    def play_audio():
        if platform.system() == "Darwin":
            subprocess.run(["afplay", temp_path])
        elif platform.system() == "Linux":
            subprocess.run(["aplay", temp_path])
        elif platform.system() == "Windows":
            subprocess.run(["powershell", "-c", f"(New-Object Media.SoundPlayer '{temp_path}').PlaySync()"])

    threading.Thread(target=play_audio, daemon=True).start()
    return jsonify({"success": True, "message": "Audio played through laptop speakers"})

@app.route('/api/alarm', methods=['POST'])
def alarm():
    if not authenticate():
        return jsonify({"error": "Unauthorized"}), 401
    print("[ALARM] High-decibel deterrent alarm triggered!")
    def beep():
        for _ in range(5):
            if platform.system() == "Windows":
                import winsound
                winsound.Beep(1800, 300)
                winsound.Beep(2400, 300)
            else:
                print("\a")
                time.sleep(0.3)
    threading.Thread(target=beep, daemon=True).start()
    return jsonify({"success": True, "message": "Alarm sounded"})

@app.route('/api/logs', methods=['GET'])
def get_logs():
    if not authenticate():
        return jsonify({"error": "Unauthorized"}), 401
    return jsonify(logs_history[:30])

if __name__ == '__main__':
    ip = get_local_ip()
    print("=" * 65)
    print("  LAPTOP GUARD COMPANION SERVER RUNNING")
    print(f"  Laptop IP Address:  {ip}")
    print(f"  Port:               {PORT}")
    print(f"  Security PIN:       {SECURITY_PIN}")
    print(f"  Enter {ip} and port {PORT} in your Android app to connect!")
    print("=" * 65)
    app.run(host='0.0.0.0', port=PORT, threaded=True)
""".trimIndent()
}
