'use strict';

const fs = require('fs');
const path = require('path');
const Parser = require('tree-sitter');
const Java = require('tree-sitter-java');

const repoRoot = path.resolve(process.argv[2] || '.');
const scannerPath = 'android-native/app/src/main/java/com/amin/pocketgba/GitHubSourceGraphScanner.java';
const callerPath = 'android-native/app/src/main/java/com/amin/pocketgba/WikiGraphActivity.java';

const parser = new Parser();
parser.setLanguage(Java);

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
  const tree = parser.parse(source);
  let found = null;
  walk(tree.rootNode, (node) => {
    if (found || node.type !== 'method_declaration') return;
    if (fieldText(node, 'name', source) === methodName) found = node;
  });
  return { tree, node: found };
}

function findInvocation(source, ownerName, methodName) {
  const tree = parser.parse(source);
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

const scannerSource = read(scannerPath);
const callerSource = read(callerPath);
const scannerMethod = findMethod(scannerSource, 'syncAsync');
const invocation = findInvocation(callerSource, 'GitHubSourceGraphScanner', 'syncAsync');

if (!scannerMethod.node) throw new Error('TREE_SITTER_SCANNER_METHOD_NOT_FOUND');
if (!invocation.node) throw new Error('TREE_SITTER_SCANNER_CALL_NOT_FOUND');
if (!invocation.enclosingMethod) throw new Error('TREE_SITTER_CALLER_METHOD_NOT_FOUND');

const callerMethodName = fieldText(invocation.enclosingMethod, 'name', callerSource);
if (!callerMethodName) throw new Error('TREE_SITTER_CALLER_METHOD_NAME_MISSING');

const revision = process.env.GITHUB_SHA || '';
const output = {
  format: 'amin-indexer-evidence',
  version: 1,
  provider: 'tree-sitter-java',
  providerVersion: require('tree-sitter-java/package.json').version,
  parserVersion: require('tree-sitter/package.json').version,
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
  gaps: [
    {
      after: `function:WikiGraphActivity.${callerMethodName}`,
      expectedLayer: 'command',
      code: 'SCANNER_COMMAND_EVIDENCE_NOT_FOUND',
      reason: 'Tree-sitter proves the Java caller relation only; it does not prove formal COMMAND ownership.'
    }
  ]
};

process.stdout.write(JSON.stringify(output, null, 2) + '\n');
