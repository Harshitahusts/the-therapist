# Knowledge base

Source documents for the companion's retrieval (RAG) layer. Each `sources/*.md`
file starts with a front-matter header that records provenance:

```
---
title: Behavioural activation
author: Haven project contributors
publication: Haven knowledge base
license: CC BY 4.0
source_url: https://github.com/harshitahusts/the-therapist/tree/main/knowledge/sources
redistribution_allowed: true
approaches: behavioral activation, CBT
---
```

`title`, `author` and `license` are required; ingestion fails without them.

## Rules for adding material

1. Only add text you have the right to use: your own writing, public-domain
   works, or openly licensed material (e.g. CC BY, CC BY-SA, CC0, or explicit
   permission). Record the licence accurately.
2. Do **not** add copyrighted books, paywalled articles or downloaded PDFs just
   because they are available online.
3. If a source allows use but not redistribution, keep it out of this public
   repository: put it in a private directory and ingest it with
   `python -m scripts.ingest_knowledge --dir /path/to/private` and set
   `redistribution_allowed: false`.
4. Add every source to the table in `/LICENSES.md`.
5. Prefer evidence-based psychoeducation. Never add content that diagnoses,
   recommends medication changes, or replaces professional care.

## Current contents

All files in `sources/` are original writing by this project's contributors,
summarising widely taught, evidence-based ideas in plain language. They are
licensed CC BY 4.0. They are general educational material, not clinical advice.

## Ingesting

```
cd backend
python -m scripts.ingest_knowledge            # uses ../knowledge/sources
```

Ingestion is idempotent: unchanged files are skipped, changed files are
re-chunked and re-embedded.
