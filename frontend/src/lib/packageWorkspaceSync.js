export function decideWorkspaceHydration({ requestId, latestRequestId, draftRevision, requestDraftRevision,
  pendingAtRequestStart, acceptServerDraft = false }) {
  if (requestId !== latestRequestId) return 'stale-response';
  if (draftRevision !== requestDraftRevision) return 'draft-edited-during-request';
  if (pendingAtRequestStart && !acceptServerDraft) return 'local-draft-pending';
  return 'apply-server';
}
