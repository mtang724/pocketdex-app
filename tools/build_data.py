"""Build the offline Chinese Pokédex dataset (dex.json) from PokeAPI CSV files."""
import csv, json, os, sys, re
from collections import defaultdict
CSV, OUT = sys.argv[1], sys.argv[2]
# Optional: folder of per-species JSON files from github.com/42arch/pokemon-dataset-zh (MIT),
# used for Chinese Pokédex entries that PokeAPI lacks.
ZHDEX = sys.argv[3] if len(sys.argv) > 3 else None
ZH, ZHT, EN = "12", "4", "9"
MAXID = 1025
def rows(name):
    with open(os.path.join(CSV, name + ".csv"), newline="", encoding="utf-8") as f:
        yield from csv.DictReader(f)
def clean(t):
    t = t.replace('\u00ad\n', '').replace('\f', '\n')
    if re.search('[\u4e00-\u9fff]', t):
        t = re.sub(r'\s*\n\s*', '', t)
        cjk = '[\u3000-\u303f\u4e00-\u9fff\uff00-\uffef]'
        t = re.sub('(?<=' + cjk + ')[ \u3000]+|[ \u3000]+(?=' + cjk + ')', '', t)
    else:
        t = re.sub(r'\s+', ' ', t)
    return t.strip()
def names(name, idcol, langcol="local_language_id", field="name"):
    """Returns {id: (zh, en)} with zh-Hans -> zh-Hant -> en fallback and en -> zh fallback."""
    d = defaultdict(dict)
    for r in rows(name):
        if r[langcol] in (ZH, ZHT, EN): d[r[idcol]][r[langcol]] = r[field]
    out = {}
    for k, v in d.items():
        zh = v.get(ZH) or v.get(ZHT) or v.get(EN, "")
        out[k] = (zh, v.get(EN) or zh)
    return out
def zh(t): return t[0] if t else ""
def en(t): return t[1] if t else ""

type_n = names("type_names", "type_id")
types = [{"id": i, "zh": zh(type_n[str(i)]), "en": en(type_n[str(i)])} for i in range(1, 19)]
eff = [[1.0] * 18 for _ in range(18)]
for r in rows("type_efficacy"):
    a, d = int(r["damage_type_id"]), int(r["target_type_id"])
    if a <= 18 and d <= 18: eff[a - 1][d - 1] = int(r["damage_factor"]) / 100

def best_text(name, idcol, vcol, langcol):
    """Latest text per id: zh (zh-Hans > zh-Hant) and en, each falling back to the other."""
    bz, be = {}, {}
    for r in rows(name):
        lang, k, v = r[langcol], r[idcol], int(r[vcol])
        if lang in (ZH, ZHT):
            rank = (2 if lang == ZH else 1, v)
            if rank > bz.get(k, ((0, 0), ""))[0]: bz[k] = (rank, clean(r["flavor_text"]))
        elif lang == EN and v > be.get(k, (0, ""))[0]:
            be[k] = (v, clean(r["flavor_text"]))
    keys = set(bz) | set(be)
    return {k: ((bz.get(k) or (0, be[k][1]))[1], (be.get(k) or (0, bz[k][1]))[1]) for k in keys}
ab_name = names("ability_names", "ability_id")
ab_desc = best_text("ability_flavor_text", "ability_id", "version_group_id", "language_id")

mv_name = names("move_names", "move_id")
moves_all = {r["id"]: r for r in rows("moves")}
item_n = names("item_names", "item_id")
loc_n = names("location_names", "location_id")
egg_n = names("egg_group_prose", "egg_group_id")
color_n = names("pokemon_color_names", "pokemon_color_id")
gen_n = names("generation_names", "generation_id")

sp_names = defaultdict(dict)
for r in rows("pokemon_species_names"):
    sp_names[r["pokemon_species_id"]][r["local_language_id"]] = (r["name"], r["genus"])
flv = best_text("pokemon_species_flavor_text", "species_id", "version_id", "language_id")
HAS_CJK = re.compile('[\u4e00-\u9fff]')
ZH_MISSING = "暂无中文图鉴描述。"
if ZHDEX:
    import glob
    filled = 0
    for f in glob.glob(os.path.join(ZHDEX, "*.json")):
        sid = str(int(os.path.basename(f)[:4]))
        if HAS_CJK.search(flv.get(sid, ("", ""))[0]):
            continue
        groups = json.load(open(f, encoding="utf-8")).get("pokedex_entries") or []
        text = next((clean(v["text"]) for g in reversed(groups) for v in g.get("versions", []) if v.get("text")), "")
        if text:
            flv[sid] = (text, flv.get(sid, ("", ""))[1]); filled += 1
    print("zh entries filled from 42arch:", filled)
