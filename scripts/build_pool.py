"""Build the pool of daily artworks: app/src/main/assets/pool.json.

Day N after the pool's start date shows entry N (wrapping round at the end), so the order is
the schedule. Rebuilding keeps every existing entry where it is and appends new artworks at
the end, so a day that has already been shown never changes.

The pool holds:
  * the Art Institute of Chicago's best-known public-domain works that have a description
    (its "boosted" set), and
  * the Cleveland Museum of Art's CC0 highlights,
each with the English Wikipedia article on the artwork and on its artist, where Wikidata
links one. The app reads the full record from the museum on the day.

Usage (standard library only):
    py -3 scripts/build_pool.py
"""

from __future__ import annotations

import datetime as dt
import json
import random
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

POOL = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "pool.json"
START = "2026-10-16"  # the day entry 0 is shown; never change it once released
SEED = 2026  # shuffles the new artworks the same way on every run
ARTIST_GAP_DAYS = 14  # the same artist at most once in this many days, where the pool allows

AGENT = "DailyGallery-pool-builder/1.0 (https://github.com/Mishallic)"
AIC_SEARCH = "https://api.artic.edu/api/v1/artworks/search"
AIC_IIIF = "https://www.artic.edu/iiif/2/{}/full/400,/0/default.jpg"
CMA_SEARCH = "https://openaccess-api.clevelandart.org/api/artworks/"
WIKIDATA_API = "https://www.wikidata.org/w/api.php"
WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"


