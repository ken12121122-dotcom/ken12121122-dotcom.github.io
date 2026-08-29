'use strict';

const fs = require('fs');
const path = require('path');
const Parser = require('tree-sitter');
const Java = require('tree-sitter-java');
const toolPackage = require('./package.json');

const repoRoot = path.resolve(process.argv[2] || '.');
const javaRoot = 'android-native/app/src/main/java/com/amin/pocketgba';
const scannerPath = `${javaRoot}/GitHubSourceGraphScanner.java`;
const callerPath = `${javaRoot}/WikiGraphActivity.java`;

const architectureLayers = [
  { layer: 'command', className: 'VoiceCommandCatalog', path: `${javaRoot}/VoiceCommandCatalog.java` },
  { layer: 'capability', className: 'CapabilitySourceMap', path: `${javaRoot}/CapabilitySourceMap.java` },
  { layer: 'registry', className: 'NodeRegistry', path: `${javaRoot}/NodeRegistry.java` },
  { layer: 'governance', className: 'RegistryApprovalActivity', path: `${javaRoot}/RegistryApprovalActivity.java` },
  { layer: 'permission', className: 'PermissionCenterActivity', path: `${javaRoot}/PermissionCenterActivity.java` }
];

function parseJava(source) {
  const parser = new Parser();
  parser.setLanguage(Java);
  return parser.parse(source);
}

function read(relative) {
  return fs.readFileSync(path.join(repoRoot, relative), 'utf8');
}

function text(node, source) {
  return source.slice(node.startIndex, node.endIndex);
}

function walk(node, visit, ancestors = []) {
  visit(node, ancestors);
  const next = ancestors.concat(node);
  for (let i = 0; i < node.namedChildCount; i++) walk(node.namedChild(i), visit, next);
}

function fieldText(node, field, source) {
  const child = node.childForFieldName(field);
  return child ? text(child, source) : '';
}

function findMethod(source, methodName) {
  const tree = parseJava(source);
  let found = null;
  walk(tree.rootNode, (node) => {
    if (found || node.type !== 'method_declaration') return;
    if (fieldText(node, 'name', source) === methodName) found = node;
  });
  return { tree, node: found };
}

function findClass(source, className) {
  const tree = parseJava(source);
  let found = null;
  walk(tree.rootNode, (node) => {
    if (found || (node.type !== 'class_declaration' && node.type !== 'interface_declaration' && node.type !== 'enum_declaration')) return;
    if (fieldText(node, 'name', source) === className) found = node;
  });
  return { tree, node: found };
}

function findInvocation(source, ownerName, methodName) {
  const tree = parseJava(source);
  let found = null;
  let enclosingMethod = null;
  walk(tree.rootNode, (node, ancestors) => {
    if (found || node.type !== 'method_invocation') return;
    const name = fieldText(node, 'name', source);
    const object = fieldText(node, 'object', source);
    if (name !== methodName || object !== ownerName) return;
    found = node;
    for (let i = ancestors.length - 1; i >= 0; i--) {
      if (ancestors[i].type === 'method_declaration') {
        enclosingMethod = ancestors[i];
        break;
      }
    }
  });
  return { tree, node: found, enclosingMethod };
}

function javaFiles(dir) {
  const absolute = path.join(repoRoot, dir);
  const out = [];
  for (const entry of fs.readdirSync(absolute, { withFileTypes: true })) {
    const relative = `${dir}/${entry.name}`;
    if (entry.isDirectory()) out.push(...javaFiles(relative));
    else if (entry.isFile() && entry.name.endsWith('.java')) out.push(relative);
  }
  return out;
}

const allJavaFiles = javaFiles(javaRoot);
const referenceScanErrors = [];

function sourceClassFor(relative) {
  return path.basename(relative, '.java');
}

function identifierReferences(className) {
  const refs = [];
  for (const relative of allJavaFiles) {
    const source = read(relative);
    if (!source.includes(className)) continue;
    let tree;
    try {
      tree = parseJava(source);
    } catch (error) {
      referenceScanErrors.push({ path: relative, className, error: error.message || error.name || 'parse_error' });
      continue;
    }
    walk(tree.rootNode, (node) => {
      if (node.type !== 'identifier' && node.type !== 'type_identifier') return;
      if (text(node, source) !== className) return;
      refs.push({
        path: relative,
        sourceClass: sourceClassFor(relative),
        nodeType: node.type,
        startIndex: node.startIndex,
        endIndex: node.endIndex,
        startPosition: node.startPosition,
        endPosition: node.endPosition
      });
    });
  }
  return refs;
}

const scannerSource = read(scannerPath);
const callerSource = read(callerPath);
const scannerMethod = findMethod(scannerSource, 'syncAsync');
const invocation = findInvocation(callerSource, 'GitHubSourceGraphScanner', 'syncAsync');

