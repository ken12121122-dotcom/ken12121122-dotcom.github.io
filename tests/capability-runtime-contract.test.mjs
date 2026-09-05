import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import test from 'node:test';

const root = new URL('../android-native/app/src/main/java/com/amin/pocketgba/', import.meta.url);
const contract = readFileSync(new URL('CapabilityGraphContract.java', root), 'utf8');
const projector = readFileSync(new URL('CapabilityInventoryProjector.java', root), 'utf8');
const provider = readFileSync(new URL('UnifiedGraphProvider.java', root), 'utf8');
const canvas = readFileSync(new URL('../android-native/app/src/main/assets/amin-wiki-graph/index.html', import.meta.url), 'utf8');

test('Step 11 reuses the shared kernel and existing Unified Graph projection', () => {
  assert.match(contract, /SharedGraphSyncKernel\.BATCH_FORMAT/);
  assert.match(contract, /put\("graph_scope", "work"\)/);
  assert.match(provider, /CapabilityInventoryStore/);
  assert.match(provider, /CapabilityInventoryProjector\.append/);
  assert.match(provider, /amin-dynamic-canvas-only/);
  assert.doesNotMatch(contract + projector, /createElement.*canvas|new Canvas|<canvas/);
});

test('Step 11 first slice cannot execute or raise autonomy', () => {
  assert.match(contract, /put\("autonomy_level", "none"\)/);
  assert.match(contract, /put\("execution_enabled", false\)/);
  assert.match(projector, /put\("enabled", false\)/);
  assert.doesNotMatch(contract + projector, /Runtime\.getRuntime|ProcessBuilder|workflow_dispatch|mergePullRequest|createRelease/);
});

test('Capability certification requires evidence and OWNER approval', () => {
  assert.match(contract, /CERTIFICATION_REQUIRES_APPROVAL_AND_EVIDENCE/);
  assert.match(contract, /certification_evidence/);
  assert.match(contract, /human_approval/);
  assert.match(contract, /review_status/);
  assert.match(contract, /certification_scope/);
  assert.match(contract, /trust_expires_at/);
  assert.match(contract, /CERTIFICATION_SCOPE_REQUIRED/);
  assert.match(contract, /certified_by/);
});

