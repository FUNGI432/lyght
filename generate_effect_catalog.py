import json

data = json.load(open(r"E:\Work\New folder\MagicLantern\effects.json", encoding="utf-8"))


def esc(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')


lines = []
lines.append("package com.aman.melklight.protocol")
lines.append("")
lines.append("data class Effect(val name: String, val code: Int)")
lines.append("data class EffectGroup(val label: String, val effects: List<Effect>)")
lines.append("data class PinOption(val label: String, val rgb: Triple<Int, Int, Int>)")
lines.append("")
lines.append("// Generated from the decompiled MagicLantern APK's res/values/arrays.xml")
lines.append("// (modes_group0..8 + modes_group*_command, pin_sequence[_command]).")
lines.append("object EffectCatalog {")
lines.append("    val groups: List<EffectGroup> = listOf(")
for g in data["groups"]:
    lines.append(f'        EffectGroup("{esc(g["label"])}", listOf(')
    for name, code in g["items"]:
        lines.append(f'            Effect("{esc(name)}", {code}),')
    lines.append("        )),")
lines.append("    )")
lines.append("")
lines.append("    val pinSequences: List<PinOption> = listOf(")
for label, code in data["pin_sequence"]:
    r, g2, b = int(code[0]), int(code[1]), int(code[2])
    lines.append(f'        PinOption("{esc(label)}", Triple({r}, {g2}, {b})),')
lines.append("    )")
lines.append("}")

out_path = r"E:\Work\New folder\MagicLantern\MelkLight\app\src\main\java\com\aman\melklight\protocol\EffectCatalog.kt"
import os
os.makedirs(os.path.dirname(out_path), exist_ok=True)
open(out_path, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("written", out_path, len(lines), "lines")
