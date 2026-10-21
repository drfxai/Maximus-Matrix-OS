# ADR: Native Galaxy stage 2 — viewport safety and durable activity projection

Status: Implementation increment for owner review. Baseline: main 4842681.

## Source audit

The six native layouts already use one read-only module/Room graph and cache geometry off
main. Areas labels are drawn at unbounded projected anchors, so long labels can overlap.
A floating camera toolbar occludes the upper graph, and hit testing and drawing currently
use the full canvas. Timeline currently assigns the latest timestamp to a module node;
it does not show each durable event. Room's existing event repository exposes the latest
100 committed events. Project workspaces have no public revision-history API and Hindsight's
public progress API returns text without timestamps; neither can honestly supply a complete
chronological history without a separate repository contract.

## Decision

- Preserve original Brain projection and default quaternion camera. Apply a shared inset
  viewport to all six additional layouts, used identically for drawing and hit testing.
- Place wrapped Areas labels with bounded collision handling. Retain an accessible area
  isolation list even when a dense canvas cannot accommodate every label.
- Add a bounded read-only chronological projection of committed Room events. Each event
  retains its persisted ID, timestamp, source/target, status type, and source reference.
  Keep undated module definitions explicit, never fabricate events or infer success.
- Preserve canonical graph-node identities across layouts. Durable event marks are activity
  overlays, not a second mutable graph database. Rendering does not write application facts.
- Display unavailable project-revision and memory-history sources explicitly. Do not scan
  private storage ad hoc, decrypt memory behind a new approval boundary, or label filesystem
  modification times as source revisions.
- Add relationship inspection and common area/search/fit controls without new rendering
  frameworks or changes to existing language preferences, Room schema, signing or versions.

## Verification

Pure JVM tests cover projection/hit viewport equivalence, label bounds/non-overlap,
chronological source identity and repeated events, undated and corrupted inputs. Run the
existing Compose/keyboard emulator gates after integration. Measure deterministic geometry
preparation on the local host and identify that host; this is not physical Android FPS.
