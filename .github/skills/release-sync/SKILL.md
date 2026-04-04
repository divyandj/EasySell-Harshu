---
name: release-sync
description: "Use when: pushing or syncing EasySell repositories to GitHub branches, including root EasySell-Harshu and nested EasySell-WEB; handling branch pushes, optional merges to main, and conflict-safe git workflow."
---

# Release Sync Skill

## Purpose
Run a repeatable, safe release workflow for this workspace's two repositories:
- Root repo: EasySell-Harshu
- Nested repo: EasySell-WEB

## Repos And Defaults
- Root repo path: c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu
- Nested repo path: c:/Users/Divyan/AndroidStudioProjects/EasySellHarshu/EasySell-WEB
- Common branches:
- Root feature branch: Cloudinary-image
- Nested feature branch: v2
- Main branches: main

## Required Safety Rules
1. Always inspect status, current branch, and remotes before any commit/push.
2. Never use destructive commands like git reset --hard or force push unless the user explicitly asks.
3. If a pull/merge creates conflicts, stop and ask the user which side is source of truth.
4. Do not merge to main unless user explicitly requested merge.
5. For nested repo work, run git -C with explicit paths to avoid cwd mistakes.

## Standard Workflow
1. Inspect both repos:
- git -C <root> status --short --branch
- git -C <root> remote -v
- git -C <nested> status --short --branch
- git -C <nested> remote -v

2. Push nested branch updates:
- git -C <nested> checkout v2
- git -C <nested> add <changed files>
- git -C <nested> commit -m "<message>" (if needed)
- git -C <nested> push origin v2

3. Optional nested merge to main (only if asked):
- git -C <nested> fetch origin
- git -C <nested> checkout main
- git -C <nested> pull --ff-only origin main
- git -C <nested> merge --no-ff v2 -m "Merge branch 'v2' into main"
- git -C <nested> push origin main

4. Push root branch updates:
- git -C <root> checkout Cloudinary-image
- git -C <root> add <changed files>
- git -C <root> commit -m "<message>" (if needed)
- git -C <root> push origin Cloudinary-image

5. Optional root merge to main (only if asked):
- git -C <root> fetch origin
- git -C <root> checkout main
- git -C <root> pull --ff-only origin main
- git -C <root> merge --no-ff Cloudinary-image -m "Merge branch 'Cloudinary-image' into main"
- git -C <root> push origin main

## Conflict Handling Playbook
If pull or merge fails:
1. Report exact conflict type (non-fast-forward or file conflicts).
2. Offer clear choices:
- Keep local branch as source of truth and force push (only with explicit approval).
- Keep remote main as source of truth and replay selected commits.
- Skip direct merge and open PR from feature branch.
3. If merge is in progress and user chooses to stop, run:
- git -C <repo> merge --abort

## Output Checklist
Always return:
1. What was pushed and to which branch.
2. Whether main merge was done or intentionally skipped.
3. Any conflicts encountered and how they were resolved.
4. Next recommended command if manual follow-up is needed.
