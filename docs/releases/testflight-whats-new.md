Summary

- Fixed inline code in rendered messages showing larger than body text
- Composer autocorrect/smart quotes/spell check now follow iPhone Settings

What to test

- Inline code rendering: open a conversation where the assistant responded with inline backtick code (e.g. a file path like `apps/ios/...`). In the rendered message bubble, the code should appear at the same visual size as the surrounding body text — not visibly larger. Try both mono and system font modes in Appearance.
- Composer keyboard behavior: go to Settings → General → Keyboard and toggle Auto-Correction, Check Spelling, Smart Punctuation, and Auto-Capitalization. The conversation and home composers should match those toggles (autocorrect suggestions, red spelling underlines, and `"` → `"` / `--` → `—` conversion should all follow the system setting).
