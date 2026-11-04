"""
BibTeX and APA 7 references, built in-house (no citation libraries).

A paper dict has: title, authors[] ("First Middle Last" or "Last, First"), year,
venue, type (ARTICLE|CONFERENCE|BOOK|THESIS|PREPRINT|OTHER), doi, url, volume,
issue, pages, publisher.

APA titles are kept as entered (converting to sentence case would break
acronyms and proper nouns); the student can edit them.
"""

import re
import unicodedata
from typing import Dict, List, Optional, Tuple

BIB_TYPES = {"ARTICLE": "article", "CONFERENCE": "inproceedings", "BOOK": "book", "THESIS": "phdthesis", "PREPRINT": "misc", "OTHER": "misc"}
STOPWORDS = {"a", "an", "the", "on", "of", "and", "for", "in", "to", "with", "towards", "toward", "is", "are", "via", "from", "at", "by"}
_BIB_ESCAPES = [("\\", r"\textbackslash{}"), ("&", r"\&"), ("%", r"\%"), ("$", r"\$"), ("#", r"\#"), ("_", r"\_"), ("{", r"\{"), ("}", r"\}"), ("~", r"\textasciitilde{}"), ("^", r"\textasciicircum{}")]


def split_name(name: str) -> Tuple[str, str]:
    """'Ada King Lovelace' → ('Lovelace', 'Ada King'); 'Lovelace, Ada' → ('Lovelace', 'Ada')."""
    name = " ".join((name or "").split())
    if not name:
        return "", ""
    if "," in name:
        last, first = name.split(",", 1)
        return last.strip(), first.strip()
    parts = name.split(" ")
    # Keep lowercase particles with the surname: "Ludwig van Beethoven" → "van Beethoven"
    i = len(parts) - 1
    while i > 1 and parts[i - 1].islower():
        i -= 1
    return " ".join(parts[i:]), " ".join(parts[:i])


def initials(first: str) -> str:
    """'Ada King' → 'A. K.'; 'Jean-Paul' → 'J.-P.'"""
    out = []
    for part in first.replace(".", " ").split():
        hyphen = [p for p in part.split("-") if p]
        out.append("-".join(f"{p[0].upper()}." for p in hyphen))
    return " ".join(out)


def doi_url(doi: Optional[str]) -> Optional[str]:
    if not doi:
        return None
    doi = doi.strip()
    doi = re.sub(r"^(https?://(dx\.)?doi\.org/|doi:\s*)", "", doi, flags=re.I)
    return f"https://doi.org/{doi}" if doi.startswith("10.") else None


def bib_escape(text: str) -> str:
    for a, b in _BIB_ESCAPES:
        text = text.replace(a, b)
    return text


def _ascii(text: str) -> str:
    return unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()


def bib_key(paper: Dict, used: Optional[set] = None) -> str:
    authors = paper.get("authors") or []
    last = split_name(authors[0])[0] if authors else "anon"
    last = re.sub(r"[^a-z]", "", _ascii(last).lower()) or "anon"
    words = [re.sub(r"[^a-z0-9]", "", _ascii(w).lower()) for w in (paper.get("title") or "").split()]
    word = next((w for w in words if w and w not in STOPWORDS), "")
    key = f"{last}{paper.get('year') or 'nd'}{word}"
    if used is not None:
        base, n = key, 0
        while key in used:
            key = base + chr(ord("a") + n)
            n += 1
        used.add(key)
    return key


def bibtex(paper: Dict, key: Optional[str] = None) -> str:
    kind = BIB_TYPES.get((paper.get("type") or "OTHER").upper(), "misc")
    authors = [split_name(a) for a in paper.get("authors") or [] if a and a.strip()]
    fields: List[Tuple[str, str]] = []
    if authors:
        fields.append(("author", " and ".join(f"{bib_escape(l)}, {bib_escape(f)}" if f else bib_escape(l) for l, f in authors)))
    fields.append(("title", "{" + bib_escape(paper.get("title") or "Untitled") + "}"))
    venue = paper.get("venue")
    if venue:
        field = {"article": "journal", "inproceedings": "booktitle", "phdthesis": "school"}.get(kind, "howpublished" if kind == "misc" else "publisher")
        fields.append((field, bib_escape(venue)))
    for name in ("volume", "number", "pages", "publisher"):
        value = paper.get("issue" if name == "number" else name)
        if value and not (name == "publisher" and kind == "book" and venue):
            fields.append((name, bib_escape(str(value)).replace("-", "--") if name == "pages" else bib_escape(str(value))))
    if paper.get("year"):
        fields.append(("year", str(paper["year"])))
    url = doi_url(paper.get("doi"))
    if url:
        fields.append(("doi", url.removeprefix("https://doi.org/")))
    if paper.get("url") and not url:
        fields.append(("url", paper["url"]))
    body = ",\n".join(f"  {k} = {{{v}}}" for k, v in fields)
    return f"@{kind}{{{key or bib_key(paper)},\n{body}\n}}"


def _apa_authors(authors: List[str]) -> str:
    names = []
    for a in authors:
        last, first = split_name(a)
        if last:
            names.append(f"{last}, {initials(first)}".rstrip(", ").strip() if first else last)
    if not names:
        return ""
    if len(names) == 1:
        return names[0]
    if len(names) == 2:
        return f"{names[0]}, & {names[1]}"
    if len(names) <= 20:
        return ", ".join(names[:-1]) + f", & {names[-1]}"
    return ", ".join(names[:19]) + f", . . . {names[-1]}"


def apa(paper: Dict) -> str:
    """APA 7 reference as plain text (italics are left to the display)."""
    authors = _apa_authors([a for a in paper.get("authors") or [] if a and a.strip()])
    year = f"({paper['year']})" if paper.get("year") else "(n.d.)"
    title = (paper.get("title") or "Untitled").strip().rstrip(".")
    venue = (paper.get("venue") or "").strip()
    kind = (paper.get("type") or "OTHER").upper()
    parts = []
    if authors:
        parts.append(f"{authors}{'' if authors.endswith('.') else '.'} {year}.")
        parts.append(f"{title}.")
    else:
        parts.append(f"{title}. {year}.")
    if venue:
        detail = venue
        if kind == "ARTICLE":
            if paper.get("volume"):
                detail += f", {paper['volume']}"
                if paper.get("issue"):
                    detail += f"({paper['issue']})"
            if paper.get("pages"):
                detail += f", {str(paper['pages']).replace('--', '–').replace('-', '–')}"
        elif kind == "CONFERENCE":
            detail = f"In {venue}"
        elif kind == "THESIS":
            detail = f"[Thesis, {venue}]"
        parts.append(f"{detail}.")
    url = doi_url(paper.get("doi")) or paper.get("url")
    if url:
        parts.append(url)
    return " ".join(parts)


def sort_key(paper: Dict) -> Tuple[str, str]:
    authors = paper.get("authors") or []
    last = split_name(authors[0])[0].lower() if authors else (paper.get("title") or "").lower()
    return last, str(paper.get("year") or "")


def export(papers: List[Dict], fmt: str) -> str:
    papers = sorted(papers, key=sort_key)
    if fmt == "bibtex":
        used: set = set()
        return "\n\n".join(bibtex(p, bib_key(p, used)) for p in papers) + ("\n" if papers else "")
    if fmt == "apa":
        return "\n\n".join(apa(p) for p in papers) + ("\n" if papers else "")
    raise ValueError("format must be bibtex or apa")