test('Capability governance stays a read-only manager inside the single Canvas', () => {
  assert.match(canvas, /id="capabilityManager"/);
  assert.match(canvas, /CAPABILITY 治理 · 唯讀/);
  assert.match(canvas, /不可執行、不可自動認證、不可提升 autonomy/);
  assert.match(canvas, /capabilityTypeFilter/);
  assert.match(canvas, /capabilityLifecycleFilter/);
  assert.match(canvas, /capabilityCertificationFilter/);
  assert.match(canvas, /<option>COMMAND<\/option>/);
  assert.match(canvas, /來源紀錄：/);
  assert.match(canvas, /effectiveCertification/);
  assert.match(canvas, /unsafeCapabilityCount/);
  assert.match(canvas, /graph\?\.capabilityInventory\?\.entities/);
  assert.match(canvas, /focusCapabilityNode\(capability\.dataset\.capabilityNode\)/);
  assert.match(canvas, /prepareFirstFocusRoute:\(\)=>\{const edge=focusGraph\(\)\.relations\[0\]/);
  assert.doesNotMatch(canvas, /id="executeCapability"|id="approveCapability"|id="certifyCapability"/);
});

test('P0 conversational capability entry remains read-only', () => {
  const conversation = readFileSync(new URL('ConversationalCapabilityRuntime.java', root), 'utf8');
  const contextBuilder = readFileSync(new URL('ReadOnlyCapabilityContextBuilder.java', root), 'utf8');
  const resolver = readFileSync(new URL('CapabilityResolver.java', root), 'utf8');
  const voiceOrb = readFileSync(new URL('VoiceOrbHomeActivity.java', root), 'utf8');
  const floatingVoice = readFileSync(new URL('FloatingVoiceController.java', root), 'utf8');
  assert.match(conversation, /CapabilityResolver\.resolve/);
  assert.match(contextBuilder, /CapabilityInventoryStore/);
  assert.match(resolver, /existing_capability/);
  assert.match(resolver, /repository_implementation/);
  assert.match(voiceOrb, /ConversationalCapabilityRuntime\.resolve/);
  assert.match(floatingVoice, /ConversationalCapabilityRuntime\.resolve/);
  assert.doesNotMatch(conversation + contextBuilder + resolver,
    /AminActionDispatcher|createCustomNode|workflow_dispatch|mergePullRequest|createRelease|ProcessBuilder|Runtime\.getRuntime/);
});

test('bridge inventory contains 43 capabilities and every built-in Node has Markdown', () => {
  const mainManifest = readFileSync(new URL('../android-native/app/src/main/AndroidManifest.xml', import.meta.url), 'utf8');
  const bridgeManifest = readFileSync(new URL('../android-native/app/src/bridge/AndroidManifest.xml', import.meta.url), 'utf8');
  const registry = readFileSync(new URL('NodeRegistry.java', root), 'utf8');
  const commands = readFileSync(new URL('VoiceCommandCatalog.java', root), 'utf8');
  const visible = (mainManifest.match(/amin\.graph\.visible" android:value="true"/g) || []).length
    + (bridgeManifest.match(/amin\.graph\.visible" android:value="true"/g) || []).length;
  const virtualPageTemplates = (registry.match(/pages\.put\(virtualPage\(/g) || []).length;
  const commandCount = (commands.match(/commands\.add\(command\(/g) || []).length;
  assert.equal(1 + visible + (virtualPageTemplates - 1) + commandCount, 43);
  const manifestIds = [...mainManifest.matchAll(/amin\.graph\.id" android:value="([^"]+)"/g),
    ...bridgeManifest.matchAll(/amin\.graph\.id" android:value="([^"]+)"/g)].map(match => match[1]);
  const virtualIds = [...registry.matchAll(/pages\.put\(virtualPage\("([^"]+)"/g)]
    .map(match => match[1]).filter(id => id !== 'fox-chat-md');
  const expectedMd = [...manifestIds, ...virtualIds].sort();
  const actualMd = readdirSync(new URL('../android-native/app/src/main/assets/node-context/', import.meta.url))
    .filter(name => name.endsWith('.md')).map(name => name.slice(0, -3)).sort();
  assert.deepEqual(actualMd, expectedMd);
  assert.equal(actualMd.length, 25);
  const bom = readFileSync(new URL('../android-native/docs/CAPABILITY_BOM.md', import.meta.url), 'utf8');
  assert.match(bom, /Existing Capability total \| 43/);
  assert.match(bom, /Chat-addressable \| 42/);
  assert.match(bom, /Bridge required \| 1/);
  assert.match(bom, /Roadmap-only \/ not implemented \| 0/);
  assert.match(bom, /`app:app-core`.*Bridge required/);
});

test('Fox chat node reads its managed Markdown through the existing graph relation', () => {
  const bridgeManifest = readFileSync(new URL('../android-native/app/src/bridge/AndroidManifest.xml', import.meta.url), 'utf8');
  const registry = readFileSync(new URL('NodeRegistry.java', root), 'utf8');
  const contextBuilder = readFileSync(new URL('NodeMdContextBuilder.java', root), 'utf8');
  const foxContext = readFileSync(new URL('FoxConversationContextBuilder.java', root), 'utf8');
  const managedMd = readFileSync(new URL('ManagedNodeMdStore.java', root), 'utf8');
  const metadataStore = readFileSync(new URL('NodeMetadataStore.java', root), 'utf8');
  const llm = readFileSync(new URL('LlmClient.java', root), 'utf8');
  const voiceOrb = readFileSync(new URL('VoiceOrbHomeActivity.java', root), 'utf8');
  const floatingVoice = readFileSync(new URL('FloatingVoiceController.java', root), 'utf8');
  const foxMd = readFileSync(new URL('../android-native/app/src/main/assets/node-context/fox-chat.md', import.meta.url), 'utf8');
  assert.match(bridgeManifest, /amin\.graph\.title" android:value="狐狸"/);
  assert.match(bridgeManifest, /context_node_id.*app:fox-chat-md/);
  assert.match(registry, /"edge:"\+rawId\+":reads-context"/);
  assert.match(registry, /"reads_from"/);
  assert.match(contextBuilder, /NodeRegistry\.typedEdgesJson/);
  assert.match(contextBuilder, /asset_md/);
  assert.match(contextBuilder, /MAX_CONTEXT_BYTES/);
  assert.match(foxContext, /MAX_SELECTED_NODES = 3/);
  assert.match(foxContext, /NodeMdContextBuilder\.build/);
  assert.match(managedMd, /review_status: generated/);
  assert.match(metadataStore, /ManagedNodeMdStore\.createDraft/);
  assert.match(metadataStore, /"internal_md"/);
  assert.match(metadataStore, /"reads_from"/);
  assert.match(llm, /systemInstruction/);
  assert.match(llm, /body\.put\("system", systemContext\)/);
  assert.match(voiceOrb, /FoxConversationContextBuilder\.systemContext/);
  assert.match(floatingVoice, /FoxConversationContextBuilder\.systemContext/);
  assert.match(foxMd, /# 狐狸/);
  assert.match(foxMd, /read_only: true/);
  assert.doesNotMatch(contextBuilder + foxContext + managedMd, /AminActionDispatcher|workflow_dispatch/);
});

test('Fox desktop pet remains a presentation of the existing voice runtime', () => {
  const voice = readFileSync(new URL('FloatingVoiceController.java', root), 'utf8');
  const bridge = readFileSync(new URL('FoxPresentationBridge.java', root), 'utf8');
  const overlay = readFileSync(new URL('FoxPetOverlayService.java', root), 'utf8');
  const control = readFileSync(new URL('FoxPetControlActivity.java', root), 'utf8');
  const registry = readFileSync(new URL('NodeRegistry.java', root), 'utf8');
  assert.match(bridge, /RuntimeListener/);
  assert.match(bridge, /FoxPetOverlayService\.ACTION_UPDATE/);
  assert.match(overlay, /FoxPresentationBridge\.requestListening/);
  assert.match(overlay, /FoxPetPreferences\.isAutoSpeakEnabled/);
  assert.match(voice, /FoxPresentationBridge\.VisualState\.LISTENING/);
  assert.match(voice, /FoxPresentationBridge\.VisualState\.THINKING/);
  assert.match(voice, /FoxPresentationBridge\.VisualState\.TALKING/);
  assert.match(voice, /FoxPresentationBridge\.VisualState\.SITTING/);
  assert.match(voice, /FoxPresentationBridge\.VisualState\.SLEEPING/);
  assert.match(voice, /reply, true/);
  assert.match(control, /MODE_VOICE_BALL/);
  assert.match(control, /MODE_FOX/);
  assert.match(control, /MODE_HIDDEN/);
  assert.match(registry, /"fox-desktop-pet","app:fox-desktop-pet"/);
  assert.doesNotMatch(bridge + overlay,
    /SpeechRecognizer|VoiceCommandParser|AminActionDispatcher|CapabilityResolver|LlmClient/);
});

test('P0 contract documents the existing-first resolution policy and deferred boundaries', () => {
  const p0 = readFileSync(new URL('../android-native/docs/STEP_11_P0_CONVERSATIONAL_CAPABILITY_ENTRY.md', import.meta.url), 'utf8');
  assert.match(p0, /existing capability/);
  assert.match(p0, /repository implementation/);
  assert.match(p0, /reusable GitHub implementation/);
  assert.match(p0, /small internal implementation/);
  assert.match(p0, /API \/ MCP \/ Connector/);
  assert.match(p0, /P0 executes only step 1/);
  assert.match(p0, /execution_allowed: false/);
  assert.match(p0, /self_extension_allowed: false/);
});
