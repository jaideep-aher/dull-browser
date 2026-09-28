#!/usr/bin/env python3
"""Document every bundled blocked domain by source category; never change app behavior."""
import hashlib
import importlib.util
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('build_blocklist', ROOT / 'tools/build-blocklist.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)
STEVENBLACK = 'abe587abf7979d93b7a8267d5d3e1fbc32541163'
NEWS = 'fbc631c9536be4e293092d7da181538f8cdedca1'


def normalize(domains):
    return {d.removeprefix('www.') for d in domains}


def fetch(url, parser):
    with urllib.request.urlopen(url, timeout=60) as response:
        return normalize(parser(response.read().decode('utf-8')))


def main():
    asset = ROOT / 'app/src/main/assets/blocklist.txt'
    bundled = set(asset.read_text().splitlines())
    sources = {
        'social-media': f'https://raw.githubusercontent.com/StevenBlack/hosts/{STEVENBLACK}/alternates/social-only/hosts',
        'porn-adult': f'https://raw.githubusercontent.com/StevenBlack/hosts/{STEVENBLACK}/alternates/porn-only/hosts',
        'news': f'https://raw.githubusercontent.com/bdr2/uBlacklist-news-blocklist/{NEWS}/ublacklist-news.txt',
    }
    groups = {
        'video': {'youtube.com', 'youtu.be', 'youtube-nocookie.com', 'yt.be'},
        'social-media': fetch(sources['social-media'], builder.parse_hosts) | {
            'discord.com', 'discordapp.com', 'telegram.org', 'web.telegram.org'},
        'porn-adult': fetch(sources['porn-adult'], builder.parse_hosts),
        'news': fetch(sources['news'], builder.parse_ublacklist) | normalize(
            builder.parse_plain((ROOT / 'tools/news-domains.txt').read_text())),
    }
    # Source categories can overlap. Use this explicit order for a disjoint catalog.
    remaining = bundled.copy()
    for key in groups:
        groups[key] &= remaining
        remaining -= groups[key]
    if remaining:
        groups['other-unclassified'] = remaining
    labels = {
        'video': 'Video / YouTube',
        'social-media': 'Social media and chat',
        'porn-adult': 'Porn / adult content',
        'news': 'News',
        'other-unclassified': 'Other / unclassified',
    }
    out = ROOT / 'docs/blocked-sites'
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    for key, domains in groups.items():
        (out / f'{key}.txt').write_text(''.join(f'{d}\n' for d in sorted(domains)))
        rows.append(f'| [{labels[key]}](docs/blocked-sites/{key}.txt) | {len(domains):,} |')
    assert set().union(*groups.values()) == bundled
    assert sum(map(len, groups.values())) == len(bundled)
    digest = hashlib.sha256(asset.read_bytes()).hexdigest()
    (ROOT / 'BLOCKED_SITES.md').write_text('''# Blocked sites

Dull Browser keeps these sites closed internally. **There is no setting to disable site blocking, allow a site, or open it anyway.** Changing the bundled list requires editing the source and rebuilding the app.

## Full list and categories

The [complete bundled list](app/src/main/assets/blocklist.txt) is the source of truth. The iOS project on the [ios branch](https://github.com/jaideep-aher/dull-browser/tree/ios) bundles the same asset path.

Each file below contains one domain per line, sorted alphabetically. Blocking a domain also blocks its subdomains. Category files are a documentation index, not separate controls in the app.

| Category | Listed domains |
| --- | ---: |
''' + '\n'.join(rows) + f'''
| **Total** | **{len(bundled):,}** |

Every domain in the bundled list appears in exactly one category. These counts describe explicit entries, not all covered subdomains.

## How categories are assigned

Categories follow the upstream social, adult, and news lists plus the project's local additions. YouTube domains are grouped under video; Discord and Telegram under social media and chat. Overlaps use this order: video, social, adult, news. Domains without a match in these source snapshots stay under **Other / unclassified** rather than receiving a guessed label. Source labels are not an independent review of each site's content.

Source snapshots used for this catalog:

''' + '\n'.join(f'- [{labels[key]} source]({url})' for key, url in sources.items()) + f'''

Local additions: [extra domains](tools/extra-domains.txt) and [news domains](tools/news-domains.txt).

The browser also uses Cloudflare family DNS filtering. Domains blocked only by that external service cannot be exhaustively listed here. The files above cover the bundled list.

## Keep this catalog current

After updating the app's blocklist, run:

```bash
python3 tools/catalog-blocklist.py
```

Requires Python 3.9+ and internet access. The script uses pinned source snapshots for reproducibility and leaves the app's blocklist unchanged. Update the source commit IDs in the script when refreshing classification sources.

Bundled list SHA-256: `{digest}`.
''')
    for key, domains in groups.items():
        print(f'{key}: {len(domains):,}')


if __name__ == '__main__':
    main()
