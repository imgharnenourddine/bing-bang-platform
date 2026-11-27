#!/usr/bin/env python3
"""F07 smoke test. Run only against the disposable f07-smoke Compose project.

Uses Python's standard library. It creates real test participants, draws and prizes;
never point it at a shared, production or event database.
"""
import base64
import hashlib
import http.cookiejar
import json
import os
import re
import secrets
import select
import socket
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("F07_BASE_URL", "http://localhost:18088").rstrip("/")
parsed = urllib.parse.urlparse(BASE)
if os.environ.get("F07_ISOLATED") != "1" or parsed.scheme != "http" or parsed.hostname not in ("localhost", "127.0.0.1") or parsed.port != 18088:
    sys.exit("Refusing to run: use F07_ISOLATED=1 and the disposable localhost:18088 environment only.")
PROJECT = os.environ.get("F07_COMPOSE_PROJECT", "")
if not re.fullmatch(r"f07-[a-z0-9-]+", PROJECT):
    sys.exit("Set F07_COMPOSE_PROJECT to the unique f07-* disposable Compose project name.")
ADMIN_USER = os.environ.get("F07_ADMIN_USERNAME")
ADMIN_PASS = os.environ.get("F07_ADMIN_PASSWORD")
if not ADMIN_USER or not ADMIN_PASS:
    sys.exit("Set F07_ADMIN_USERNAME and F07_ADMIN_PASSWORD to the disposable environment credentials.")

class Browser:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
    def request(self, path, method="GET", body=None, csrf=False, expected=(200,)):
        headers = {}
        if csrf:
            token = self.request("/api/auth/csrf")
            headers[token["headerName"]] = token["token"]
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            with self.http.open(request, timeout=12) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as exc:
            status, raw = exc.code, exc.read()
        if status not in expected:
            raise AssertionError(f"{method} {path}: unexpected status {status}; expected {expected}")
        if path == "/healthz": return raw.decode()
        return json.loads(raw) if raw else None
    def cookie(self):
        return "; ".join(f"{c.name}={c.value}" for c in self.jar)

class Stomp:
    def __init__(self, browser, invalid_csrf=False):
        token = browser.request("/api/auth/csrf")
        if invalid_csrf: token["token"] = "invalid-token"
        self.socket = socket.create_connection((parsed.hostname, parsed.port), timeout=5)
        self.socket.settimeout(5)
        key = base64.b64encode(secrets.token_bytes(16)).decode()
        handshake = (f"GET /ws HTTP/1.1\r\nHost: {parsed.netloc}\r\nUpgrade: websocket\r\n"
                     f"Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: {key}\r\n"
                     f"Origin: {BASE}\r\nCookie: {browser.cookie()}\r\n\r\n")
        self.socket.sendall(handshake.encode())
        head = b""
        while not head.endswith(b"\r\n\r\n"):
            chunk = self.socket.recv(1)
            if not chunk or len(head) > 16384:
                raise AssertionError("WebSocket handshake failed")
            head += chunk
        if b" 101 " not in head.split(b"\r\n", 1)[0]:
            raise AssertionError("WebSocket handshake rejected")
        accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest())
        if accept.lower() not in head.lower():
            raise AssertionError("WebSocket handshake accept mismatch")
        self.send(f"CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n{token['headerName']}:{token['token']}\n\n\0")
        if not self.receive(8).startswith("CONNECTED\n"):
            raise AssertionError("STOMP CONNECT failed")
    def _read_exact(self, size):
        data = b""
        while len(data) < size:
            chunk = self.socket.recv(size - len(data))
            if not chunk:
                raise AssertionError("WebSocket closed unexpectedly")
            data += chunk
        return data
    def send(self, value):
        payload = value.encode(); mask = secrets.token_bytes(4); size = len(payload)
        header = bytes([0x81, 0x80 | (size if size < 126 else 126)])
        if size >= 126:
            header += struct.pack("!H", size)
        masked = bytes(byte ^ mask[i % 4] for i, byte in enumerate(payload))
        self.socket.sendall(header + mask + masked)
    def receive(self, timeout):
        self.socket.settimeout(timeout)
        header = self._read_exact(2); opcode = header[0] & 15; length = header[1] & 127
        if length == 126: length = struct.unpack("!H", self._read_exact(2))[0]
        elif length == 127: length = struct.unpack("!Q", self._read_exact(8))[0]
        if length > 1_000_000 or header[1] & 0x80:
            raise AssertionError("Invalid server WebSocket frame")
        payload = self._read_exact(length)
        if opcode == 8: raise AssertionError("WebSocket closed")
        if opcode != 1: return ""
        return payload.decode().strip("\x00\n")
    def subscribe(self, destination, subid):
        # Spring's simple broker does not guarantee STOMP receipt support.
        self.send(f"SUBSCRIBE\nid:{subid}\ndestination:{destination}\n\n\0")
        time.sleep(.3)
    def event(self, timeout=3):
        ready, _, _ = select.select([self.socket], [], [], timeout)
        if not ready: return None
        frame = self.receive(4)
        if frame.startswith("ERROR\n"): raise AssertionError("STOMP ERROR")
        if frame.startswith("MESSAGE\n"):
            return json.loads(frame.split("\n\n", 1)[1])
        return None
    def close(self): self.socket.close()

