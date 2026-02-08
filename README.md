# 🔧 Resize - Player Size Change Plugin

**Resize** is a lightweight, fast, and powerful Minecraft plugin that allows you to **change player size** with smooth animations, flexible configuration, and a well-designed permission system.

Perfect for **fun servers, PvP, RPG, events, and mini-games**.

---

## 📏 Changing Player Size

Command:
```

/resize <size> [player]

````

Decimal values are supported (`0.6`, `1.2`, `1.6`, etc.).

---

## 🎬 Smooth Size Change Animation

You can enable smooth size transitions instead of instant changes:

```yml
animation:
  enabled: true
  duration: 1
````

Makes size changes **smooth and visually pleasing**.

---

## ⚔ Automatic Size Reset on Damage (Highly Configurable)

A fully configurable system that automatically resets player size when:

* receiving damage
* dealing damage
* dying
* PvP combat, mob damage, or environmental damage

Perfect for **balanced PvP, RPG mechanics, and event gameplay**.

---

## 👑 Administrators

Administrators can:

* change the size of other players
* ignore size limits
* bypass cooldowns
* be protected from automatic size resets

Everything is fully configurable.

---

## 🎯 Size Limits

You can configure the minimum and maximum player size:

```yml
scale:
  min: 0.6
  max: 1.6
```

---

## ⏱ Anti-Spam Protection

Built-in cooldown system to prevent command spam.

---

## 💾 Size Persistence

```yml
save-sizes: true
```

* `true` — player size is saved after rejoining
* `false` — player size is always reset to `1.0` on join

---

## 🌍 Multi-Language Support

```
/plugins/Resize/lang/
  en.yml
  ru.yml
  es.yml
  de.yml
  pl.yml
  pt_br.yml
```

The plugin is localized in the following languages:

* English
* Russian
* German
* Spanish
* Polish
* Brazilian Portuguese

---

## 🔄 Configuration Reload

Reload configuration and language files without restarting the server:

```
/resize reload
```

---

## 🔐 Permissions

| Permission      | Description                                                            |
| --------------- | ---------------------------------------------------------------------- |
| `resize.resize` | Allows using the `/resize` command                                     |
| `resize.admin`  | Removes all restrictions (no cooldowns, no size limits, no auto-reset) |
| `resize.reload` | Allows reloading the plugin configuration                              |

---
