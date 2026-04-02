# Adding More Modules (1.21)

Fish Client now supports **three ways** to add modules:

1. Built-in code modules
- Register in `com.fishclient.module.core.ModuleRegistry#registerBuiltinDefaults`.

2. External JSON modules (no code)
- Put `.json` files in `config/fishclient/modules/`.
- An example file is auto-created at first launch: `_example_module.json`.
- Example:
```json
{
  "id": "my_extra_module",
  "name": "My Extra Module",
  "description": "Loaded from config",
  "category": "MISC",
  "enabledByDefault": false
}
```

3. Provider modules from another mod jar
- Implement `com.fishclient.module.api.ModuleProvider` in your mod.
- Register it via Java `ServiceLoader` (`META-INF/services/com.fishclient.module.api.ModuleProvider`).
- Fish Client auto-loads providers at startup.

Hotkeys:
- `Right Shift`: Open Fish Client menu
- `F9`: Reload external JSON modules