def fetch_json(url: str, *, params: dict | None = None, body: dict | None = None) -> dict:
    if params:
        url += "?" + urllib.parse.urlencode(params)
    headers = {"User-Agent": AGENT, "AIC-User-Agent": AGENT, "Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, data=data, headers=headers)
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except OSError as error:  # URLError, HTTPError and timeouts
            if attempt == 3:
                raise
            print(f"  retrying after {error}", file=sys.stderr)
            time.sleep(2 * (attempt + 1))
    raise AssertionError("unreachable")


def aic_candidates() -> list[dict]:
    query = {
        "query": {
            "bool": {
                "must": [
                    {"term": {"is_public_domain": True}},
                    {"term": {"is_boosted": True}},
                    {"exists": {"field": "description"}},
                    {"exists": {"field": "image_id"}},
                ]
            }
        },
        "fields": ["id", "title", "artist_title", "artist_display", "date_display", "image_id"],
    }
    works, page = [], 1
    while True:
        result = fetch_json(AIC_SEARCH, params={"page": page, "limit": 100}, body=query)
        works += result["data"]
        if page >= result["pagination"]["total_pages"]:
            break
        page += 1
    return [
        {
            "source": "aic",
            "id": str(work["id"]),
            "title": work["title"],
            # artist_title is empty for unattributed works; the display line names the culture.
            "artist": work.get("artist_title") or work["artist_display"].split("\n")[0],
            "date": work["date_display"],
            "thumbnail": AIC_IIIF.format(work["image_id"]),
        }
        for work in works
    ]


def cma_candidates() -> list[dict]:
    result = fetch_json(CMA_SEARCH, params={"cc0": "", "has_image": 1, "highlight": 1, "limit": 1000})
    entries = []
    for work in result["data"]:
        creators = [c for c in work.get("creators") or [] if c.get("role") == "artist"] or work.get("creators") or []
        if creators:
            artist = creators[0]["description"].split(" (")[0]
        else:
            artist = (work.get("culture") or ["Unknown maker"])[0]
        entries.append(
            {
                "source": "cma",
                "id": str(work["id"]),
                "title": work["title"],
                "artist": artist,
                "date": work.get("creation_date") or "",
                "thumbnail": work["images"]["web"]["url"],
                "_wikidata": [url.rsplit("/", 1)[1] for url in (work.get("external_resources") or {}).get("wikidata", [])],
            }
        )
    return entries


def aic_wikidata_items() -> dict[str, str]:
    """Art Institute of Chicago artwork id -> Wikidata item, through "ARTIC artwork ID" (P4610)."""
    sparql = "SELECT ?item ?aic WHERE { ?item wdt:P4610 ?aic . }"
    rows = fetch_json(WIKIDATA_SPARQL, params={"query": sparql, "format": "json"})["results"]["bindings"]
    return {row["aic"]["value"]: row["item"]["value"].rsplit("/", 1)[1] for row in rows}


def wikidata_entities(ids: list[str]) -> dict[str, dict]:
    """English Wikipedia titles and creators (P170) for Wikidata items, 50 to a request."""
    entities: dict[str, dict] = {}
    unique = sorted(set(ids))
    for i in range(0, len(unique), 50):
        batch = unique[i : i + 50]
        result = fetch_json(
            WIKIDATA_API,
            params={
                "action": "wbgetentities",
                "ids": "|".join(batch),
                "props": "sitelinks|claims",
                "sitefilter": "enwiki",
                "format": "json",
            },
        )
        for qid, entity in result.get("entities", {}).items():
            creators = [
                claim["mainsnak"]["datavalue"]["value"]["id"]
                for claim in entity.get("claims", {}).get("P170", [])
                if claim["mainsnak"].get("datavalue")
            ]
            title = entity.get("sitelinks", {}).get("enwiki", {}).get("title")
            entities[qid] = {"wikipedia": title, "creators": creators}
    return entities


def add_wikipedia(entries: list[dict]) -> None:
    aic_items = aic_wikidata_items()
    for entry in entries:
        if entry["source"] == "aic":
            entry["_wikidata"] = [aic_items[entry["id"]]] if entry["id"] in aic_items else []
    artworks = wikidata_entities([qid for entry in entries for qid in entry["_wikidata"]])
    creators = wikidata_entities([c for item in artworks.values() for c in item["creators"]])
    for entry in entries:
        items = [artworks[qid] for qid in entry.pop("_wikidata") if qid in artworks]
        entry["wikipedia"] = next((item["wikipedia"] for item in items if item["wikipedia"]), None)
        artist_titles = [creators[c]["wikipedia"] for item in items for c in item["creators"] if c in creators]
        entry["artistWikipedia"] = next((title for title in artist_titles if title), None)


def schedule(entries: list[dict], already: list[dict]) -> list[dict]:
    """Order new artworks: alternate the museums, and keep an artist ARTIST_GAP_DAYS apart where possible."""
    rng = random.Random(SEED)
    by_source = {source: [e for e in entries if e["source"] == source] for source in ("aic", "cma")}
    for works in by_source.values():
        rng.shuffle(works)
    ordered, recent = [], [e["artist"] for e in already[-ARTIST_GAP_DAYS:]]
    turn = 0
    while any(by_source.values()):
        source = ("aic", "cma")[turn % 2] if all(by_source.values()) else next(s for s, w in by_source.items() if w)
        works = by_source[source]
        pick = next((i for i, work in enumerate(works) if work["artist"] not in recent), 0)
        work = works.pop(pick)
        ordered.append(work)
        recent = (recent + [work["artist"]])[-ARTIST_GAP_DAYS:]
        turn += 1
    return ordered


def main() -> None:
    existing = json.loads(POOL.read_text(encoding="utf-8"))["entries"] if POOL.exists() else []
    known = {(e["source"], e["id"]) for e in existing}

    print("Reading the Art Institute of Chicago...")
    candidates = aic_candidates()
    print("Reading the Cleveland Museum of Art...")
    candidates += cma_candidates()
    new = [entry for entry in candidates if (entry["source"], entry["id"]) not in known]
    print(f"Linking {len(new)} new artworks to Wikipedia...")
    add_wikipedia(new)

    entries = existing + schedule(new, existing)
    POOL.parent.mkdir(parents=True, exist_ok=True)
    lines = ",\n".join("    " + json.dumps(entry, ensure_ascii=False) for entry in entries)
    POOL.write_text(
        f'{{\n  "start": "{START}",\n  "built": "{dt.date.today().isoformat()}",\n  "entries": [\n{lines}\n  ]\n}}\n',
        encoding="utf-8",
    )
    linked = sum(1 for e in entries if e["wikipedia"])
    print(f"{len(entries)} artworks ({len(new)} new), {linked} with a Wikipedia article -> {POOL}")


if __name__ == "__main__":
    main()
