# 🔧 Resize - Player Size Change Plugin

**Resize** is a lightweight, fast, and powerful Minecraft plugin that allows you to **change player size** with smooth animations, flexible configuration, group-based limits, and advanced integrations.

Perfect for **fun servers, PvP, RPG, events, and mini-games**.

<img width="1101" height="1030" alt="image" src="https://github.com/user-attachments/assets/eb49d2c0-1814-4c94-bc18-1f2891de4ad4" />

---

## 📏 Changing Player Size

Command:

```
/resize <size> [player]
```

Decimal values are supported (`0.6`, `1.2`, `1.6`, etc.).

![resize_sizechange (1)](https://github.com/user-attachments/assets/4b4366c0-e06a-4213-9203-3adf045bea8b)

---

## 📊 Player Size Information

New command:

```
/resize info
/resize info <player>
```

Displays:

* Current size
* Minimum allowed size
* Maximum allowed size

---

## 🎬 Smooth Size Change Animation

You can enable smooth size transitions instead of instant changes:

```yml
animation:
  enabled: true
  duration: 1
```

![resize_gif (1)](https://github.com/user-attachments/assets/75064299-9c3d-4f4c-bb91-8332c0fff750)

Makes size changes **smooth and visually pleasing**.

---

## 👥 LuckPerms Group-Based Limits

Resize now supports **LuckPerms integration**.

You can define different size limits for each group:

```yml
group-limits:
  enabled: true
  default:
    min: 0.6
    max: 1.6
  vip:
    max: 1.4
  titan:
    min: 0.5
    max: 1.6
```

If enabled:

* The plugin automatically detects the player's **primary LuckPerms group**
* Applies group-specific min/max limits
* Falls back to global limits if a value is not defined

If disabled:

* Only global `scale.min` and `scale.max` values are used

⚠ LuckPerms is **optional** — Resize does not depend on it.

---

## 🌍 WorldGuard Integration

Resize includes a custom WorldGuard flag:

```
/rg flag <region> resize allow|deny
```

This allows you to:

* Enable resizing in specific regions
* Disable resizing in protected areas (spawn, arenas, etc.)

Perfect for balanced gameplay and server control.

⚠ WorldGuard is optional and only used if installed.

---

## ⚔ Automatic Size Reset on Damage (Highly Configurable)

A fully configurable system that automatically resets player size when:

* Receiving damage
* Dealing damage
* Dying
* PvP combat
* Mob damage
* Environmental damage

Perfect for **balanced PvP, RPG mechanics, and event gameplay**.

---

## 🎯 Global Size Limits

You can configure the minimum and maximum player size:

```yml
scale:
  min: 0.6
  max: 1.6
```

These values act as:

* Default limits
* Fallback values for group limits
* Tab-completion suggestions

---

## 👑 Administrators

Administrators can:

* Change the size of other players
* Ignore size limits
* Bypass cooldowns
* Be protected from automatic size resets
* See unlimited size range in `/resize info`

---

## ⏱ Anti-Spam Protection

Built-in cooldown system prevents command spam.

---

## 💾 Size Persistence

```yml
save-sizes: true
```

* `true` — Player size is saved after rejoining
* `false` — Player size resets to `1.0` on join

---

## 🌐 Multi-Language Support

```
/plugins/Resize/lang/
  en.yml
  ru.yml
  es.yml
  de.yml
  pl.yml
  pt_br.yml
```

Available languages:

* English
* Russian
* German
* Spanish
* Polish
* Brazilian Portuguese

Supports **HEX color codes** in config and language files.

---

## 🔄 Configuration Reload

Reload configuration and language files without restarting the server:

```
/resize reload
```

---

## 🔐 Permissions

| Permission            | Description |
|----------------------|------------|
| `resize.resize`      | Allows using `/resize` |
| `resize.info`        | Allows using `/resize info` |
| `resize.info.other`  | Allows viewing size information of other players |
| `resize.reload`      | Allows reloading the plugin configuration |
| `resize.admin`       | Removes all restrictions (no cooldowns, no limits, no auto-reset, full bypass) |
| `resize.*`           | Grants all Resize permissions |

---

## 🔌 Optional Dependencies

* LuckPerms (for group-based limits)
* WorldGuard (for region resize control)

The plugin works perfectly without them.
