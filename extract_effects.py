import re, json

path = r"E:\Work\New folder\MagicLantern\decompiled\resources\res\values\arrays.xml"
data = open(path, encoding="utf-8").read()
arrays = dict(re.findall(r'<array name="([^"]+)">(.*?)</array>', data, re.S))


def items(key):
    body = arrays.get(key, "")
    raw = re.findall(r"<item>(.*?)</item>", body, re.S)
    return [x.replace("\\'", "'") for x in raw]


groups = ["Basic", "Curtain", "Trans", "Water", "Flow", "Tail", "Run", "RunBack", "Scenes"]
out = {"groups": []}
for g in range(9):
    names = items(f"modes_group{g}")
    cmds = [int(c) for c in items(f"modes_group{g}_command")]
    out["groups"].append({"label": groups[g], "items": list(zip(names, cmds))})

out["pin_sequence"] = list(zip(items("pin_sequence"), items("pin_sequence_command")))

out_path = r"E:\Work\New folder\MagicLantern\effects.json"
json.dump(out, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("written", out_path)
