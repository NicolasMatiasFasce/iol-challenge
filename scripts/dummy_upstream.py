#!/usr/bin/env python3
"""Dummy upstream server for local development.

It returns HTTP 200 for common methods so the rate limiter can forward
requests without generating noisy upstream connection errors.
"""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "127.0.0.1"
PORT = 8081


class DummyUpstreamHandler(BaseHTTPRequestHandler):
    server_version = "DummyUpstream/1.0"

    def _send_ok(self) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        body = '{{"status":"ok","source":"dummy-upstream","method":"{}","path":"{}"}}'.format(
            self.command,
            self.path,
        )
        self.wfile.write(body.encode("utf-8"))

    def do_GET(self) -> None:
        self._send_ok()

    def do_POST(self) -> None:
        self._send_ok()

    def do_PUT(self) -> None:
        self._send_ok()

    def do_DELETE(self) -> None:
        self._send_ok()

    def do_PATCH(self) -> None:
        self._send_ok()

    def do_OPTIONS(self) -> None:
        self._send_ok()

    def do_HEAD(self) -> None:
        self.send_response(200)
        self.end_headers()

    def log_message(self, _format: str, *_args: object) -> None:
        # Keep script logs concise during local runs.
        return


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), DummyUpstreamHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()


