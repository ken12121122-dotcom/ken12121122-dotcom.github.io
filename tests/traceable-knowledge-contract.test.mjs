import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const javaRoot = new URL('../android-native/app/src/main/java/com/amin/pocketgba/', import.meta.url);
const docsRoot = new URL('../android-native/docs/', import.meta.url);

test('traceable knowledge M0-M2 remains isolated and source-backed', () => {
  const contract = readFileSync(new URL('TraceableKnowledgeContract.java', javaRoot), 'utf8');
  const database = readFileSync(new URL('TraceableKnowledgeDatabase.java', javaRoot), 'utf8');
  const repository = readFileSync(new URL('TraceableKnowledgeRepository.java', javaRoot), 'utf8');
  const rawStore = readFileSync(new URL('ImmutableKnowledgeSourceStore.java', javaRoot), 'utf8');
  const docs = readFileSync(new URL('TRACEABLE_KNOWLEDGE_M0_M2_CONTRACT.md', docsRoot), 'utf8');

  for (const table of ['sources', 'documents', 'document_versions', 'chunks',
    'retrieval_logs', 'verification_records']) assert.match(database, new RegExp(`CREATE TABLE ${table}`));
  assert.match(database, /CREATE VIRTUAL TABLE chunks_fts USING fts4/);
  assert.match(rawStore, /SHA-256/);
  assert.match(rawStore, /if \(target\.isFile\(\)\)/);
  assert.match(repository, /beginTransaction/);
  assert.match(repository, /TraceableKnowledgeContract\.require/);
  assert.match(repository, /offline_search_failed/);
  assert.match(contract, /CAP-TRACEABLE-KNOWLEDGE-001/);
  assert.match(docs, /does not connect Fox/);
  assert.doesNotMatch(contract + database + repository + rawStore,
    /AminActionDispatcher|SharedGraphSyncKernel|workflow_dispatch|mergePullRequest|createRelease/);
});
