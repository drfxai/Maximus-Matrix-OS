"""Exercise the actual locale gate with split Android XML resource catalogs."""
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
GATE = ROOT / '.github/scripts/check-ui-locales.py'


class UiLocaleGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        for folder in ('app/src/main/res', 'app/src/main/java/ai/drfx/maximus/matrixai/ui'):
            shutil.copytree(ROOT / folder, self.root / folder)

    def run_gate(self):
        return subprocess.run([sys.executable, str(GATE)], cwd=self.root,
                              capture_output=True, text=True)

    def edit_string(self, locale, name, replacement):
        path = self.root / f'app/src/main/res/values{locale}/conversation.xml'
        original = path.read_text()
        changed, count = re.subn(r'(<string name="' + name + r'">).*?(</string>)',
                                lambda found: found[1] + replacement + found[2], original)
        self.assertEqual(1, count)
        path.write_text(changed)

    def test_split_catalogs_pass_and_preserve_english_keyboard_selectors(self):
        result = self.run_gate()
        self.assertEqual(0, result.returncode, result.stderr)
        english = (self.root / 'app/src/main/res/values/strings.xml').read_text()
        self.assertIn('<string name="chat_composer_description">Chat composer</string>', english)
        self.assertIn('<string name="chat_input_description">Chat input</string>', english)

    def test_duplicate_name_in_another_xml_file_is_rejected(self):
        duplicate = self.root / 'app/src/main/res/values-fa/duplicate.xml'
        duplicate.write_text('<resources><string name="conv_exported">تکراری</string></resources>')
        result = self.run_gate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Duplicate/empty localized key', result.stderr)
        self.assertIn('conv_exported', result.stderr)

    def test_missing_translation_in_split_catalog_is_rejected(self):
        path = self.root / 'app/src/main/res/values-ru/conversation.xml'
        path.write_text(re.sub(r'\s*<string name="conv_detecting">.*?</string>', '', path.read_text()))
        result = self.run_gate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('ru resource catalog mismatch', result.stderr)
        self.assertIn('conv_detecting', result.stderr)

    def test_format_argument_type_mismatch_in_split_catalog_is_rejected(self):
        self.edit_string('-tr', 'conv_eleven_limit', 'Sınır: %1$s')
        result = self.run_gate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('tr format arguments mismatch: conv_eleven_limit', result.stderr)

    def test_english_fallback_in_split_catalog_is_rejected(self):
        self.edit_string('-ar', 'conv_exported', 'Response downloaded')
        result = self.run_gate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('English-only conversation label in ar: conv_exported', result.stderr)

    def test_reintroduced_brain_accessibility_literal_is_rejected(self):
        path = self.root / 'app/src/main/java/ai/drfx/maximus/matrixai/ui/MatrixGraphScreen.kt'
        source = path.read_text()
        old = 'contentDescription = appContext.getString(R.string.conv_brain_question)'
        self.assertIn(old, source)
        path.write_text(source.replace(old, 'contentDescription = "Assistant question"'))
        result = self.run_gate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Hardcoded Brain accessibility description', result.stderr)

    def test_nontranslatable_base_string_does_not_require_locale_copies(self):
        path = self.root / 'app/src/main/res/values/product.xml'
        path.write_text('<resources><string name="product_identifier" translatable="false">MAXIMUS</string></resources>')
        result = self.run_gate()
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == '__main__':
    unittest.main()
