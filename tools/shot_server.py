#!/usr/bin/env python3
"""Tiny HTTP server that receives screenshots from the browser preview.

POST /  body: PNG bytes (or JSON {name, data:<base64>}) -> saves into shots/
"""
import base64
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer

OUT = "shots_raw"
os.makedirs(OUT, exist_ok=True)


class Handler(BaseHTTPRequestHandler):
    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        name = "shot"
        try:
            payload = json.loads(body)
            name = payload.get("name", "shot")
            data = base64.b64decode(payload["data"])
        except Exception:
            data = body
        path = os.path.join(OUT, name + ".png")
        with open(path, "wb") as f:
            f.write(data)
        print("saved", path, len(data), "bytes")
        self.send_response(200)
        self._cors()
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8971))
    print("listening on", port)
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
