# ❓ FAQ and Troubleshooting

<details>
<summary><b>Pressing R does nothing / reloads my shaders</b></summary>

Update to v0.2.0 or newer: the Gojo keys now win over other mods' keys while you're in a world. In older versions Iris's
"Reload Shaders" (also `R`) took the key. Rebind one of them under **Options → Controls** if you want both.
</details>

<details>
<summary><b>The game won't start after updating</b></summary>

You probably have two copies of the mod. Delete every old `gojo-limitless-*.jar` from your `mods` folder and keep only the
newest.
</details>

<details>
<summary><b>I hear no sound at all</b></summary>

If the log (`logs/latest.log`) says **"Failed to open OpenAL device"**, Minecraft couldn't open your audio device. That
happens before any mod sound plays. Check that your speakers or headphones are connected and set as the default output in
Windows Sound settings, then restart the game.
</details>

<details>
<summary><b>It destroyed my base</b></summary>

Terrain destruction is on by default. Turn it off under **Settings → General**, or turn it off per technique. Back up
worlds you care about before trying the 200% Purple or the nuke.
</details>

<details>
<summary><b>Low FPS during the big techniques</b></summary>

Lower **Client → VFX quality** and **maximum debris**, and **General → blocks per tick**. The nuke and Maximum Output move
a lot of terrain.
</details>

<details>
<summary><b>The flashes or impact frames are too much</b></summary>

Lower **Client → flash strength** (impact frames switch off below 0.25) or turn **Impact frames** off.
</details>

<details>
<summary><b>Can I skip the cutscenes?</b></summary>

Press `Enter` to skip one, or turn **Client → cutscenes** off to play everything without them.
</details>

<details>
<summary><b>Is this safe? It was made with AI</b></summary>

Every file is in this repository. Read the code, or [build the jar yourself](Building-from-Source) and use that one.
</details>

## Reporting a bug
Open an [issue](https://github.com/{{REPO}}/issues) with:
- what you did and what happened
- your mod list (and shader pack, if any)
- `logs/latest.log`, and the crash report from `crash-reports/` if the game crashed