# Never show the English text in Chinese mode.
for sid in list(flv):
    if not HAS_CJK.search(flv[sid][0]):
        flv[sid] = (ZH_MISSING, flv[sid][1])

pk = {r["id"]: r for r in rows("pokemon") if int(r["id"]) <= MAXID}
ptypes = defaultdict(list)
for r in rows("pokemon_types"):
    if r["pokemon_id"] in pk: ptypes[r["pokemon_id"]].append((int(r["slot"]), int(r["type_id"])))
pstats = defaultdict(dict)
for r in rows("pokemon_stats"):
    if r["pokemon_id"] in pk: pstats[r["pokemon_id"]][int(r["stat_id"])] = int(r["base_stat"])
pabs = defaultdict(list)
for r in rows("pokemon_abilities"):
    if r["pokemon_id"] in pk: pabs[r["pokemon_id"]].append((int(r["slot"]), int(r["ability_id"]), int(r["is_hidden"])))
peggs = defaultdict(list)
for r in rows("pokemon_egg_groups"):
    peggs[r["species_id"]].append(egg_n.get(r["egg_group_id"], ("", "")))

vg_order = {r["id"]: int(r["order"]) for r in rows("version_groups")}
SV = "25"
lvl = defaultdict(lambda: defaultdict(dict))  # pokemon -> vg -> move -> level
for r in rows("pokemon_moves"):
    if r["pokemon_move_method_id"] != "1" or r["pokemon_id"] not in pk: continue
    lvl[r["pokemon_id"]][r["version_group_id"]][r["move_id"]] = int(r["level"] or 0)
used_moves = set()
def pick_moves(pid):
    vgs = lvl.get(pid, {})
    if not vgs: return []
    vg = SV if SV in vgs else max(vgs, key=lambda v: vg_order.get(v, 0))
    out = sorted(((lv, int(m)) for m, lv in vgs[vg].items()))
    for _, m in out: used_moves.add(str(m))
    return [[m, lv] for lv, m in out]

GENDER = {"1": ("雄性", "male"), "2": ("雌性", "female")}
TOD = {"day": ("白天", "daytime"), "night": ("夜晚", "at night"), "dusk": ("黄昏", "at dusk"), "full-moon": ("满月", "full moon")}
def spname(i, li): n = sp_names.get(i, {}); t = n.get(ZH if li == 0 else EN) or n.get(EN) or ("",); return t[0]
def evo_text(r, li):
    """li = 0 for Chinese, 1 for English."""
    Z = li == 0
    t = r["evolution_trigger_id"]; parts = []
    def nm(table, key, default): return table.get(key, (default, default))[li]
    if t == "1":
        if r["minimum_level"]: parts.append(f"Lv.{r['minimum_level']}")
        if r["minimum_happiness"]: parts.append("亲密度高" if Z else "high friendship")
        if r["minimum_affection"]: parts.append("友好度高" if Z else "high affection")
        if r["minimum_beauty"]: parts.append("美丽度高" if Z else "high beauty")
        if r["known_move_id"]: parts.append(("学会" if Z else "knowing ") + nm(mv_name, r["known_move_id"], "move"))
        if r["known_move_type_id"]: parts.append(("学会" + nm(type_n, r["known_move_type_id"], "") + "属性招式") if Z else ("knowing a " + nm(type_n, r["known_move_type_id"], "") + "-type move"))
        if r["held_item_id"]: parts.append(("携带" if Z else "holding ") + nm(item_n, r["held_item_id"], "item"))
        if r["location_id"]: parts.append(("在" if Z else "at ") + nm(loc_n, r["location_id"], "a special place"))
        if r["needs_overworld_rain"] == "1": parts.append("下雨时" if Z else "in rain")
        if r["turn_upside_down"] == "1": parts.append("倒持主机" if Z else "console upside down")
        if r["party_species_id"]: parts.append(("队伍中有" if Z else "with ") + spname(r["party_species_id"], li) + ("" if Z else " in party"))
        if r["party_type_id"]: parts.append(("队伍中有" + nm(type_n, r["party_type_id"], "") + "属性") if Z else ("with a " + nm(type_n, r["party_type_id"], "") + "-type in party"))
        if r["relative_physical_stats"]: parts.append({"1": "Atk > Def", "-1": "Atk < Def", "0": "Atk = Def"}.get(r["relative_physical_stats"], "") if not Z else {"1": "攻击>防御", "-1": "攻击<防御", "0": "攻击=防御"}.get(r["relative_physical_stats"], ""))
        if not parts: parts.append("升级" if Z else "level up")
    elif t == "2":
        parts.append("通信交换" if Z else "trade")
        if r["held_item_id"]: parts.append(("携带" if Z else "holding ") + nm(item_n, r["held_item_id"], "item"))
        if r["trade_species_id"]: parts.append(("与" + spname(r["trade_species_id"], li) + "交换") if Z else ("for " + spname(r["trade_species_id"], li)))
    elif t == "3":
        parts.append(("使用" if Z else "use ") + nm(item_n, r["trigger_item_id"], "item"))
    else:
        parts.append("特殊条件" if Z else "special condition")
    if r["gender_id"]: parts.append(GENDER.get(r["gender_id"], ("", ""))[li])
    if r["time_of_day"]: parts.append(TOD.get(r["time_of_day"], ("", ""))[li])
    return (" " if Z else ", ").join(p for p in parts if p)
