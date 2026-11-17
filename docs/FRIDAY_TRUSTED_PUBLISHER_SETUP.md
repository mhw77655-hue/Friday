# Friday trusted publisher setup

The repository builder separates an untrusted coding worker from a trusted publisher:

- The coding worker receives read-only repository access and produces a patch.
- A separate publisher validates the patch, pushes an isolated proposal branch and opens a draft pull request.
- No automatic merge, signing, release or phone installation is performed.

## One-time repository setting

The publisher currently uses the repository-scoped GitHub Actions token. GitHub has rejected PR creation with:

`GitHub Actions is not permitted to create or approve pull requests (createPullRequest)`

The workflow already requests `contents: write` and `pull-requests: write` for the trusted publisher job. The remaining gate is the repository-level setting.

1. Open [Friday repository Actions settings](https://github.com/mhw77655-hue/Friday/settings/actions).
2. Under **Workflow permissions**, enable **Allow GitHub Actions to create and approve pull requests**.
3. Keep the publisher job's explicit permissions narrow. Do not grant write permissions or publisher credentials to the coding-worker job.
4. Keep `main` protected with required checks and owner review before merging.

If the setting remains disabled, the workflow can still preserve a validated proposal branch but cannot create its draft PR.

## Safe retry behavior

The publisher now handles the partial-success case where a prior run pushed a branch and then failed to create the PR:

- It reuses an existing branch only if its Git tree exactly matches the newly validated patch.
- It refuses to overwrite an existing branch with different content.
- It reuses an already-open PR instead of creating a duplicate.
- When GitHub blocks PR creation, it prints this setup instruction and preserves the branch.

After enabling the setting, rerun the same request while its validated branch is still current. If the branch content differs from the new patch, review the old branch and issue a new request rather than overwriting it.

## Security boundary

Do not solve this by giving the coding worker a personal access token or write permissions. Only the trusted publisher job should have write access, and every proposal must pass path/symlink validation and hosted build/test checks before owner review.
