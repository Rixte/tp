"""Check diagram pairs, relative includes, and documentation links without dependencies.

Run from any directory with Python 3: python docs/diagrams/verify.py.
Pass --html PATH after exporting to check actual rendered in-document links as well.
PlantUML syntax and visual inspection remain separate checks.
"""

from collections import Counter
import argparse
from html.parser import HTMLParser
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
DIAGRAMS = DOCS / "diagrams"
IMAGES = DOCS / "images"


def heading_anchors(text):
    """Collect GitHub-style heading anchors, including duplicate and explicit IDs."""
    anchors = set(re.findall(r'<a\s+(?:id|name)=["\']([^"\']+)["\']', text))
    counts = Counter()
    text = re.sub(r"^```.*?^```[^\n]*", "", text, flags=re.MULTILINE | re.DOTALL)
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*\s*$", text, re.MULTILINE):
        heading = re.sub(r"\[([^]]+)\]\([^)]+\)", r"\1", heading)
        slug = re.sub(r"[^\w\- ]", "", heading.lower()).replace(" ", "-")
        suffix = "" if counts[slug] == 0 else f"-{counts[slug]}"
        anchors.add(slug + suffix)
        counts[slug] += 1
    return anchors


def check_diagrams(errors):
    """Check every PlantUML source and recursively referenced local style file."""
    sources = sorted(DIAGRAMS.rglob("*.puml"))
    expected_images = set()
    checked_includes = set()

    def check_includes(path):
        if path in checked_includes:
            return
        checked_includes.add(path)
        for include in re.findall(r'^!include(?:_once)?\s+"?([^"\s]+)',
                                  path.read_text(encoding="utf-8"), re.MULTILINE):
            target = (path.parent / include).resolve()
            if not target.is_file():
                errors.append(f"{path.relative_to(ROOT)}: missing include {include}")
            else:
                check_includes(target)

    for source in sources:
        check_includes(source)
        image = IMAGES / source.relative_to(DIAGRAMS).with_suffix(".svg")
        expected_images.add(image)
        if not image.is_file():
            errors.append(f"{source.relative_to(ROOT)}: missing matching SVG")
            continue
        try:
            svg = ET.parse(image).getroot()
            if svg.tag != "{http://www.w3.org/2000/svg}svg":
                errors.append(f"{image.relative_to(ROOT)}: not an SVG document")
            if any("Syntax Error" in (element.text or "") for element in svg.iter()):
                errors.append(f"{image.relative_to(ROOT)}: contains a PlantUML error")
        except ET.ParseError as error:
            errors.append(f"{image.relative_to(ROOT)}: {error}")

    # Architecture is maintained directly as SVG, rather than generated from PlantUML.
    for image in sorted(IMAGES.rglob("*.svg")):
        if image not in expected_images and image.relative_to(IMAGES).parts[0] != "architecture":
            errors.append(f"{image.relative_to(ROOT)}: no matching PlantUML source")
    return len(sources), len(checked_includes) - len(sources)


def check_links(errors):
    """Check local Markdown/image links and anchors throughout the guides and READMEs."""
    documents = [DOCS / "DeveloperGuide.md", DOCS / "UserGuide.md", DOCS / "AboutUs.md"]
    documents += sorted(DIAGRAMS.rglob("*.md"))
    documents += sorted((DOCS / "team").glob("*.md"))
    count = 0
    for document in documents:
        text = document.read_text(encoding="utf-8")
        text = re.sub(r"^```.*?^```[^\n]*", "", text, flags=re.MULTILINE | re.DOTALL)
        html = ExportedHtml()
        html.feed(text)
        html.close()
        for link in re.findall(r"\]\(([^)\s]+)\)", text) + html.links:
            url = urlsplit(link)
            if url.scheme or url.netloc:
                continue
            count += 1
            target = (document.parent / unquote(url.path)).resolve() if url.path else document
            if not target.exists():
                errors.append(f"{document.relative_to(ROOT)}: missing link {link}")
            elif url.fragment and target.suffix.lower() == ".md":
                if unquote(url.fragment) not in heading_anchors(target.read_text(encoding="utf-8")):
                    errors.append(f"{document.relative_to(ROOT)}: missing anchor {link}")
    return count


