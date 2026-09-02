(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.AminFocusChain = api;
})(typeof globalThis === 'object' ? globalThis : this, function () {
  'use strict';

  const VERSION = 1;
  const MAX_OPEN_CHAINS = 3;
  const MAX_DEPTH_PER_CHAIN = 6;
  const MAX_SEARCH_DEPTH = 24;
  const MAX_CANDIDATES = 4;
  const MAX_VISITS = 12000;
  const CHAIN_TYPES = [
    'dependency', 'risk', 'decision', 'evidence',
    'workflow', 'version', 'responsibility', 'impact', 'other'
  ];
  const CHAIN_LABELS = Object.freeze({
    dependency: 'Dependency Chain', risk: 'Risk Chain', decision: 'Decision Chain',
    evidence: 'Evidence Chain', workflow: 'Workflow Chain', version: 'Version Chain',
    responsibility: 'Responsibility Chain', impact: 'Impact Chain', other: 'Other / Related'
  });
  const RELATION_REGISTRY = Object.freeze({
    dependency: new Set(['depends_on', 'requires', 'requires_capability', 'uses', 'reads_from']),
    risk: new Set(['risk_of', 'has_risk', 'hazard_for', 'exposes', 'mitigates']),
    decision: new Set(['decides', 'decision_for', 'approved_by', 'rejected_by', 'selected_by']),
    evidence: new Set(['provides_evidence_for', 'evidence_for', 'verified_by', 'verifies', 'learned_from']),
    workflow: new Set(['contains', 'opens', 'executes', 'has_run', 'has_job', 'has_artifact']),
    version: new Set(['points_to', 'head_commit', 'version_of', 'supersedes', 'derived_from', 'promoted_to']),
    responsibility: new Set(['owns', 'owned_by', 'assigned_to', 'reviewed_by', 'has_review', 'certified_by', 'certifies']),
    impact: new Set(['impacts', 'affects', 'writes_to', 'produces', 'generated', 'deploys'])
  });

  const clean = value => String(value == null ? '' : value).trim();
  const relationType = relation => clean(relation && (relation.type || relation.relation || relation.relationship_type || relation.relationshipType)).toLowerCase() || 'related_to';
  const relationId = relation => clean(relation && (relation.id || relation.edge_id || relation.edgeId));
  const sourceId = relation => clean(relation && (relation.from || relation.source || relation.source_node_id || relation.sourceNodeId));
  const targetId = relation => clean(relation && (relation.to || relation.target || relation.target_node_id || relation.targetNodeId));

  function classifyRelation(relation) {
    const semantic = relationType(relation);
    for (const type of CHAIN_TYPES) {
      if (type !== 'other' && RELATION_REGISTRY[type].has(semantic)) return type;
    }
    return 'other';
  }

  function normalizeGraph(graph) {
    const nodes = Array.isArray(graph && graph.nodes) ? graph.nodes : [];
    const nodeIds = new Set(nodes.map(node => clean(node && node.id)).filter(Boolean));
    const seen = new Set();
    const relations = [];
    for (const source of (Array.isArray(graph && graph.relations) ? graph.relations : [])) {
      const from = sourceId(source), to = targetId(source), id = relationId(source);
      if (!id || !from || !to || !nodeIds.has(from) || !nodeIds.has(to) || seen.has(id)) continue;
      seen.add(id);
      relations.push(Object.assign({}, source, {id, from, to, type: relationType(source), chainType: classifyRelation(source)}));
    }
    relations.sort((a, b) => `${a.type}\u0000${a.id}\u0000${a.to}`.localeCompare(`${b.type}\u0000${b.id}\u0000${b.to}`));
    return {nodes, nodeIds, relations};
  }

  function adjacency(graph, chainType) {
    const normalized = normalizeGraph(graph), bySource = new Map();
    for (const relation of normalized.relations) {
      if (chainType && relation.chainType !== chainType) continue;
      const list = bySource.get(relation.from) || [];
      list.push(relation);
      bySource.set(relation.from, list);
    }
    return {normalized, bySource};
  }

  function makeRoute(source, target, direction, nodeIds, edgeIds, relationById) {
    const chainTypes = [];
    for (const id of edgeIds) {
      const type = relationById.get(id).chainType;
      if (!chainTypes.includes(type)) chainTypes.push(type);
    }
    return {
      route_id: `${direction}:${source}>${target}:${edgeIds.join('|')}`,
      source_node_id: source,
      target_node_id: target,
      direction,
      node_ids: nodeIds.slice(),
      edge_ids: edgeIds.slice(),
      hop_count: edgeIds.length,
      chain_types: chainTypes,
      review_status: 'generated'
    };
  }

  function shortestPath(bySource, start, end, maxDepth, bannedEdgeIds) {
    const queue = [{node: start, depth: 0}], seen = new Set([start]), parent = new Map();
    let cursor = 0, visits = 0;
    while (cursor < queue.length && visits < MAX_VISITS) {
      const current = queue[cursor++];
      if (current.depth >= maxDepth) continue;
      for (const edge of bySource.get(current.node) || []) {
        visits++;
        if (bannedEdgeIds.has(edge.id) || seen.has(edge.to)) continue;
        seen.add(edge.to);
        parent.set(edge.to, {node: current.node, edge: edge.id});
        if (edge.to === end) {
          const nodeIds = [end], edgeIds = [];
          let node = end;
          while (node !== start) {
            const step = parent.get(node);
            if (!step) return null;
            edgeIds.unshift(step.edge); nodeIds.unshift(step.node); node = step.node;
          }
          return {nodeIds, edgeIds};
        }
        queue.push({node: edge.to, depth: current.depth + 1});
        if (visits >= MAX_VISITS) break;
      }
    }
    return null;
  }

  function routeCandidates(graph, source, target, options) {
    const settings = Object.assign({direction: 'forward', chainType: '', maxSearchDepth: MAX_SEARCH_DEPTH, maxCandidates: MAX_CANDIDATES}, options || {});
    const start = clean(source), end = clean(target);
    const {normalized, bySource} = adjacency(graph, settings.chainType);
    if (!start || !end || start === end || !normalized.nodeIds.has(start) || !normalized.nodeIds.has(end)) return [];
    const relationById = new Map(normalized.relations.map(relation => [relation.id, relation]));
    const first = shortestPath(bySource, start, end, settings.maxSearchDepth, new Set());
    if (!first) return [];
    const found = [makeRoute(start, end, settings.direction, first.nodeIds, first.edgeIds, relationById)];
    const seenRoutes = new Set([first.edgeIds.join('|')]), bans = first.edgeIds.map(id => new Set([id]));
    let cursor = 0;
    while (cursor < bans.length && found.length < settings.maxCandidates) {
      const banned = bans[cursor++], alternative = shortestPath(bySource, start, end, settings.maxSearchDepth, banned);
      if (!alternative) continue;
      const key = alternative.edgeIds.join('|');
      if (seenRoutes.has(key)) continue;
      seenRoutes.add(key);
      found.push(makeRoute(start, end, settings.direction, alternative.nodeIds, alternative.edgeIds, relationById));
      for (const id of alternative.edgeIds) {
        const next = new Set(banned); next.add(id); bans.push(next);
      }
    }
    return found.sort((a, b) => a.hop_count - b.hop_count || a.route_id.localeCompare(b.route_id));
  }

  function twoEndpointCandidates(graph, source, target) {
    const forward = routeCandidates(graph, source, target, {direction: 'forward'});
    const reverse = routeCandidates(graph, target, source, {direction: 'reverse'});
    return {forward, reverse};
  }

  function routeSegments(route, maxDepth) {
    const depth = Math.max(1, Math.min(MAX_DEPTH_PER_CHAIN, Number(maxDepth) || MAX_DEPTH_PER_CHAIN));
    const segments = [];
    for (let offset = 0; offset < route.edge_ids.length; offset += depth) {
      segments.push({
        index: segments.length,
        node_ids: route.node_ids.slice(offset, offset + depth + 1),
        edge_ids: route.edge_ids.slice(offset, offset + depth),
        hidden_before: offset,
        hidden_after: Math.max(0, route.edge_ids.length - offset - depth)
      });
    }
    return segments.length ? segments : [{index: 0, node_ids: route.node_ids.slice(0, 1), edge_ids: [], hidden_before: 0, hidden_after: 0}];
  }

  function segmentFor(route) {
    const segments = routeSegments(route, route.max_depth || MAX_DEPTH_PER_CHAIN);
    return segments[Math.max(0, Math.min(segments.length - 1, Number(route.segment_index) || 0))];
  }

  function project(graph, routes) {
    const normalized = normalizeGraph(graph), nodeIds = new Set(), edgeIds = new Set();
    const safeRoutes = (Array.isArray(routes) ? routes : []).filter(Boolean).slice(0, MAX_OPEN_CHAINS);
    for (const route of safeRoutes) {
      const segment = segmentFor(route);
      for (const id of segment.node_ids) if (normalized.nodeIds.has(id)) nodeIds.add(id);
      for (const id of segment.edge_ids) edgeIds.add(id);
    }
    const collapsedByNode = {};
    for (const relation of normalized.relations) {
      if (edgeIds.has(relation.id)) continue;
      if (nodeIds.has(relation.from)) collapsedByNode[relation.from] = (collapsedByNode[relation.from] || 0) + 1;
      if (nodeIds.has(relation.to)) collapsedByNode[relation.to] = (collapsedByNode[relation.to] || 0) + 1;
    }
    return {
      node_ids: [...nodeIds], edge_ids: [...edgeIds], collapsed_by_node: collapsedByNode,
      hidden_node_count: Math.max(0, normalized.nodes.length - nodeIds.size),
      hidden_edge_count: Math.max(0, normalized.relations.length - edgeIds.size),
      route_count: safeRoutes.length
    };
  }

  function chainSummaries(graph, rootId) {
    const root = clean(rootId), {normalized, bySource} = adjacency(graph, '');
    if (!normalized.nodeIds.has(root)) return [];
    return CHAIN_TYPES.map(type => {
      const direct = (bySource.get(root) || []).filter(edge => edge.chainType === type);
      return {chain_type: type, label: CHAIN_LABELS[type], direct_count: direct.length};
    }).filter(item => item.direct_count > 0);
  }

  function expandChain(graph, rootId, chainType, maxDepth) {
    const depth = Math.max(1, Math.min(MAX_DEPTH_PER_CHAIN, Number(maxDepth) || MAX_DEPTH_PER_CHAIN));
    const root = clean(rootId), {normalized, bySource} = adjacency(graph, chainType);
    if (!normalized.nodeIds.has(root)) return null;
    const relationById = new Map(normalized.relations.map(relation => [relation.id, relation]));
    const nodeIds = [root], edgeIds = [];
    let cursor = root;
    while (edgeIds.length < depth) {
      const next = (bySource.get(cursor) || []).find(edge => !nodeIds.includes(edge.to));
      if (!next) break;
      edgeIds.push(next.id); nodeIds.push(next.to); cursor = next.to;
    }
    return edgeIds.length ? makeRoute(root, cursor, 'forward', nodeIds, edgeIds, relationById) : null;
  }

  function restorePinnedRoutes(raw, graph) {
    let parsed;
    try { parsed = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch (_) { return []; }
    const normalized = normalizeGraph(graph), edgeIds = new Set(normalized.relations.map(edge => edge.id));
    const routes = Array.isArray(parsed && parsed.routes) ? parsed.routes : [];
    return routes.filter(route => route && Array.isArray(route.node_ids) && Array.isArray(route.edge_ids)
      && route.node_ids.length > 1 && route.node_ids.every(id => normalized.nodeIds.has(id))
      && route.edge_ids.length === route.node_ids.length - 1 && route.edge_ids.every(id => edgeIds.has(id)))
      .slice(0, MAX_OPEN_CHAINS).map(route => Object.assign({}, route, {pinned: true, review_status: 'approved'}));
  }

  function serializePinnedRoutes(routes) {
    return JSON.stringify({version: VERSION, routes: (Array.isArray(routes) ? routes : []).filter(route => route && route.pinned).slice(0, MAX_OPEN_CHAINS).map(route => ({
      route_id: clean(route.route_id), source_node_id: clean(route.source_node_id), target_node_id: clean(route.target_node_id),
      direction: clean(route.direction) || 'forward', node_ids: route.node_ids.slice(), edge_ids: route.edge_ids.slice(),
      hop_count: Number(route.hop_count) || route.edge_ids.length, chain_types: Array.isArray(route.chain_types) ? route.chain_types.slice() : [],
      segment_index: Math.max(0, Number(route.segment_index) || 0), max_depth: MAX_DEPTH_PER_CHAIN, pinned: true, review_status: 'approved'
    }))});
  }

  return Object.freeze({
    VERSION, MAX_OPEN_CHAINS, MAX_DEPTH_PER_CHAIN, MAX_SEARCH_DEPTH, CHAIN_TYPES, CHAIN_LABELS,
    classifyRelation, normalizeGraph, routeCandidates, twoEndpointCandidates, routeSegments,
    project, chainSummaries, expandChain, restorePinnedRoutes, serializePinnedRoutes
  });
});
