from __future__ import annotations

from io import BytesIO

import pytest
from pypdf import PdfWriter
from pypdf.generic import DecodedStreamObject, DictionaryObject, NameObject

from oep_ai_tutor.parsing import Chunk, Passage, UnsupportedSource, chunk_passages, parse_source


def pdf_bytes(text: str | None) -> bytes:
    writer = PdfWriter()
    page = writer.add_blank_page(width=612, height=792)
    if text is not None:
        font = DictionaryObject({
            NameObject("/Type"): NameObject("/Font"),
            NameObject("/Subtype"): NameObject("/Type1"),
            NameObject("/BaseFont"): NameObject("/Helvetica"),
        })
        font_ref = writer._add_object(font)
        page[NameObject("/Resources")] = DictionaryObject({
            NameObject("/Font"): DictionaryObject({NameObject("/F1"): font_ref}),
        })
        stream = DecodedStreamObject()
        stream.set_data(f"BT /F1 12 Tf 72 720 Td ({text}) Tj ET".encode("ascii"))
        page[NameObject("/Contents")] = writer._add_object(stream)
    output = BytesIO()
    writer.write(output)
    return output.getvalue()


def test_inline_text_and_markdown_keep_paragraph_and_heading_provenance() -> None:
    text = parse_source("TEXT", "  Xin chào\n  học viên.\n\nBài hai.  ")
    assert [passage.text for passage in text] == ["Xin chào học viên.", "Bài hai."]

    markdown = parse_source(
        "FILE",
        b"# Chapter\nIntro\n## Subsection\nDetail\n```\n# code heading\n```\n",
        file_name="lesson.MD", mime_type="text/markdown; charset=utf-8",
    )
    assert [part.heading for part in markdown] == ["Chapter", "Chapter > Subsection"]
    assert "# code heading" in markdown[1].text
    assert markdown[1].text.startswith("## Subsection")


def test_json_flattens_scalar_leaves_with_stable_escaped_paths() -> None:
    passages = parse_source(
        "FILE", '{"unit/a":{"title":"Học AI","flags":[true,null,3]},"x~y":0}'.encode(),
        file_name="lesson.json", mime_type="application/json",
    )
    assert [(part.json_path, part.text) for part in passages] == [
        ("$/unit~1a/title", '$/unit~1a/title: "Học AI"'),
        ("$/unit~1a/flags/0", "$/unit~1a/flags/0: true"),
        ("$/unit~1a/flags/1", "$/unit~1a/flags/1: null"),
        ("$/unit~1a/flags/2", "$/unit~1a/flags/2: 3"),
        ("$/x~0y", "$/x~0y: 0"),
    ]


def test_text_pdf_preserves_page_and_empty_pdf_requires_ocr() -> None:
    extracted = parse_source(
        "PDF", pdf_bytes("Inheritance reuses parent methods"),
        file_name="lesson.pdf", mime_type="application/pdf",
    )
    assert len(extracted) == 1
    assert extracted[0].page_number == 1
    assert "Inheritance reuses parent methods" in extracted[0].text

    with pytest.raises(UnsupportedSource, match="OCR_REQUIRED"):
        parse_source("PDF", pdf_bytes(None), file_name="scan.pdf", mime_type="application/pdf")

    encrypted = PdfWriter()
    encrypted.add_blank_page(width=612, height=792)
    encrypted.encrypt("secret")
    output = BytesIO()
    encrypted.write(output)
    with pytest.raises(UnsupportedSource, match="ENCRYPTED_PDF"):
        parse_source("PDF", output.getvalue(), file_name="locked.pdf", mime_type="application/pdf")


@pytest.mark.parametrize("content_type,payload,file_name,mime_type,code", [
    ("TEXT", "  ", None, None, "EMPTY_TEXT"),
    ("FILE", b"{}", "empty.json", "application/json", "EMPTY_JSON"),
    ("FILE", b"{bad}", "broken.json", "application/json", "INVALID_JSON"),
    ("FILE", b"\xff", "broken.md", "text/markdown", "INVALID_UTF8"),
    ("FILE", b"hello", "lesson.md", "application/json", "UNSUPPORTED_FORMAT"),
    ("FILE", b"hello", "lesson.docx", "application/octet-stream", "UNSUPPORTED_FORMAT"),
    ("FILE", ("# " + "x" * 256 + "\nbody").encode(), "long.md", "text/markdown", "HEADING_TOO_LONG"),
    ("FILE", ('{"' + "x" * 501 + '":1}').encode(), "long.json", "application/json", "JSON_PATH_TOO_LONG"),
    ("PDF", b"not a PDF", "lesson.pdf", "application/pdf", "INVALID_PDF"),
])
def test_unsupported_or_bad_source_cannot_be_parsed(
    content_type: str, payload: str | bytes, file_name: str | None,
    mime_type: str | None, code: str,
) -> None:
    with pytest.raises(UnsupportedSource) as error:
        parse_source(content_type, payload, file_name=file_name, mime_type=mime_type)
    assert error.value.code == code


def test_chunk_windows_are_deterministic_and_never_mix_citation_locations() -> None:
    passages = [
        Passage("Alpha " * 30, page_number=1),
        Passage("Beta " * 30, page_number=2),
    ]
    chunks = chunk_passages(passages, max_chars=60, overlap_chars=10)
    assert chunks == chunk_passages(passages, max_chars=60, overlap_chars=10)
    assert [chunk.index for chunk in chunks] == list(range(len(chunks)))
    assert all(len(chunk.text) <= 60 for chunk in chunks)
    assert all("Beta" not in chunk.text for chunk in chunks if chunk.page_number == 1)
    assert all("Alpha" not in chunk.text for chunk in chunks if chunk.page_number == 2)
    assert len(chunks) > 2
