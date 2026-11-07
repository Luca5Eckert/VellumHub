#!/usr/bin/env python3
"""Wait for an HTTP registry endpoint without logging response bodies."""
import sys
import time
import urllib.request
import urllib.error
if len(sys.argv) != 2:
    raise SystemExit("Usage: wait_ready.py URL")
deadline = time.monotonic() + 150
while time.monotonic() < deadline:
    try:
        with urllib.request.urlopen(sys.argv[1], timeout=4) as response:
            if response.status == 200:
                print("Registry ready: PASS")
                raise SystemExit(0)
    except OSError:
        pass
    time.sleep(2)
raise SystemExit("Registry readiness timed out")
