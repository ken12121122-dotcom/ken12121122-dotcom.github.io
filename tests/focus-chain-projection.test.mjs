import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const require = createRequire(import.meta.url);
const focus = require('../android-native/app/src/main/assets/amin-wiki-graph/focus-chain-projection.js');
const html = readFileSync(new URL('../android-native/app/src/main/assets/amin-wiki-graph/index.html', import.meta.url), 'utf8');

function graph(nodeCount = 10) {
  const nodes = Array.from({length: nodeCount}, (_, index) => ({id: `n${index}`, title: `Node ${index}`}));
  const relations = [
    {id: 'e01', from: 'n0', to: 'n1', type: 'depends_on'},
    {id: 'e12', from: 'n1', to: 'n2', type: 'depends_on'},
    {id: 'e23', from: 'n2', to: 'n3', type: 'uses'},
    {id: 'e14', from: 'n1', to: 'n4', type: 'has_run'},
    {id: 'e42', from: 'n4', to: 'n2', type: 'has_job'},
    {id: 'cycle', from: 'n2', to: 'n0', type: 'related_to'},
    {id: 'reverse', from: 'n5', to: 'n0', type: 'provides_evidence_for'}
  ];
  return {nodes, relations};
}

test('relation registry classifies semantics and never mutable titles', () => {
  assert.equal(focus.classifyRelation({type: 'depends_on', title: '風險'}), 'dependency');
  assert.equal(focus.classifyRelation({type: 'provides_evidence_for', title: 'Workflow'}), 'evidence');
  assert.equal(focus.classifyRelation({type: 'unknown_authored_relation', title: 'Risk Chain'}), 'other');
  const withoutStableEdgeId = focus.normalizeGraph({nodes: [{id: 'a'}, {id: 'b'}], relations: [{from: 'a', to: 'b', type: 'contains'}]});
  assert.deepEqual(withoutStableEdgeId.relations, []);
});

test('two endpoint route search is directed, deterministic, and cycle safe', () => {
  const routes = focus.twoEndpointCandidates(graph(), 'n0', 'n2');
  assert.deepEqual(routes.forward[0].edge_ids, ['e01', 'e12']);
  assert.equal(routes.forward[0].hop_count, 2);
  assert.deepEqual(routes.forward[1].edge_ids, ['e01', 'e14', 'e42']);
  assert.deepEqual(routes.reverse[0].edge_ids, ['cycle']);
  assert.equal(routes.reverse[0].direction, 'reverse');

  const reverse = focus.twoEndpointCandidates(graph(), 'n0', 'n5');
  assert.equal(reverse.forward.length, 0);
  assert.deepEqual(reverse.reverse[0].edge_ids, ['reverse']);
  assert.equal(reverse.reverse[0].direction, 'reverse');
});

test('route projection preserves IDs, collapses siblings, and never mutates graph', () => {
  const source = graph(), snapshot = JSON.stringify(source);
  const route = focus.routeCandidates(source, 'n0', 'n3')[0];
  const projected = focus.project(source, [route]);
  assert.deepEqual(projected.node_ids, ['n0', 'n1', 'n2', 'n3']);
  assert.deepEqual(projected.edge_ids, ['e01', 'e12', 'e23']);
  assert.equal(projected.collapsed_by_node.n1, 1);
  assert.equal(projected.collapsed_by_node.n2, 2);
  assert.equal(projected.hidden_node_count, 6);
  assert.equal(JSON.stringify(source), snapshot);
});

test('long routes are chunked into at most six hops with an overlapping junction', () => {
  const nodes = Array.from({length: 15}, (_, index) => ({id: `p${index}`}));
  const relations = Array.from({length: 14}, (_, index) => ({id: `p${index}-${index + 1}`, from: `p${index}`, to: `p${index + 1}`, type: 'contains'}));
  const route = focus.routeCandidates({nodes, relations}, 'p0', 'p14')[0];
  const segments = focus.routeSegments(route, 6);
  assert.equal(segments.length, 3);
  assert.ok(segments.every(segment => segment.edge_ids.length <= 6));
  assert.equal(segments[0].node_ids.at(-1), segments[1].node_ids[0]);
  assert.equal(segments[1].node_ids.at(-1), segments[2].node_ids[0]);
});

test('pin persistence restores only live node and edge references and caps three chains', () => {
  const source = graph();
  const valid = focus.routeCandidates(source, 'n0', 'n2')[0];
  const stale = {...valid, route_id: 'stale', node_ids: ['n0', 'missing'], edge_ids: ['missing-edge'], pinned: true};
  const raw = focus.serializePinnedRoutes([
    {...valid, route_id: 'one', pinned: true}, {...valid, route_id: 'two', pinned: true},
    {...valid, route_id: 'three', pinned: true}, {...valid, route_id: 'four', pinned: true}, stale
  ]);
  const restored = focus.restorePinnedRoutes(raw, source);
  assert.equal(restored.length, 3);
  assert.ok(restored.every(route => route.node_ids.every(id => source.nodes.some(node => node.id === id))));
  assert.deepEqual(focus.restorePinnedRoutes('{bad json', source), []);
});

test('accordion summaries and expansion use the authored relation registry', () => {
  const summaries = focus.chainSummaries(graph(), 'n0');
  assert.deepEqual(summaries.map(item => item.chain_type), ['dependency']);
  const route = focus.expandChain(graph(), 'n0', 'dependency', 6);
  assert.deepEqual(route.node_ids, ['n0', 'n1', 'n2', 'n3']);
  assert.deepEqual(route.edge_ids, ['e01', 'e12', 'e23']);
});

test('large mobile graph remains bounded to three six-hop paths with shared identity', () => {
  const nodes = Array.from({length: 300}, (_, index) => ({id: `large-${index}`}));
  const relations = [];
  for (let index = 0; index < 299; index++) {
    relations.push({id: `large-edge-${index}`, from: `large-${index}`, to: `large-${index + 1}`, type: 'depends_on'});
  }
  for (let index = 10; index < 290; index += 10) {
    relations.push({id: `large-sibling-${index}`, from: `large-${index}`, to: `large-${index + 2}`, type: 'has_run'});
  }
  const source = {nodes, relations};
  const first = focus.routeCandidates(source, 'large-0', 'large-20')[0];
  const second = focus.routeCandidates(source, 'large-10', 'large-30')[0];
  const third = focus.routeCandidates(source, 'large-20', 'large-40')[0];
  const fourth = focus.routeCandidates(source, 'large-30', 'large-50')[0];
  const projected = focus.project(source, [first, second, third, fourth]);
  assert.equal(projected.route_count, 3);
  assert.ok(projected.node_ids.length <= 21);
  assert.equal(new Set(projected.node_ids).size, projected.node_ids.length);
  assert.ok(projected.hidden_node_count >= 279);
});

test('mobile canvas exposes focus controls without adding another canvas or write authority', () => {
  assert.equal((html.match(/<canvas\s+id="graph"/g) || []).length, 1);
  for (const marker of ['id="focusExpand"', 'id="focusManager"', 'id="routeReview"', '📌 Pin Path', '最多同時保留 3 條鏈', '每段最多 6 hops']) {
    assert.ok(html.includes(marker), `missing ${marker}`);
  }
  assert.match(html, /focus-chain-projection\.js/);
  assert.match(html, /window\.AminGraphBack/);
  assert.doesNotMatch(html, /createElement\(['"]canvas|workflow_dispatch|mergePullRequest|createRelease/);
});
