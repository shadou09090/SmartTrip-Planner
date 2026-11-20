import test from 'node:test';
import assert from 'node:assert/strict';
import { decideWorkspaceHydration } from './packageWorkspaceSync.js';

test('late server responses cannot replace a newer request or draft revision', () => {
  assert.equal(decideWorkspaceHydration({ requestId: 1, latestRequestId: 2, draftRevision: 0,
    requestDraftRevision: 0, pendingAtRequestStart: false }), 'stale-response');
  assert.equal(decideWorkspaceHydration({ requestId: 2, latestRequestId: 2, draftRevision: 3,
    requestDraftRevision: 2, pendingAtRequestStart: false }), 'draft-edited-during-request');
});

test('server refresh does not hydrate over a pending local draft except after confirmed save', () => {
  const request = { requestId: 2, latestRequestId: 2, draftRevision: 3, requestDraftRevision: 3,
    pendingAtRequestStart: true };
  assert.equal(decideWorkspaceHydration(request), 'local-draft-pending');
  assert.equal(decideWorkspaceHydration({ ...request, acceptServerDraft: true }), 'apply-server');
});
