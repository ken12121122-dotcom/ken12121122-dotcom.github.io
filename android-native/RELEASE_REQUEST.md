# Android Production Release Request

release_status: requested
versionName: 0.10.26
versionCode: 154
channel: bridge
bridge: 58
scope: voice orb launcher fix + floating chat UI test path
notes:
  - restore LaunchGateActivity as app startup path
  - keep legacy VoiceOrb/VoiceCommand components but disable automatic takeover
  - keep FloatingVoiceController chat UI test path
  - keep legacy voice routing disabled
  - no real LLM API in this release
