---
name: manufacturers-remote-db
description: "Discover potential manufacturer names from article canonical_names, create new manufacturers, and wire articles on Railway prod DB"
disable-model-invocation: true
---
# manufacturers-remote-db (Railway production)

Analyze articles without a `manufacturer_id`, extract potential brand/manufacturer names
from `canonical_name`, create new manufacturer records, and wire articles to them.

Operates against the **Railway production database**. Every write is real.

---

## REMOTE / PROD DB -- READ THIS FIRST

### Prerequisites

- Railway CLI installed and linked: `railway login && railway link`
- postgres-mcp pointing at Railway

### Step 1: Switch postgres-mcp to Railway

```bash
bb postgres-mcp-railway
```

This writes Railway connection settings to `tmp/.postgres.env`. After running it,
**restart the postgres MCP server** for the new settings to take effect.

When done with the remote session, switch back:

```bash
bb postgres-mcp-dev
```

### Safety rules (production-specific)

- **Always preview before writing** — run SELECT queries to confirm what will be affected
	before any INSERT or UPDATE.
- **Batch sizes** — limit INSERT to ~25 rows per statement for reviewability.
- **Do NOT print `DATABASE_URL`** values in conversation.
- **Verify immediately** after every write with a SELECT.

---

## Response size rules (non-negotiable)

- **Summarise, don't repeat** — describe SQL results in tables or bullet points, not raw rows.
- **Per-phase summaries only** — concise status lines after each phase.
- **Article tables** — compact Markdown, max 50 rows visible; if more, state count.

---

## Hard rules (non-negotiable)

- **`normalized_key` convention**: lowercase, hyphens for spaces/special chars, no diacritics
	(e.g. `"Zlatni Korijen"` -> `zlatni-korijen`, `"Dr. Luigi"` -> `dr-luigi`, `"Semić"` -> `semic`).
- **No duplicate manufacturers** — always check existing manufacturers before creating.
- **ILIKE pattern safety** — use anchored/specific patterns to avoid false matches:
	- Prefix anchor for common words: `'Nordic%'` not `'%Nordic%'`
	- Trailing space for short words: `'Seth %'` not `'%Seth%'`
	- Dot anchor for abbreviations: `'Domest.%'` not `'%Domest%'`
- **Batch-first** — use single INSERT for all new manufacturers, then batch UPDATE for wiring.
- **Verify after every write phase** — confirm counts match expectations.
- **dm sub-brands** — Denkmit, Dmbio, Jessa, Mivolis, Profissimo, Balea, Alverde all map to
	manufacturer `dm` (not separate manufacturer entries).

---

## Phase 0: Survey the landscape

### Step 0a — Count articles without manufacturer

```sql
SELECT COUNT(*) AS total,
			 COUNT(*) FILTER (WHERE manufacturer_id IS NULL) AS without_manufacturer
FROM articles;
```

### Step 0b — Fetch unmanufactured article names

```sql
SELECT id, canonical_name
FROM articles
WHERE manufacturer_id IS NULL
ORDER BY canonical_name;
```

### Step 0c — Fetch all existing manufacturers

```sql
SELECT display_name FROM manufacturers ORDER BY display_name;
```

---

## Phase 1: Extract potential manufacturer names

Analyze each `canonical_name` and identify words/tokens that are likely brand or
manufacturer names. Use these heuristics:

### Identification heuristics

| Signal | Example | Confidence |
|--------|---------|------------|
| Non-Bosnian capitalized word | `"Zbregov"`, `"Karington"`, `"Nordic"` | High |
| Known brand pattern at start/end | `"Denkmit sredstvo za..."` | High |
| Pharma drug brand name | `"Voltaren"`, `"Euthyrox"`, `"Nebilet"` | High |
| Foreign word (German, English, Italian) | `"Brotlinies"`, `"Tartaruga"`, `"Munchmallow"` | High |
| Truncated brand at name boundary | `"...Bosnalij"` (= Bosnalijek) | Medium |
| ALL-CAPS word in mixed name | `"ESENBAK BULARDI"` | Medium |

### Anti-patterns (do NOT treat as manufacturer)

| Token | Why | Action |
|-------|-----|--------|
| Intenso, Classic, Soft, Forte, Espresso, Prženi | Product descriptors | Skip |
| Product words: jogurt, sir, kafa, šećer, hljeb | Common Bosnian nouns | Skip |
| City/country names: Brazil, Paris, Bayreuth | Origin, not manufacturer (usually) | Skip unless clearly a brand |
| Size/weight: 1KG, 500G, 250ML | Units | Skip |
| Truncated < 3 chars: `"Dr"`, `"Su"`, `"Wh"` | Too short to be sure alone | Check context |

### Output structure

Organize findings into three groups:

1. **Already in DB** — articles whose brand matches an existing manufacturer (wire immediately)
2. **dm sub-brands** — articles with dm house-brand names (wire to `dm`)
3. **New manufacturers needed** — articles with identifiable brands not yet in manufacturers table

For each candidate, note:
- Proposed `display_name` and `normalized_key`
- ILIKE pattern for matching
- Which article(s) it covers

**Present to user as a Markdown table for review before proceeding.** On production,
never auto-execute writes without user confirmation.

---

## Phase 2: Wire articles to existing manufacturers

For brands that match existing manufacturers, run targeted UPDATEs:

```sql
-- Pattern: one UPDATE per existing manufacturer
UPDATE articles
SET manufacturer_id = (SELECT id FROM manufacturers WHERE display_name = '<Manufacturer>')
WHERE manufacturer_id IS NULL
AND canonical_name ILIKE '<pattern>';
```

