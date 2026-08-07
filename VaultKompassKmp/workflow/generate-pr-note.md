# Generate / refresh a PR note

Paste this prompt to Claude (in a session with `gh` available and authenticated).
Replace `<n>` with the PR number.

---

**Prompt:**

> Generate a PR note for PR #`<n>` following `workflow/pr-note-template.md`
> and the conventions in `workflow/WORKFLOW.md`. Pull live data with the `gh`
> command below, fill every field, include the warning block only if a check is
> failing or the PR is blocked, and save it to the correct `dev-progress/`
> location based on status (inprogress vs done) and the PR-number → repo mapping.
> If the work-item folder doesn't exist yet, create it with the next global
> number prefix (`<NNN>` = max across inprogress/ + done/, +1).
> If the note already exists, refresh it in place and update the "Generated"
> timestamp.

---

**The data command Claude runs:**

```bash
gh pr view <n> --json \
  number,title,url,author,headRefName,baseRefName,isDraft,state,\
  reviewDecision,statusCheckRollup,mergeable,body
```

Notes:
- `statusCheckRollup` carries each CI check's name + conclusion → use it for the
  **Checks** row (e.g. detekt failing, others passing).
- `isDraft` + `state` + `reviewDecision` → the **Review** row and the 🟢/⚪ marker.
- `author.login` → the GitHub handle; `author.name` → display name.
- The note is a **snapshot** — re-run this prompt to refresh it.
- **Redact secrets.** The PR `body` can contain tokens, keys, URLs with
  credentials, or PII. Never copy such values into the note — summarize and drop
  anything that looks like a secret. The note is a shared artifact.

**Refresh many at once (optional):**

> Refresh every PR note under `dev-progress/inprogress/` against current GitHub
> state, move any now-merged ones to `done/` with a `[DONE]` prefix, and update
> their timestamps.
