# PR-Tracking Workflow (Obsidian + Claude + GitHub)

A reproducible setup for running coding work through GitHub PRs while tracking
everything as markdown notes in Obsidian. The IDE is optional; the source of
truth is git + GitHub, and Obsidian is the readable dashboard on top.

## The idea in one line

Claude implements on a branch and opens a PR → a generated note mirrors the PR's
state into the vault → a status board organizes the notes → review and CI happen
on GitHub → merge, then move the note to `done`.

## Folder layout

The **vault root is the project name** (e.g. `KMPFramework/`). Inside it,
keep a `dev-progress/` knowledge base organized by **status**:

```
<ProjectName>/                                ← vault root = project name
└── dev-progress/
    ├── inprogress/
    │   └── <NNN> <AB#id> - <work item title>/
    │       └── PR#<n> - <pr title>.md        ← the PR note
    └── done/
        └── <NNN> <AB#id> - <work item title>/
            └── [DONE] PR#<n> - <pr title>.md
```

A note's **location encodes its status**. When a PR merges, move its note from
`inprogress/` to `done/` and prefix the filename with `[DONE]`. The folder
keeps its number — it never changes after creation.

## Naming & ID conventions

- `<NNN>` — **global sequence number** (3 digits, zero-padded: `001`, `002`, …)
  prefixed to every work-item folder so the vault sorts chronologically.
  It tracks the *work itself*, not the PR — work without a PR still gets a
  number. Assigned once at folder creation: scan all existing folders under
  `inprogress/` + `done/` and use **max + 1**. Never reused, never renumbered.
- `AB#<id>` — work item (the feature/story; e.g. Azure Boards, or a roadmap
  `<phase>-<slug>` ID like `P1-lint`).
- `PR#<n>` — the GitHub pull request implementing it. Independent of `<NNN>`
  (e.g. folder `002` may hold `PR#5`).
- `[DONE]` filename prefix marks merged work at a glance.

## Status lifecycle

```
inprogress  →  done
draft →  ready for review  →  approved  →  merged
```

A PR note tracks both: where the file lives (`inprogress/` vs `done/`) and the
PR's own review state (draft / ready / approved / merged), shown in its table.

## The loop (one roadmap item = one branch = one PR)

1. Pick the next item from `roadmap.md` (or an `AB#` work item).
2. Claude creates a feature branch, implements, commits (Conventional Commits),
   pushes.
3. Claude opens the PR with `gh`.
4. Claude generates the PR note into `dev-progress/inprogress/<NNN> <AB#…>/`
   (new folder → next global number = max existing + 1) using
   `pr-note-template.md` and `generate-pr-note.md` (in this folder).
5. Reviewer reviews the diff **on GitHub**; CI runs build + lint + tests.
6. Claude addresses comments, pushes to the same branch → PR updates in place.
   Re-generate the PR note to refresh its status.
7. On merge: move the note to `done/`, prefix `[DONE]`, check the item off in
   `roadmap.md`.

## How the PR note gets into Obsidian

The note is a plain `.md` file Claude writes from GitHub data
(`gh pr view … --json …`). It is a **snapshot**, not a live view — it reflects
the PR state at generation time. Refresh it by re-running the generate prompt.
(If you want truly live data, a GitHub community plugin can embed it, but the
snapshot-via-Claude approach is simpler and is what this workflow assumes.)

## CI is what makes "no IDE" work

Add a GitHub Actions workflow that runs build + lint (e.g. detekt) + tests on
every PR. Then a reviewer sees a green/red check before reading a line, and
nobody needs the toolchain locally just to verify a change. Visual/behavioral
checks (does the screen look right?) still need a device, emulator, or
screenshot — that's the only thing CI can't cover.

## Copying this workflow to a new repo

1. Copy this `workflow/` folder into the new project's vault.
2. Make the project name the vault's root folder (the `<ProjectName>/` in the
   layout above).
3. Point Claude at this file: "Follow workflow/WORKFLOW.md for PR tracking."
4. Make sure the repo has the `gh` CLI authenticated and a CI workflow.

## Multi-repo vaults (optional)

If a single vault tracks more than one repo, restore an extra
`<area-or-repo>/` layer between `dev-progress/<status>/` and the work-item
folder:

```
<VaultName>/
└── dev-progress/
    ├── inprogress/
    │   └── <area-or-repo>/
    │       └── <NNN> <AB#id> - <work item title>/
    │           └── PR#<n> - <pr title>.md
    └── done/
        └── <area-or-repo>/
            └── <NNN> <AB#id> - <work item title>/
                └── [DONE] PR#<n> - <pr title>.md
```

When PR numbers can collide across repos, also keep a PR-number → repo
mapping table so Claude doesn't have to guess which repo a PR belongs to:

| PR number range | Repo / area |
|-----------------|-------------|
| 7000–7999       | <repo A>    |
| 400–499         | <repo B>    |

The single-repo flat layout above the table is the default; this section is
only for when one vault tracks multiple repos.
