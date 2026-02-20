# 🔧 Resize - Player & Mob Size Change Plugin

**Resize** is a lightweight, fast, and powerful Minecraft plugin that allows you to **change player and mob size** with smooth animations, flexible configuration, group-based limits, and advanced integrations.

Perfect for **fun servers, PvP, RPG, events, and mini-games**.

<img width="1101" height="1030" alt="image" src="https://github.com/user-attachments/assets/eb49d2c0-1814-4c94-bc18-1f2891de4ad4" />

---

## 📏 Changing Player Size

Command:

```
/resize <size> [player]
```

Decimal values are supported (`0.6`, `1.2`, `1.6`, etc.).

![resize\_sizechange (1)](https://github.com/user-attachments/assets/4b4366c0-e06a-4213-9203-3adf045bea8b)

---

## 🐄 Changing Mob Size

Command:

```
/resize mob <size>
```

To change a mob's size:

1. Look at the mob
2. Execute the command
3. The mob's size will change (respecting limits and distance)

Features:

* Configurable maximum targeting distance
* Supports global and group-based limits
* Supports smooth animation
* Fully compatible with WorldGuard regions
* Works on both Spigot and Paper

---

## 📊 Size Information

Commands:

```
/resize info
/resize info <player>
```

Displays:

* Current size
* Minimum allowed player size
* Maximum allowed player size
* Minimum allowed mob size
* Maximum allowed mob size

---

## 🎬 Smooth Size Change Animation

You can enable smooth size transitions instead of instant changes:

```yml
animation:
  enabled: true
  duration: 1
```

![resize\_gif (1)](https://github.com/user-attachments/assets/75064299-9c3d-4f4c-bb91-8332c0fff750)

Makes size changes **smooth and visually pleasing** (for both players and mobs).

---

## 👥 LuckPerms Group-Based Limits

Resize supports **LuckPerms integration**.

You can define different limits for each group:

```yml
group-limits:
  enabled: true
  default:
    min: 0.6
    max: 1.6
    mob_min: 0.5
    mob_max: 2.0
  vip:
    max: 1.4
  titan:
    min: 0.5
    max: 1.6
    mob_max: 3.0
```

If enabled:

* The plugin automatically detects the player’s **primary LuckPerms group**
* Applies group-specific limits
* Falls back to global values if not defined

If disabled:

* Only global `scale` values are used

⚠ LuckPerms is **optional**.

---

## 🌍 WorldGuard Integration

Resize includes a custom WorldGuard flag:

```
/rg flag <region> resize allow|deny
```

This allows you to:

* Enable resizing in specific regions
* Disable resizing in protected areas (spawn, arenas, etc.)
* Automatically reset player and mob size inside restricted regions

⚠ WorldGuard is optional and only used if installed.

---

## ⚔ Automatic Size Reset on Damage

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

You can configure minimum and maximum size values:

```yml
scale:
  min: 0.6
  max: 1.6
  mob_min: 0.5
  mob_max: 2.0
```

These values act as:

* Global limits
* Default group fallback values
* Tab-completion suggestions

---

## 👑 Administrators

Administrators can:

* Change other players’ size
* Change mob size
* Ignore size limits
* Bypass cooldowns
* Ignore WorldGuard restrictions
* Be protected from automatic resets
* See unlimited size range in `/resize info`

---

## ⏱ Anti-Spam Protection

Built-in cooldown system prevents command spam.

Applies to:

* `/resize`
* `/resize mob`

---

## 💾 Size Persistence

```yml
save-sizes: true
```

* `true` — Size is saved after rejoining
* `false` — Size resets to `1.0` on join

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

| Permission          | Description                                      |
| ------------------- | ------------------------------------------------ |
| `resize.resize`     | Allows using `/resize`                           |
| `resize.mob`        | Allows using `/resize mob`                       |
| `resize.info`       | Allows using `/resize info`                      |
| `resize.info.other` | Allows viewing size information of other players |
| `resize.reload`     | Allows reloading the plugin configuration        |
| `resize.admin`      | Full bypass of all restrictions                  |
| `resize.*`          | Grants all Resize permissions                    |

---

## 🔌 Optional Dependencies

* LuckPerms (for group-based limits)
* WorldGuard (for region resize control)

The plugin works perfectly without them.