### dm sub-brands (batch together)

```sql
UPDATE articles
SET manufacturer_id = (SELECT id FROM manufacturers WHERE display_name = 'dm')
WHERE manufacturer_id IS NULL
AND (
	canonical_name ILIKE 'Denkmit%'
	OR canonical_name ILIKE 'Dmbio%'
	OR canonical_name ILIKE 'Jessa %'
	OR canonical_name ILIKE 'Mivolis%'
	OR canonical_name ILIKE 'Profissimo%'
	OR canonical_name ILIKE 'Balea%'
	OR canonical_name ILIKE 'Alverde%'
);
```

**Verify immediately:**

```sql
SELECT a.canonical_name, m.display_name AS manufacturer
FROM articles a
JOIN manufacturers m ON a.manufacturer_id = m.id
WHERE m.display_name IN ('<list of matched manufacturers>')
ORDER BY m.display_name, a.canonical_name;
```

---

## Phase 3: Create new manufacturers + wire articles

### Step 3a — Preview what will be created (mandatory on prod)

Before any INSERT, show the user:
- Full list of proposed `display_name` + `normalized_key` pairs
- Confirm none clash with existing manufacturers

```sql
-- Check for near-duplicates
SELECT display_name, normalized_key FROM manufacturers
WHERE normalized_key IN ('<key1>', '<key2>', ...);
```

### Step 3b — Batch INSERT new manufacturers

After user confirmation:

```sql
INSERT INTO manufacturers (id, display_name, normalized_key) VALUES
(gen_random_uuid(), '<DisplayName1>', '<normalized-key-1>'),
(gen_random_uuid(), '<DisplayName2>', '<normalized-key-2>'),
...;
```

Limit to ~25 rows per INSERT for reviewability.

### Step 3c — Batch UPDATE to wire articles

Use a single UPDATE with FROM join for efficiency:

```sql
UPDATE articles a
SET manufacturer_id = m.id
FROM manufacturers m
WHERE a.manufacturer_id IS NULL
AND (
	(m.display_name = '<Mfr1>' AND a.canonical_name ILIKE '<pattern1>')
	OR (m.display_name = '<Mfr2>' AND a.canonical_name ILIKE '<pattern2>')
	...
);
```

Split into multiple UPDATE batches if > 25 conditions.

### Step 3d — Verify (mandatory on prod)

```sql
SELECT m.display_name AS manufacturer, COUNT(*) AS articles_linked
FROM articles a
JOIN manufacturers m ON a.manufacturer_id = m.id
WHERE m.display_name IN ('<list of new manufacturers>')
GROUP BY m.display_name
ORDER BY m.display_name;
```

---

## Phase 4: Update taxonomy files (conditional)

If new brand-to-manufacturer mappings were established, update the taxonomy files
so future `articles_research.clj` runs recognize them automatically:

| File | When to update |
|------|---------------|
| `resources/articles/taxonomy/self-named-brands.edn` | Brand name = manufacturer name (e.g. `"Zbregov"`, `"Cekin"`) |
| `resources/articles/taxonomy/brand-parent-mappings.edn` | Brand differs from parent (e.g. `"Nocko"` -> `"Milkos"`) |

---

## Phase 5: Final report

```sql
SELECT
	COUNT(*) AS total,
	COUNT(*) FILTER (WHERE manufacturer_id IS NULL) AS without_manufacturer,
	COUNT(*) FILTER (WHERE manufacturer_id IS NOT NULL) AS with_manufacturer,
	ROUND(100.0 * COUNT(*) FILTER (WHERE manufacturer_id IS NOT NULL) / COUNT(*), 1) AS coverage_pct
FROM articles;
```

Report:
1. **Existing manufacturers linked** — count per manufacturer
2. **New manufacturers created** — count, with article counts
3. **Coverage change** — before vs after percentages
4. **Remaining unmanufactured** — note that ~40-60% of articles (generic produce, services, bags, etc.) legitimately have no manufacturer

---

## Completion gate

- [ ] All identifiable brand names in article names have been matched
- [ ] No duplicate manufacturers created (checked before INSERT)
- [ ] ILIKE patterns verified to not cause false matches
- [ ] Counts verified after each write phase
- [ ] Taxonomy files updated if new brand mappings discovered
- [ ] Final coverage % reported
- [ ] postgres-mcp switched back to dev: `bb postgres-mcp-dev`

---

## Cleanup (always last)

Switch postgres-mcp back to dev database:

```bash
bb postgres-mcp-dev
```

Then restart the postgres MCP server.

---

## Common Bosnian manufacturer patterns (quick reference)

| Pattern in article name | Manufacturer | Type |
|------------------------|-------------|------|
| Denkmit, Dmbio, Jessa, Mivolis, Profissimo, Balea, Alverde | dm | dm sub-brands |
| Zbregov | Zbregov (or Dukat parent) | Dairy |
| Zdenka | Zdenka | Cheese |
| Cekin | Cekin | Meat |
| Nafaka, Karington, Zlatni Korijen | Bakery brands | Bread |
| Voltaren, Euthyrox, Nebilet, Revalid | Pharma brands | Pharmaceutical |
| Carefree, Essence, Lola Lady | Personal care brands | Non-food |
| Tucana, Superiore | Coffee brands | Coffee |
| Tuzlanska | Tuzlanska Solana | Salt |
