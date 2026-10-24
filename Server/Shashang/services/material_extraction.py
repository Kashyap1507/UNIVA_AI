"""
Text extraction and chunking for course materials.

Extraction returns page/slide-numbered segments so chunks can cite where
they came from. Scanned PDFs and images fall back to Gemini OCR.
"""

import base64
import io
import logging
import re
from dataclasses import dataclass
from typing import Callable, List, Optional

logger = logging.getLogger(__name__)

PDF_MIME = "application/pdf"
PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
TEXT_MIMES = ("text/plain", "text/markdown")
IMAGE_MIMES = ("image/jpeg", "image/png")
SUPPORTED_MIMES = (PDF_MIME, PPTX_MIME, *TEXT_MIMES, *IMAGE_MIMES)

EXTENSION_MIMES = {
    ".pdf": PDF_MIME,
    ".pptx": PPTX_MIME,
    ".txt": "text/plain",
    ".md": "text/markdown",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
}

# Below this many characters per page on average, a PDF is treated as scanned
MIN_CHARS_PER_PDF_PAGE = 40

CHUNK_SIZE = 1000
CHUNK_OVERLAP = 200


@dataclass
class Segment:
    text: str
    page: Optional[int] = None  # page number (PDF) or slide number (PPTX), 1-based


@dataclass
class Chunk:
    index: int
    text: str
    page: Optional[int]
    page_end: Optional[int]


class ExtractionError(Exception):
    pass


def mime_for_filename(filename: str, declared: Optional[str] = None) -> Optional[str]:
    """Trust the extension over the client-declared type; None if unsupported."""
    lower = (filename or "").lower()
    for ext, mime in EXTENSION_MIMES.items():
        if lower.endswith(ext):
            return mime
    if declared in SUPPORTED_MIMES:
        return declared
    return None


def _normalise(text: str) -> str:
    text = text.replace("\x00", " ")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


# --- extractors -----------------------------------------------------------------


def extract_pdf(data: bytes) -> List[Segment]:
    import PyPDF2

    try:
        reader = PyPDF2.PdfReader(io.BytesIO(data))
    except Exception as e:
        raise ExtractionError(f"Could not read PDF: {e}")
    if reader.is_encrypted:
        try:
            reader.decrypt("")
        except Exception:
            raise ExtractionError("PDF is password protected")
    segments = []
    for number, page in enumerate(reader.pages, start=1):
        try:
            text = _normalise(page.extract_text() or "")
        except Exception:
            text = ""
        segments.append(Segment(text=text, page=number))
    return segments


def extract_pptx(data: bytes) -> List[Segment]:
    from pptx import Presentation

    try:
        deck = Presentation(io.BytesIO(data))
    except Exception as e:
        raise ExtractionError(f"Could not read PowerPoint file: {e}")
    segments = []
    for number, slide in enumerate(deck.slides, start=1):
        parts = []
        for shape in slide.shapes:
            if getattr(shape, "has_text_frame", False) and shape.text_frame.text.strip():
                parts.append(shape.text_frame.text)
            if getattr(shape, "has_table", False):
                for row in shape.table.rows:
                    parts.append(" | ".join(cell.text for cell in row.cells))
        if slide.has_notes_slide and slide.notes_slide.notes_text_frame.text.strip():
            parts.append("Speaker notes: " + slide.notes_slide.notes_text_frame.text)
        segments.append(Segment(text=_normalise("\n".join(parts)), page=number))
    return segments


def extract_text(data: bytes) -> List[Segment]:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        text = data.decode("latin-1")
    return [Segment(text=_normalise(text), page=None)]


OCR_PROMPT = (
    "Transcribe all text in this university course document exactly, including "
    "equations (as LaTeX), code and table contents. Do not summarise or add commentary. "
    "Start each page with a line of the form '=== Page N ===' (N starting at 1)."
)

PAGE_MARKER = re.compile(r"^=== Page (\d+) ===\s*$", re.MULTILINE)


def parse_ocr_pages(text: str) -> List[Segment]:
    """Split OCR output on '=== Page N ===' markers; unmarked text becomes page 1."""
    matches = list(PAGE_MARKER.finditer(text))
    if not matches:
        return [Segment(text=_normalise(text), page=1)]
    segments = []
    for i, match in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        segments.append(Segment(text=_normalise(text[match.end():end]), page=int(match.group(1))))
    return segments


def ocr_with_gemini(data: bytes, mime: str, generate: Callable[..., str]) -> List[Segment]:
    """`generate(prompt, image_data=b64, mime_type=mime)` returns the model's text."""
    raw = generate(OCR_PROMPT, image_data=base64.b64encode(data).decode("ascii"), mime_type=mime)
    return parse_ocr_pages(raw or "")


def extract_segments(data: bytes, mime: str, ocr: Optional[Callable[..., str]] = None) -> List[Segment]:
    """
    Extract text segments. `ocr` is the Gemini text generator used for images
    and scanned PDFs; without it those inputs raise ExtractionError.
    """
    if mime == PDF_MIME:
        segments = extract_pdf(data)
        total = sum(len(s.text) for s in segments)
        if segments and total / len(segments) >= MIN_CHARS_PER_PDF_PAGE:
            return segments
        if ocr is None:
            raise ExtractionError("This PDF has no text layer and OCR is unavailable")
        logger.info("PDF looks scanned; using Gemini OCR")
        return ocr_with_gemini(data, mime, ocr)
    if mime == PPTX_MIME:
        return extract_pptx(data)
    if mime in TEXT_MIMES:
        return extract_text(data)
    if mime in IMAGE_MIMES:
        if ocr is None:
            raise ExtractionError("Image OCR is unavailable")
        return ocr_with_gemini(data, mime, ocr)
    raise ExtractionError(f"Unsupported file type: {mime}")


# --- chunking ------------------------------------------------------------------------


def chunk_segments(
    segments: List[Segment],
    size: int = CHUNK_SIZE,
    overlap: int = CHUNK_OVERLAP,
) -> List[Chunk]:
    """
    Sliding-window chunks over the concatenated text, breaking on whitespace
    where possible. Each chunk records the page at its start and end.
    """
    if overlap >= size:
        raise ValueError("overlap must be smaller than size")

    pieces = [s for s in segments if s.text]
    if not pieces:
        return []

    # Concatenate, remembering where each segment starts
    text_parts, starts, pages = [], [], []
    offset = 0
    for seg in pieces:
        starts.append(offset)
        pages.append(seg.page)
        text_parts.append(seg.text)
        offset += len(seg.text) + 2
    full = "\n\n".join(text_parts)

    def page_at(pos: int) -> Optional[int]:
        page = pages[0]
        for start, p in zip(starts, pages):
            if start <= pos:
                page = p
            else:
                break
        return page

    chunks: List[Chunk] = []
    pos = 0
    while pos < len(full):
        end = min(pos + size, len(full))
        if end < len(full):
            # Prefer to break at whitespace in the last 20% of the window
            cut = full.rfind(" ", pos + int(size * 0.8), end)
            newline = full.rfind("\n", pos + int(size * 0.8), end)
            cut = max(cut, newline)
            if cut > pos:
                end = cut
        text = full[pos:end].strip()
        if text:
            chunks.append(Chunk(index=len(chunks), text=text, page=page_at(pos), page_end=page_at(max(pos, end - 1))))
        if end >= len(full):
            break
        pos = max(end - overlap, pos + 1)
    return chunks