class ExportedHtml(HTMLParser):
    """Collect actual HTML targets and links instead of guessing rendered IDs from Markdown."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids = Counter()
        self.links = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if values.get("id"):
            self.ids[values["id"]] += 1
        if tag == "a":
            if values.get("name") and values.get("name") != values.get("id"):
                self.ids[values["name"]] += 1
            if "href" in values:
                self.links.append(values["href"])


def check_exported_html(path, errors):
    """Verify fragment links within an exported document, including same-file relative links."""
    document = Path(path).resolve()
    parser = ExportedHtml()
    try:
        parser.feed(document.read_text(encoding="utf-8"))
        parser.close()
    except (OSError, UnicodeError) as error:
        errors.append(f"{document}: could not read exported HTML: {error}")
        return 0
    for anchor, count in parser.ids.items():
        if count > 1:
            errors.append(f"{document}: duplicate exported HTML anchor #{anchor}")
    checked = 0
    for link in parser.links:
        try:
            url = urlsplit(link)
        except ValueError as error:
            errors.append(f"{document}: invalid exported HTML link {link!r}: {error}")
            continue
        if url.scheme or url.netloc or not url.fragment:
            continue
        target = (document.parent / unquote(url.path)).resolve() if url.path else document
        if target != document:
            continue
        checked += 1
        if unquote(url.fragment) not in parser.ids:
            errors.append(f"{document}: missing exported HTML anchor #{unquote(url.fragment)}")
    return checked


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plantuml-jar", type=Path,
                        help="Also compare SVG embedded sources using this local renderer and Java 25.")
    parser.add_argument("--html", type=Path, action="append", default=[], metavar="PATH",
                        help="Check actual in-document links in this exported HTML file; may be repeated.")
    options = parser.parse_args()
    if options.plantuml_jar is not None and not options.plantuml_jar.is_file():
        parser.error("--plantuml-jar must name an existing local JAR")
    errors = []
    sources, styles = check_diagrams(errors)
    links = check_links(errors)
    html_links = sum(check_exported_html(path, errors) for path in options.html)
    metadata_count = 0
    missing_metadata = 0
    if options.plantuml_jar is not None and not errors:
        images = [IMAGES / source.relative_to(DIAGRAMS).with_suffix(".svg")
                  for source in sorted(DIAGRAMS.rglob("*.puml"))]
        try:
            result = subprocess.run(
                ["java", "-jar", str(options.plantuml_jar.resolve()), "-metadata"]
                + [str(image) for image in images],
                check=True, capture_output=True, text=True, encoding="utf-8", timeout=120,
            )
            metadata = re.findall(r"^-{24}\n([^\n]+)\n\n(.*?)(?=^-{24}$)",
                                  result.stdout, re.MULTILINE | re.DOTALL)
            if len(metadata) != sources:
                errors.append("Could not extract exactly one embedded source per SVG")
            for image_name, embedded in metadata:
                source = DIAGRAMS / Path(image_name).relative_to(IMAGES).with_suffix(".puml")
                if not embedded.strip():
                    missing_metadata += 1
                    continue
                # PlantUML excludes author comments outside the diagram and can omit blank lines.
                # Do not mistake those formatting differences for changed messages or relationships.
                def diagram_lines(text):
                    match = re.search(r"@startuml.*?@enduml", text, re.DOTALL)
                    return [line.strip() for line in match.group().splitlines() if line.strip()] if match else None

                current = source.read_text(encoding="utf-8")
                if diagram_lines(embedded) != diagram_lines(current):
                    errors.append(f"{source.relative_to(ROOT)}: SVG embedded source differs")
                metadata_count += 1
        except (OSError, subprocess.SubprocessError, ValueError) as error:
            errors.append(f"SVG metadata check failed: {error}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Verified {sources} source/SVG pairs, {styles} included styles, and {links} local links/anchors.")
    if options.html:
        print(f"Verified {html_links} in-document links in {len(options.html)} exported HTML files.")
    else:
        print("Rendered HTML not checked; use --html PATH after export to verify actual navigation targets.")
    if options.plantuml_jar is not None:
        print(f"All {metadata_count} available SVG embedded sources match their PlantUML sources.")
        if missing_metadata:
            print(f"{missing_metadata} SVGs contain no embedded source; their content requires manual review.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
