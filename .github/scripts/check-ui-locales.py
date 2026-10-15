#!/usr/bin/env python3
"""Validate five complete Android UI resource packs and report legacy hardcoded UI labels.

Only source/UI labels are inspected: user chat content, device logs and model output
MUST NOT be machine-translated or altered by app localization.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

res = Path("app/src/main/res")
files = {
    lang: res / ("values" + ("-" + lang if lang else "")) / "strings.xml"
    for lang in ("", "fa", "ar", "tr", "ru")
}

def parse(path):
    result = {}
    for item in ET.parse(path).getroot().findall("string"):
        name = item.get("name")
        if not name or name in result:
            raise ValueError("Duplicate/empty localized key in " + str(path) + ": " + str(name))
        result[name] = "".join(item.itertext()).strip()
        if not result[name]:
            raise ValueError("Empty translation: " + str(path) + " " + name)
    return result

catalog = {language: parse(path) for language, path in files.items()}
base = set(catalog[""])
for language in ("fa", "ar", "tr", "ru"):
    current = set(catalog[language])
    missing = sorted(base - current)
    unexpected = sorted(current - base)
    if missing or unexpected:
        sys.exit(language + " resource catalog mismatch. Missing=" +
                 repr(missing[:15]) + "; unexpected=" + repr(unexpected[:15]))
    # A translated key can exist yet crash getString/String.format at runtime.
    # Compare argument positions and types; literal %% is not an argument.
    def arguments(text):
        return sorted(re.findall(r'%(\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])',
                                 text.replace('%%', '')))
    for key in base:
        if arguments(catalog[''][key]) != arguments(catalog[language][key]):
            sys.exit(language + ' format arguments mismatch: ' + key)

for key in ("speech_language_label", "speech_conversation_label",
            "speech_tts_missing", "phone_install", "phone_resume",
            "phone_install_required", "phone_review_security"):
    if key not in base:
        sys.exit("Required accessible localized label missing: " + key)
    for lang in catalog:
        if not catalog[lang][key]:
            sys.exit("Missing " + key + " in " + (lang or "en"))

roots = Path("app/src/main/java/ai/drfx/maximus/matrixai/ui")
stats = {}
for file in sorted(roots.glob("*.kt")):
    source = file.read_text(encoding="utf-8")
    # This is an inventory, not a full Kotlin syntax parser.
    labels = re.findall(r'\bText\s*\(\s*"([^"\n]{2,180})"', source)
    hardcoded = [label for label in labels if not label.startswith("https://")]
    if hardcoded:
        stats[file.name] = len(hardcoded)
print("Five Android locale catalogs validated, keys:", len(base))
print("Remaining direct Text string-literal occurrences:", sum(stats.values()))
for name, count in stats.items():
    print(f"  {name}: {count}")
print("Untranslated UI literals are still tracked for staged resource migration.")
