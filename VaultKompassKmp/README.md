# VaultKompassKmp

The Obsidian vault for the **KompassKmp** repo. It lives inside the repo, at the
repo root, and it is committed — the notes travel with the code.

Open this folder as an Obsidian vault (Open folder as vault → pick
`KompassKmp/VaultKompassKmp`).

## Folders

- `workflow/` — the PR-tracking playbook, copied from the Template vault.
  Read `WORKFLOW.md` first.
- `dev-progress/inprogress/` — one folder per work item, holding its PR note.
- `dev-progress/done/` — the same folders after the PR merges, with the note
  renamed to `[DONE] …`.

## Naming

```
dev-progress/inprogress/<NNN> <AB#id> - <work item title>/PR#<n> - <pr title>.md
```

`<NNN>` is a global 3-digit sequence: scan `inprogress/` + `done/`, take max + 1.
It is assigned once and never renumbered. See `workflow/WORKFLOW.md`.

## Git

`.obsidian/` is gitignored — it holds per-machine window state and caches.
Everything else here is committed on purpose.

Never paste tokens, keys, or PII into a note. The vault is part of the repo.
