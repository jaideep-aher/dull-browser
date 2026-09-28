#!/usr/bin/env python3
"""Builds the compiled blocklist shipped in the APK.

Merges the StevenBlack porn + social host lists with tools/extra-domains.txt
and writes one bare, lowercase domain per line to the app assets. The browser
matches a host and every parent domain against this set, so listing
"youtube.com" is enough to cover its subdomains.
"""
import pathlib
import re
import sys
import urllib.request

# Hosts-format sources (0.0.0.0 domain.com).
HOSTS_SOURCES = [
    "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-social-only/hosts",
]

# uBlacklist-format sources (*://*.domain.com/*).
UBLACKLIST_SOURCES = [
    "https://raw.githubusercontent.com/bdr2/uBlacklist-news-blocklist/main/ublacklist-news.txt",
]

ROOT = pathlib.Path(__file__).resolve().parent.parent
EXTRA = ROOT / "tools" / "extra-domains.txt"
NEWS = ROOT / "tools" / "news-domains.txt"
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "blocklist.txt"

# Hosts files point blocked names at a null address; anything else is a comment
# or a malformed line we skip.
HOSTS_LINE = re.compile(r"^(?:0\.0\.0\.0|127\.0\.0\.1)\s+(\S+)")
VALID_DOMAIN = re.compile(r"^[a-z0-9]([a-z0-9\-._]*[a-z0-9])?$")
UBLACKLIST_LINE = re.compile(r"^\*://(?:\*\.)?([a-z0-9][a-z0-9\-._]*)/")

# Never block these, even if a source list contains them: losing them breaks
# sign-in, maps or the browser's own search.
NEVER_BLOCK = {
    "localhost",
    "localhost.localdomain",
    "local",
    "broadcasthost",
    "ip6-localhost",
    "ip6-loopback",
    "ip6-localnet",
    "ip6-mcastprefix",
    "ip6-allnodes",
    "ip6-allrouters",
    "ip6-allhosts",
    "0.0.0.0",
}


def parse_hosts(text):
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        match = HOSTS_LINE.match(line)
        if match:
            yield match.group(1).lower().rstrip(".")


def parse_ublacklist(text):
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip().lower()
        if not line:
            continue
        match = UBLACKLIST_LINE.match(line)
        if match:
            yield match.group(1).rstrip(".")


def parse_plain(text):
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip().lower().rstrip(".")
        if line:
            yield line


def main():
    domains = set()

    for url, parser in [(u, parse_hosts) for u in HOSTS_SOURCES] + \
                       [(u, parse_ublacklist) for u in UBLACKLIST_SOURCES]:
        print(f"fetching {url}")
        with urllib.request.urlopen(url, timeout=120) as response:
            body = response.read().decode("utf-8", "replace")
        before = len(domains)
        domains.update(parser(body))
        print(f"  +{len(domains) - before} domains")

    for path in (EXTRA, NEWS):
        before = len(domains)
        domains.update(parse_plain(path.read_text()))
        print(f"{path.name}: +{len(domains) - before} domains")

    # "www." prefixes are redundant: the matcher already walks parent domains.
    domains = {d[4:] if d.startswith("www.") else d for d in domains}
    domains = {d for d in domains if VALID_DOMAIN.match(d) and d not in NEVER_BLOCK}

    if len(domains) < 10_000:
        sys.exit(f"refusing to write a suspiciously small list ({len(domains)} domains)")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(sorted(domains)) + "\n")
    size_mb = OUTPUT.stat().st_size / 1024 / 1024
    print(f"wrote {len(domains)} domains to {OUTPUT} ({size_mb:.2f} MB)")


if __name__ == "__main__":
    main()