if (!scannerMethod.node) throw new Error('TREE_SITTER_SCANNER_METHOD_NOT_FOUND');
if (!invocation.node) throw new Error('TREE_SITTER_SCANNER_CALL_NOT_FOUND');
if (!invocation.enclosingMethod) throw new Error('TREE_SITTER_CALLER_METHOD_NOT_FOUND');

const callerMethodName = fieldText(invocation.enclosingMethod, 'name', callerSource);
if (!callerMethodName) throw new Error('TREE_SITTER_CALLER_METHOD_NAME_MISSING');

const artifacts = architectureLayers.map((candidate) => {
  const source = read(candidate.path);
  const declaration = findClass(source, candidate.className);
  if (!declaration.node) throw new Error(`TREE_SITTER_LAYER_DECLARATION_NOT_FOUND:${candidate.layer}`);
  const references = identifierReferences(candidate.className)
    .filter((ref) => !(ref.path === candidate.path && ref.startIndex >= declaration.node.startIndex && ref.endIndex <= declaration.node.endIndex));
  return {
    layer: candidate.layer,
    entityId: `source-artifact:${candidate.className}`,
    className: candidate.className,
    path: candidate.path,
    verification: 'ast_verified',
    authorityVerified: false,
    declaration: {
      nodeType: declaration.node.type,
      startIndex: declaration.node.startIndex,
      endIndex: declaration.node.endIndex,
      startPosition: declaration.node.startPosition,
      endPosition: declaration.node.endPosition
    },
    references
  };
});

const byClass = new Map(artifacts.map((artifact) => [artifact.className, artifact]));
const structuralRelationKeys = new Set();
const structuralRelations = [];
for (const target of artifacts) {
  for (const ref of target.references) {
    const source = byClass.get(ref.sourceClass);
    if (!source || source.className === target.className) continue;
    const key = `${source.entityId}>${target.entityId}`;
    if (structuralRelationKeys.has(key)) continue;
    structuralRelationKeys.add(key);
    structuralRelations.push({
      relationId: `relation:references:${source.entityId}>${target.entityId}`,
      from: source.entityId,
      to: target.entityId,
      type: 'references',
      verification: 'ast_verified',
      authorityVerified: false,
      evidence: {
        provider: 'tree-sitter-java',
        path: ref.path,
        sourceClass: ref.sourceClass,
        targetClass: target.className,
        nodeType: ref.nodeType,
        startIndex: ref.startIndex,
        endIndex: ref.endIndex,
        startPosition: ref.startPosition,
        endPosition: ref.endPosition
      }
    });
  }
}

const revision = process.env.GITHUB_SHA || '';
const output = {
  format: 'amin-indexer-evidence',
  version: 1,
  provider: 'tree-sitter-java',
  providerVersion: toolPackage.dependencies['tree-sitter-java'],
  parserVersion: toolPackage.dependencies['tree-sitter'],
  revision,
  anchor: {
    entityId: 'function:GitHubSourceGraphScanner.syncAsync',
    kind: 'function',
    title: 'Scanner · syncAsync',
    verification: 'ast_verified',
    evidence: {
      provider: 'tree-sitter-java',
      path: scannerPath,
      nodeType: scannerMethod.node.type,
      startIndex: scannerMethod.node.startIndex,
      endIndex: scannerMethod.node.endIndex,
      startPosition: scannerMethod.node.startPosition,
      endPosition: scannerMethod.node.endPosition,
      symbol: 'GitHubSourceGraphScanner.syncAsync'
    }
  },
  relations: [
    {
      relationId: `relation:invoked_by:function:GitHubSourceGraphScanner.syncAsync>function:WikiGraphActivity.${callerMethodName}`,
      from: 'function:GitHubSourceGraphScanner.syncAsync',
      to: `function:WikiGraphActivity.${callerMethodName}`,
      type: 'invoked_by',
      verification: 'ast_verified',
      evidence: {
        provider: 'tree-sitter-java',
        path: callerPath,
        nodeType: invocation.node.type,
        callerNodeType: invocation.enclosingMethod.type,
        callerSymbol: `WikiGraphActivity.${callerMethodName}`,
        calleeSymbol: 'GitHubSourceGraphScanner.syncAsync',
        startIndex: invocation.node.startIndex,
        endIndex: invocation.node.endIndex,
        startPosition: invocation.node.startPosition,
        endPosition: invocation.node.endPosition
      }
    }
  ],
  artifacts,
  structuralRelations,
  referenceScanErrors,
  gaps: [
    {
      after: `function:WikiGraphActivity.${callerMethodName}`,
      expectedLayer: 'command',
      code: 'SCANNER_COMMAND_EVIDENCE_NOT_FOUND',
      reason: 'Tree-sitter proves Java caller and structural references only; structural references are not formal COMMAND or authority ownership.'
    }
  ]
};

process.stdout.write(JSON.stringify(output, null, 2) + '\n');
