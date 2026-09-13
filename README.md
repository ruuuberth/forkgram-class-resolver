# Forkgram Class Resolver

Structural runtime discovery for Forkgram/Telegram-derived Android clients.

## Goal

Discover the runtime class that owns the channel/update processing method without depending on a static obfuscation map.

The resolver combines multiple structural fingerprints and refuses to select an ambiguous candidate.

## Initial design

1. Enumerate classes available to the target `ClassLoader` within Telegram/Forkgram namespaces.
2. Find methods matching the known update-processor shape: five parameters with three list-like arguments followed by `boolean` and `int`.
3. Score the owning class using additional structural fingerprints.
4. Require a unique high-confidence candidate instead of blindly selecting the first match.
5. Expose a diagnostic discovery mode so the exact Forkgram build can be inspected before installing hooks.

This project is intentionally independent from TeleVip-LSPosed. TeleVip is used as a reference for client-specific resolution, while this project experiments with a map-free structural resolver.

## Status

Early implementation. The first milestone is discovery and diagnostics against the user's exact Forkgram runtime. Hooking and message filtering will be added only after candidate discovery is validated.

## License

GPL-3.0-or-later.