evo = {}
for r in rows("pokemon_evolution"):
    s_ = r["evolved_species_id"]
    if int(s_) > MAXID: continue
    if s_ not in evo or r["is_default"] == "1":
        evo[s_] = (evo_text(r, 0), evo_text(r, 1))

species = []
for r in rows("pokemon_species"):
    sid = r["id"]
    if int(sid) > MAXID: continue
    n = sp_names[sid]; zhn = n.get(ZH) or n.get(ZHT) or n.get(EN); enn = n.get(EN) or zhn
    p = pk[sid]
    st = pstats[sid]
    species.append({
        "id": int(sid), "zh": zhn[0], "en": enn[0], "genus": zhn[1] or enn[1], "genus_en": enn[1] or zhn[1],
        "h": int(p["height"]), "w": int(p["weight"]),
        "t": [t for _, t in sorted(ptypes[sid])],
        "s": [st.get(i, 0) for i in range(1, 7)],
        "ab": [[a, h] for _, a, h in sorted(pabs[sid])],
        "fl": zh(flv.get(sid)), "fl_en": en(flv.get(sid)),
        "gen": zh(gen_n.get(r["generation_id"])), "gen_en": en(gen_n.get(r["generation_id"])),
        "cap": int(r["capture_rate"] or 0), "gr": int(r["gender_rate"]),
        "egg": [zh(e) for e in peggs[sid] if e[0]], "egg_en": [en(e) for e in peggs[sid] if e[0]],
        "col": zh(color_n.get(r["color_id"])), "col_en": en(color_n.get(r["color_id"])),
        "leg": r["is_legendary"] == "1", "myth": r["is_mythical"] == "1", "baby": r["is_baby"] == "1",
        "from": int(r["evolves_from_species_id"]) if r["evolves_from_species_id"] else 0,
        "chain": int(r["evolution_chain_id"] or 0), "evo": zh(evo.get(sid)), "evo_en": en(evo.get(sid)),
        "mv": pick_moves(sid),
    })
species.sort(key=lambda s: s["id"])
used_abs = {str(a) for s in species for a, _ in s["ab"]}
abilities = {a: [zh(ab_name.get(a)), zh(ab_desc.get(a)), en(ab_name.get(a)), en(ab_desc.get(a))] for a in sorted(used_abs, key=int)}
moves = {}
for m in sorted(used_moves, key=int):
    r = moves_all[m]
    nm_ = mv_name.get(m, (r["identifier"], r["identifier"]))
    moves[m] = [nm_[0], int(r["type_id"]), int(r["power"] or 0), int(r["accuracy"] or 0), int(r["pp"] or 0), int(r["damage_class_id"] or 0), nm_[1]]
out = {"types": types, "eff": eff, "abilities": abilities, "moves": moves, "species": species}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print("species", len(species), "abilities", len(abilities), "moves", len(moves), "size KB", os.path.getsize(OUT) // 1024)
miss = [s["id"] for s in species if not s["fl"] or not s["t"] or not s["mv"]]
print("missing flavor/types/moves:", len(miss), miss[:20])
p = species[53]; print(json.dumps({k: p[k] for k in p if k != "mv"}, ensure_ascii=False)); print("moves:", [(moves[str(m)][0], moves[str(m)][6], l) for m, l in p["mv"][:6]]); print("abilities:", [abilities[str(a)] for a, _ in p["ab"]][:1])
e = species[132]; print("eevee chain", e["chain"], [(s["en"], s["evo_en"]) for s in species if s["chain"] == e["chain"]])
