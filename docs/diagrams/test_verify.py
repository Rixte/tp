"""Regression tests for the rendered HTML link check, including real Pandoc heading parsing."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import verify


class ExportedHtmlTests(unittest.TestCase):
    """Check rendered targets rather than assuming that Markdown-looking text became a heading."""

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.html = Path(self.directory.name) / "guide.html"

    def check_html(self, text):
        """Return the check's count and diagnostics for one isolated HTML document."""
        self.html.write_text(text, encoding="utf-8")
        errors = []
        count = verify.check_exported_html(self.html, errors)
        return count, errors

    def test_missing_rendered_heading_rejects_broken_contents_link(self):
        count, errors = self.check_html('<a href="#appendix-a-product-scope">Appendix A</a>'
                                       '<p>Previous paragraph. ## Appendix A: Product Scope</p>')
        self.assertEqual(1, count)
        self.assertTrue(any("missing exported HTML anchor #appendix-a-product-scope" in e for e in errors))

    def test_valid_targets_support_unicode_entities_and_same_file_links(self):
        count, errors = self.check_html('<h2 id="caf&eacute;">Caf&eacute;</h2><a name="legacy"></a>'
                                       '<a href="#caf%C3%A9">Unicode</a>'
                                       '<a href="./guide.html#legacy">Same file</a>'
                                       '<a href="#">Top</a><a href="https://example.com/#outside">External</a>')
        self.assertEqual(2, count)
        self.assertEqual([], errors)

    def test_duplicate_ids_are_reported(self):
        _, errors = self.check_html('<h2 id="duplicate">First</h2><h2 id="duplicate">Second</h2>')
        self.assertTrue(any("duplicate exported HTML anchor #duplicate" in e for e in errors))

    def test_same_element_id_and_legacy_name_are_one_target(self):
        count, errors = self.check_html('<a id="target" name="target"></a><a href="#target">Target</a>')
        self.assertEqual(1, count)
        self.assertEqual([], errors)

    def test_unreadable_export_is_reported(self):
        errors = []
        self.assertEqual(0, verify.check_exported_html(self.html, errors))
        self.assertTrue(any("could not read exported HTML" in e for e in errors))

    def test_malformed_url_is_reported_without_raising(self):
        _, errors = self.check_html('<a href="https://[invalid">Broken URL</a>')
        self.assertTrue(any("invalid exported HTML link" in e for e in errors))

    def test_source_html_links_check_local_files_alongside_markdown_links(self):
        root = Path(self.directory.name)
        docs = root / "docs"
        docs.mkdir()
        for name in ("DeveloperGuide.md", "UserGuide.md"):
            (docs / name).write_text("", encoding="utf-8")
        (docs / "AboutUs.md").write_text('<table><tr><td><a href="team/member.md">Portfolio</a>'
                                        '</td></tr></table>', encoding="utf-8")
        with patch.multiple(verify, ROOT=root, DOCS=docs, DIAGRAMS=docs / "diagrams"):
            errors = []
            self.assertEqual(1, verify.check_links(errors))
            self.assertTrue(any("missing link team/member.md" in error for error in errors))
            (docs / "team").mkdir()
            (docs / "team/member.md").write_text("# Portfolio\n", encoding="utf-8")
            errors = []
            self.assertEqual(1, verify.check_links(errors))
            self.assertEqual([], errors)

    @unittest.skipUnless(shutil.which("pandoc"), "Pandoc is required for the export regression")
    def test_real_pandoc_export_detects_missing_blank_line_and_accepts_fix(self):
        for blank_line in (False, True):
            with self.subTest(blank_line=blank_line):
                markdown = ("[Appendix A](#appendix-a-product-scope)\n\nPrevious paragraph.\n"
                            + ("\n" if blank_line else "") + "## Appendix A: Product Scope\n\nScope.\n")
                result = subprocess.run([shutil.which("pandoc"), "--from=markdown", "--to=html"],
                                        input=markdown, capture_output=True, text=True,
                                        encoding="utf-8", check=True, timeout=15)
                count, errors = self.check_html(result.stdout)
                self.assertEqual(1, count)
                if blank_line:
                    self.assertEqual([], errors)
                else:
                    self.assertTrue(any("missing exported HTML anchor #appendix-a-product-scope" in e
                                        for e in errors))


if __name__ == "__main__":
    unittest.main()
