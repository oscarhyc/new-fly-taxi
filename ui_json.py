"""Produce machine-readable UI specs from the shipped APK.

  python3 ui_json.py <apk> <decompiled-sources-root> <outdir>

Outputs:
  <outdir>/layout-spec.json   one JSON spec per layout (element tree, resolved names)
  <outdir>/screen-map.json    Activity/Fragment -> layouts it inflates
"""
import json
import os
import re
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ui_dump import dump_xml, RES_XML_START_TAG, RES_XML_END_TAG
from ui_spec import r_map

# attributes worth keeping for a renderer
KEEP = {
    'id', 'text', 'hint', 'contentDescription', 'visibility', 'orientation', 'gravity',
    'layout_width', 'layout_height', 'layout_margin', 'layout_marginTop', 'layout_marginBottom',
    'layout_marginStart', 'layout_marginEnd', 'padding', 'paddingTop', 'paddingBottom',
    'paddingLeft', 'paddingRight', 'textColor', 'textSize', 'background', 'src', 'srcCompat',
    'scaleType', 'maxLines', 'singleLine', 'inputType', 'imeOptions', 'minHeight', 'minWidth',
    'layout_weight', 'layout_gravity', 'includeFontPadding', 'alpha', 'elevation',
}


def build_tree(events, rmap):
    root = {'tag': 'ROOT', 'attrs': {}, 'children': []}
    stack = [root]
    for t, name, attrs in events:
        if t == RES_XML_START_TAG:
            node = {'tag': name, 'attrs': {}, 'children': []}
            for n, r, v in attrs:
                key = n
                if isinstance(v, str) and v.startswith('@0x'):
                    rid = int(v[3:], 16)
                    name = rmap.get(rid)
                    if name:
                        v = f'@{name}'
                elif isinstance(v, int) and v >= 0x7f000000:
                    name = rmap.get(v)
                    if name:
                        v = f'@{name}'
                if n in KEEP or n.startswith('layout_') or n.startswith('padding'):
                    node['attrs'][key] = v
            stack[-1]['children'].append(node)
            stack.append(node)
        elif t == RES_XML_END_TAG:
            if len(stack) > 1:
                stack.pop()
    return root


def screen_map(src_root):
    """Activity/Fragment class -> {layouts:[...], kind:activity|fragment}"""
    out = {}
    for dp, _, fs in os.walk(src_root):
        for f in fs:
            if not f.endswith('.java'):
                continue
            p = os.path.join(dp, f)
            t = open(p, encoding='utf-8', errors='replace').read()
            cls = os.path.relpath(p, src_root)[:-5].replace('/', '.')
            layouts = sorted(set(re.findall(r'R\.layout\.(\w+)', t)))
            if not layouts:
                continue
            kind = 'activity' if t.endswith('Activity') or 'Activity' in cls.split('.')[-1] else \
                   'fragment' if 'Fragment' in cls or 'fragment' in cls else 'other'
            out[cls] = {'kind': kind, 'layouts': layouts}
    return out


def main():
    apk, src_root, outdir = sys.argv[1], sys.argv[2], sys.argv[3]
    rmap = r_map(src_root)
    os.makedirs(outdir, exist_ok=True)
    z = zipfile.ZipFile(apk)
    names = [n for n in z.namelist()
             if n.startswith('res/layout/') and n.endswith('.xml')
             and not re.search(r'/(abc_|design_|mtrl_|notification_|select_dialog|support_|test_|custom_dialog|plain_text|preference_|simple_list|dropdown_item|mtrl_|_)', n)]
    spec = {}
    for n in sorted(names):
        ev = dump_xml(z.read(n))
        base = n.split('/')[-1][:-4]
        spec[base] = {'source': n, 'tree': build_tree(ev, rmap)['children']}
    with open(os.path.join(outdir, 'layout-spec.json'), 'w', encoding='utf-8') as fh:
        json.dump(spec, fh, ensure_ascii=False, indent=1)
    sm = screen_map(src_root)
    with open(os.path.join(outdir, 'screen-map.json'), 'w', encoding='utf-8') as fh:
        json.dump(sm, fh, ensure_ascii=False, indent=1)
    print('layout specs:', len(spec), '| screens mapped:', len(sm))


if __name__ == '__main__':
    main()