def require(condition, message):
    if not condition: raise AssertionError(message)

def wait_event(client, event_type, timeout=8):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        event = client.event(min(1, max(.01, deadline-time.monotonic())))
        if event and event.get("type") == event_type: return event
    raise AssertionError(f"Missing {event_type} event")

def expect_stomp_denied(client, frame):
    client.send(frame)
    try:
        response = client.receive(4)
    except socket.timeout as exc:
        raise AssertionError("Unauthorized STOMP command was not rejected in time") from exc
    except (ConnectionError, AssertionError):
        return  # Connection closed by the server.
    require(response.startswith("ERROR"), "Unauthorized STOMP command did not receive ERROR")


def main():
    anonymous = Browser()
    anonymous.request("/api/auth/me", expected=(401,))
    anonymous.request("/api/participants/me", expected=(401,))
    anonymous.request("/api/participants/register", "POST", {"firstName": "A", "lastName": "B"}, expected=(403,))
    admin = Browser()
    require(admin.request("/healthz").strip() == "ok", "Nginx health endpoint failed")
    with urllib.request.urlopen("http://localhost:18080/actuator/health", timeout=5) as response:
        require(response.status == 200 and json.load(response).get("status") == "UP", "Backend health failed")
    admin.request("/api/auth/admin/login", "POST", {"username": ADMIN_USER, "password": "incorrect-password"}, True, (401,))
    admin.request("/api/auth/admin/login", "POST", {"username": ADMIN_USER, "password": ADMIN_PASS}, True)
    admin_identity = admin.request("/api/auth/me")
    require(admin_identity.get("role") == "ADMIN", "Admin session missing")
    require(admin.request("/api/admin/participants/count")["count"] == 0, "Database is not empty; refusing to modify it")
    require(admin.request("/api/admin/prizes") == [], "Prize inventory is not empty; refusing to modify it")
    admin_socket = Stomp(admin)
    admin_socket.subscribe("/topic/admin", "admin")
    participants = []
    for i in range(3):
        browser = Browser()
        person = browser.request("/api/participants/register", "POST", {"firstName": "Smoke", "lastName": f"Participant{i}"}, True, (201,))
        participants.append((browser, person))
        recovered = browser.request("/api/participants/me")
        require(recovered["id"] == person["id"], "Participant session did not persist")
        repeated = browser.request("/api/participants/register", "POST", {"firstName": "Smoke", "lastName": f"Participant{i}"}, True, (200,))
        require(repeated["id"] == person["id"], "Repeated registration changed participant")
        browser.request("/api/admin/draw/status", expected=(403,))
        wait_event(admin_socket, "PARTICIPANT_REGISTERED")
    numbers = {person["participantNumber"] for _, person in participants}
    require(len(numbers) == 3, "Participant numbers not unique")
    require(admin.request("/api/admin/participants/count")["count"] == 3, "Expected a fresh database with exactly three participants")
    # A fresh authenticated socket must reject a bad STOMP CONNECT token.
    try:
        bad = Stomp(participants[0][0], invalid_csrf=True)
    except (AssertionError, OSError):
        pass
    else:
        bad.close()
        raise AssertionError("STOMP CONNECT accepted an invalid CSRF token")
    denied_subscription = Stomp(participants[0][0])
    try:
        expect_stomp_denied(denied_subscription, "SUBSCRIBE\nid:forbidden\ndestination:/topic/admin\n\n\0")
    finally:
        denied_subscription.close()
    denied_send = Stomp(participants[0][0])
    try:
        expect_stomp_denied(denied_send, "SEND\ndestination:/topic/draw\n\n{}\0")
    finally:
        denied_send.close()
    sockets = []
    try:
        for index, (browser, person) in enumerate(participants):
            client = Stomp(browser); client.subscribe("/user/queue/notifications", f"participant-{index}")
            sockets.append((person, client))
        for category in ("STANDARD", "PREMIUM"):
            admin.request("/api/admin/prizes", "POST", {"name": f"Smoke {category}", "category": category, "baseWeight": 1, "initialStock": 3, "enabled": True}, True, (201,))
        admin.request("/api/admin/wheel/config", "PATCH", {"geometricGrowthFactor": 1.5, "maximumPremiumMultiplier": 10}, True)
        before = admin.request("/api/admin/prizes")
        draw = admin.request("/api/admin/draw/start", "POST", csrf=True, expected=(201,))
        selected_id = draw["participant"]["id"]
        selected = next(client for person, client in sockets if person["id"] == selected_id)
        wait_event(selected, "PARTICIPANT_SELECTED")
        for person, client in sockets:
            if person["id"] != selected_id:
                event = client.event(2)
                require(not event or event.get("type") != "PARTICIPANT_SELECTED", "Private draw notification leaked")
        admin.request("/api/admin/draw/start", "POST", csrf=True, expected=(409,))
        award = admin.request("/api/admin/wheel/spin", "POST", {"drawId": draw["id"]}, True, (201,))
        wait_event(selected, "PRIZE_WON")
        for person, client in sockets:
            if person["id"] != selected_id:
                event = client.event(2)
                require(not event or event.get("type") != "PRIZE_WON", "Private prize notification leaked")
        after = admin.request("/api/admin/prizes")
        require(sum(p["remainingStock"] for p in after) == sum(p["remainingStock"] for p in before)-1, "Stock decrement incorrect")
        require(admin.request("/api/admin/draw/status")["activeDraw"] is None, "Draw not completed")
        progression = admin.request("/api/admin/wheel/config")["consecutiveStandardAwards"]
        retry = admin.request("/api/admin/wheel/spin", "POST", {"drawId": draw["id"]}, True, (200,))
        require(retry["awardId"] == award["awardId"], "Retry returned a different award")
        require(admin.request("/api/admin/prizes") == after, "Retry altered stock")
        require(admin.request("/api/admin/wheel/config")["consecutiveStandardAwards"] == progression, "Retry altered progression")
        next_draw = admin.request("/api/admin/draw/start", "POST", csrf=True, expected=(201,))
        require(next_draw["participant"]["id"] != selected_id, "Participant selected twice in cycle")
        admin.request(f"/api/admin/draw/{next_draw['id']}/cancel", "POST", csrf=True)
        reset = admin.request("/api/admin/draw/reset", "POST", {"confirmed": True, "expectedCycleNumber": draw["cycleNumber"]}, True)
        require(reset["eligibleCount"] == 3 and reset["selectedCount"] == 0, "Reset did not restore eligibility")
        require(admin.request("/api/admin/prizes") == after, "Reset changed inventory")
        require(admin.request("/api/admin/wheel/config")["consecutiveStandardAwards"] == progression, "Reset changed progression")
        require(len(admin.request("/api/admin/draw/history")["content"]) == 2, "Draw history lost")
        require(len(admin.request("/api/admin/wheel/history")["content"]) == 1, "Award history lost")
        if os.environ.get("F07_RESTART_BACKEND") == "1":
            subprocess.run(["docker", "compose", "-p", PROJECT, "restart", "backend"], check=True, timeout=60)
            for _ in range(40):
                try:
                    if admin.request("/api/auth/me").get("role") == "ADMIN": break
                except (OSError, AssertionError): time.sleep(1)
            else: raise AssertionError("Session did not recover after backend restart")
            require(admin.request("/api/admin/participants/count")["count"] == 3, "Participants lost after restart")
            for browser, person in participants:
                require(browser.request("/api/participants/me")["id"] == person["id"], "Participant session did not survive restart")
            require(len(admin.request("/api/admin/draw/history")["content"]) == 2, "Draws lost after restart")
            require(len(admin.request("/api/admin/wheel/history")["content"]) == 1, "Awards lost after restart")
            require(admin.request("/api/admin/prizes") == after, "Inventory changed after restart")
            require(admin.request("/api/admin/wheel/config")["consecutiveStandardAwards"] == progression, "Progression changed after restart")
        else:
            print("SKIPPED: backend restart (set F07_RESTART_BACKEND=1)")
        print("PASSED: isolated HTTP/STOMP draw, private notifications, spin retry and reset smoke scenario")
    finally:
        for _, client in sockets: client.close()
        admin_socket.close()

if __name__ == "__main__":
    main()
