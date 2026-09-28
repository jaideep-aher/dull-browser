# Blocked sites

Dull Browser keeps these sites closed internally. **There is no setting to disable site blocking, allow a site, or open it anyway.** Changing the bundled list requires editing the source and rebuilding the app.

## Full list and categories

The [complete bundled list](app/src/main/assets/blocklist.txt) is the source of truth. The iOS project on the [ios branch](https://github.com/jaideep-aher/dull-browser/tree/ios) bundles the same asset path.

Each file below contains one domain per line, sorted alphabetically. Blocking a domain also blocks its subdomains. Category files are a documentation index, not separate controls in the app.

| Category | Listed domains |
| --- | ---: |
| [Video / YouTube](docs/blocked-sites/video.txt) | 4 |
| [Social media and chat](docs/blocked-sites/social-media.txt) | 3,728 |
| [Porn / adult content](docs/blocked-sites/porn-adult.txt) | 64,272 |
| [News](docs/blocked-sites/news.txt) | 179 |
| **Total** | **68,183** |

Every domain in the bundled list appears in exactly one category. These counts describe explicit entries, not all covered subdomains.

## How categories are assigned

Categories follow the upstream social, adult, and news lists plus the project's local additions. YouTube domains are grouped under video; Discord and Telegram under social media and chat. Overlaps use this order: video, social, adult, news. Domains without a match in these source snapshots stay under **Other / unclassified** rather than receiving a guessed label. Source labels are not an independent review of each site's content.

Source snapshots used for this catalog:

- [Social media and chat source](https://raw.githubusercontent.com/StevenBlack/hosts/abe587abf7979d93b7a8267d5d3e1fbc32541163/alternates/social-only/hosts)
- [Porn / adult content source](https://raw.githubusercontent.com/StevenBlack/hosts/abe587abf7979d93b7a8267d5d3e1fbc32541163/alternates/porn-only/hosts)
- [News source](https://raw.githubusercontent.com/bdr2/uBlacklist-news-blocklist/fbc631c9536be4e293092d7da181538f8cdedca1/ublacklist-news.txt)

Local additions: [extra domains](tools/extra-domains.txt) and [news domains](tools/news-domains.txt).

The browser also uses Cloudflare family DNS filtering. Domains blocked only by that external service cannot be exhaustively listed here. The files above cover the bundled list.

## Keep this catalog current

After updating the app's blocklist, run:

```bash
python3 tools/catalog-blocklist.py
```

Requires Python 3.9+ and internet access. The script uses pinned source snapshots for reproducibility and leaves the app's blocklist unchanged. Update the source commit IDs in the script when refreshing classification sources.

Bundled list SHA-256: `3c2c665f5f6bb7c3efe25f3b19cd6fcbf7cd37dbfdbb1eace9fa972c48fb5ffe`.
