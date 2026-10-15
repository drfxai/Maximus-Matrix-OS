#!/usr/bin/env python3
"""Validate five Android plain <string> resource catalogs and inventory legacy UI labels.

Every XML file in each supported values directory is aggregated. Plurals and
string arrays are outside this gate; it does not certify full linguistic coverage.

Only source/UI labels are inspected: user chat content, device logs and model output
MUST NOT be machine-translated or altered by app localization.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

res = Path("app/src/main/res")
files = {
    lang: sorted((res / ("values" + ("-" + lang if lang else ""))).glob("*.xml"))
    for lang in ("", "fa", "ar", "tr", "ru")
}

def parse(paths):
    result = {}
    nontranslatable = set()
    origins = {}
    for path in paths:
        for item in ET.parse(path).getroot().findall("string"):
            name = item.get("name")
            if not name or name in result:
                raise ValueError("Duplicate/empty localized key in " + str(path) + ": " + str(name) +
                                 "; previous=" + str(origins.get(name, "none")))
            result[name] = "".join(item.itertext()).strip()
            origins[name] = path
            if not result[name]:
                raise ValueError("Empty translation: " + str(path) + " " + name)
            if item.get("translatable") == "false":
                nontranslatable.add(name)
    return result, nontranslatable

parsed = {language: parse(paths) for language, paths in files.items()}
catalog = {language: content[0] for language, content in parsed.items()}
nontranslatable = parsed[""][1]
base = set(catalog[""])
for language in ("fa", "ar", "tr", "ru"):
    current = set(catalog[language])
    missing = sorted(base - nontranslatable - current)
    unexpected = sorted(current - base)
    if missing or unexpected:
        sys.exit(language + " resource catalog mismatch. Missing=" +
                 repr(missing[:15]) + "; unexpected=" + repr(unexpected[:15]))
    # A translated key can exist yet crash getString/String.format at runtime.
    # Compare argument positions and types; literal %% is not an argument.
    def arguments(text):
        return sorted(re.findall(r'%(\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])',
                                 text.replace('%%', '')))
    for key in base & current:
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
# A previously localized screen must not silently regress to English-only
# literal Text() labels. Legacy screens are inventoried but not prematurely
# declared fully translated.
fully_localized = (
    "LocalModelHubPanel.kt",
    "ElevenLabsPanel.kt",
    "AgentTeamPanel.kt",
    "PhoneHarnessPanel.kt",
)
for name in fully_localized:
    if not (roots / name).is_file():
        sys.exit("Localization coverage panel missing: " + name)
    if stats.get(name, 0):
        sys.exit("Direct English Text() literal returned in localized panel: " + name)

# Chat accessibility descriptions are user-facing UI too. Keep their English
# values stable for existing keyboard tests while requiring localized resources.
chat_accessibility = (
    "chat_go_to_latest_message", "chat_composer_description",
    "chat_input_description", "chat_configure_key_description", "chat_select_model",
)
for key in chat_accessibility:
    if key not in base:
        sys.exit("Required localized Chat accessibility label missing: " + key)
    for lang in ("fa", "ar", "tr", "ru"):
        if catalog[lang][key] == catalog[""][key]:
            sys.exit("English-only Chat accessibility label in " + lang + ": " + key)
# The conversation migration contains user-facing phrases rather than brands.
# Require actual translations for its split catalog, not English fallbacks.
for key in sorted(name for name in base - nontranslatable if name.startswith("conv_")):
    for lang in ("fa", "ar", "tr", "ru"):
        if catalog[lang][key] == catalog[""][key]:
            sys.exit("English-only conversation label in " + lang + ": " + key)
chat_source = (roots / "ProviderChatScreen.kt").read_text(encoding="utf-8")
if re.search(r'\bcontentDescription\s*=\s*"', chat_source):
    sys.exit("Hardcoded Chat accessibility description; use a localized resource")

brain_source = (roots / "MatrixGraphScreen.kt").read_text(encoding="utf-8")
if re.search(r'\bcontentDescription\s*=\s*"', brain_source):
    sys.exit("Hardcoded Brain accessibility description; use a localized resource")

print("Five Android locale catalogs validated, keys:", len(base))
print("Remaining direct Text string-literal occurrences:", sum(stats.values()))
for name, count in stats.items():
    print(f"  {name}: {count}")
print("Untranslated UI literals are still tracked for staged resource migration.")
