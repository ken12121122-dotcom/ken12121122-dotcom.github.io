# Amin Prompt Keyboard v0.1

Status: candidate implementation on `agent/amin-prompt-keyboard-v01`.

## Verified product contract

```text
Select text in another Android app
→ tap "存到提示詞"
→ choose a category
→ save the exact selected text locally
→ switch to Amin 提示詞鍵盤
→ choose the saved prompt
→ commit the exact text into the focused editor
```

## Native components

- `PromptCaptureActivity`: Android `ACTION_PROCESS_TEXT` entry and category picker.
- `PromptStore`: package-private SQLite store shared by the app and IME process.
- `PromptKeyboardService`: Android `InputMethodService` prompt keyboard.
- `PromptKeyboardSetupActivity`: explicit system enable/select guidance.
- `prompt_input_method.xml`: IME subtype declaration.

## Data rules

- Selected content is stored exactly; display previews may collapse whitespace and truncate.
- Empty selections are rejected.
- Unknown categories fall back to `inbox`.
- Initial categories are `收件匣`, `工作`, `創作`, and `研究`.
- Data stays in the local `amin_prompts.db` database.
- Password editors do not display saved prompts.

## Release boundary

This is an Android native feature. It requires a new Bridge APK and cannot ship through the Runtime hot-update channel. The formal native release manifest must not change until CI, emulator acceptance, physical-device acceptance, and explicit release approval are complete.

The `promptPreview` build type uses package `com.amin.pocketgba.promptpreview` and the normal Android debug signer. It can be installed beside Bridge 23 for acceptance testing and is never a formal update source.

## Acceptance cases

1. `ACTION_PROCESS_TEXT` resolves to `PromptCaptureActivity` for `text/plain`.
2. Category selection preserves the exact input string in SQLite.
3. Reopening the IME refreshes the prompt list without restarting Amin.
4. Tapping a prompt calls `InputConnection.commitText` with the exact stored content.
5. Password fields reveal no prompt content.
6. The switch-keyboard control returns to another enabled IME.
7. Existing GBA, controller, update, accessibility, voice, and Control API tests remain green.
