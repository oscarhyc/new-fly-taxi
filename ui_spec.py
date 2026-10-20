"""Build a UI specification from the shipped APK (reverse engineered).

  python3 ui_spec.py <apk> <decompiled-sources-root> <outdir>

Produces:
  <outdir>/layouts/*.txt   view tree per layout, resource ids resolved to names
  <outdir>/design-tokens.json  colours / dimensions / string resources
  <outdir>/layout-index.json    layout -> screens that use it (by file reference)
"""
import json
import os
import re
import struct
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ui_dump import dump_xml, tree

# ---------------------------------------------------------------- R.java map
def r_map(src_root):
    """0x7f0a1234 -> (type, name) from every R.java in the decompiled tree"""
    out = {}
    for dp, _, fs in os.walk(src_root):
        for f in fs:
            if f != 'R.java':
                continue
            t = open(os.path.join(dp, f), encoding='utf-8', errors='replace').read()
            for block in re.finditer(r'public static final class (\w+) \{(.*?)\n    \}', t, re.S):
                kind, body = block.group(1), block.group(2)
                for name, val in re.findall(r'int (\w+) = (0x[0-9a-fA-F]+|\d+);', body):
                    try:
                        out[int(val, 0)] = f'{kind}/{name}'
                    except ValueError:
                        pass
    return out


# ---------------------------------------------------------------- ARSC types
def read_pool(d, off):
    t, h, sz, count, stylecount, flags, so, sto = struct.unpack_from('<HHIIIIII', d, off)
    utf8 = bool(flags & 0x100)
    offs = struct.unpack_from('<%dI' % count, d, off + h)
    res = []
    for o in offs:
        p = off + so + o
        if utf8:
            n = d[p]; p += 2 if n & 0x80 else 1
            n = d[p]; p += 2 if n & 0x80 else 1
            res.append(d[p:p + n].decode('utf-8', 'replace'))
        else:
            n = struct.unpack_from('<H', d, p)[0]; p += 2
            if n & 0x8000:
                n = ((n & 0x7fff) << 16) | struct.unpack_from('<H', d, p)[0]; p += 2
            res.append(d[p:p + n * 2].decode('utf-16-le', 'replace'))
    return res, sz


def arsc_values(path, want_types=('color', 'dimen', 'integer', 'bool', 'string')):
    """{type: {name: value}} from resources.arsc"""
    with zipfile.ZipFile(path) as z:
        d = z.read('resources.arsc')
    gpool, gsz = read_pool(d, 12)
    pkg = 12 + gsz
    ptype, phdr, psize = struct.unpack_from('<HHI', d, pkg)
    typeStrOff = struct.unpack_from('<I', d, pkg + 268)[0]
    keyStrOff = struct.unpack_from('<I', d, pkg + 276)[0]
    tnames, _ = read_pool(d, pkg + typeStrOff)
    knames, _ = read_pool(d, pkg + keyStrOff)
    out = {t: {} for t in want_types}
    off = pkg + phdr
    while off + 8 <= len(d):
        t, h, sz = struct.unpack_from('<HHI', d, off)
        if sz == 0:
            break
        if t == 0x0201:  # RES_TABLE_TYPE
            idv = d[off + 8]
            ecount, estart = struct.unpack_from('<II', d, off + 12)
            tname = tnames[idv - 1] if 0 < idv <= len(tnames) else str(idv)
            if tname in out:
                for e in range(ecount):
                    eo = struct.unpack_from('<I', d, off + h + 4 * e)[0]
                    if eo == 0xFFFFFFFF:
                        continue
                    ep = off + estart + eo
                    esz, eflags, key = struct.unpack_from('<HHI', d, ep)
                    if eflags & 1:
                        continue
                    vsz, res0, dt, data = struct.unpack_from('<HBBI', d, ep + esz)
                    kn = knames[key] if key < len(knames) else str(key)
                    if dt == 0x03 and data < len(gpool):
                        out[tname][kn] = gpool[data]
                    elif dt == 0x10:
                        out[tname][kn] = data
                    elif dt == 0x12:
                        out[tname][kn] = bool(data)
                    elif dt == 0x1C:  # colour
                        out[tname][kn] = '#%08X' % data
                    elif dt == 0x05:  # dimension (complex): unit | radix | mantissa
                        unit = data & 0xF
                        radix = (data >> 4) & 0x3
                        mant = data >> 8
                        if mant >= 1 << 23:
                            mant -= 1 << 24
                        mult = [1.0, 1 / (1 << 7), 1 / (1 << 15), 1 / (1 << 23)][radix]
                        unit_mult = {0: 1.0, 1: 1.0, 2: 1.0, 3: 1.0, 4: 1 / 3, 5: 160.0, 6: 3.543307}[unit]
                        val = mant * mult * unit_mult
                        suffix = {0: 'px', 1: 'dp', 2: 'sp', 3: 'pt', 4: 'pt', 5: 'in', 6: 'mm'}[unit]
                        out[tname][kn] = f'{val:.4g}{suffix}'
        off += sz
    return out


def resolve(text, rmap):
    def rep(m):
        v = int(m.group(1), 16)
        name = rmap.get(v)
        return f'@{name}' if name else m.group(0)
    return re.sub(r'@0x([0-9a-fA-F]{8})', rep, text)


def main():
    apk, src_root, outdir = sys.argv[1], sys.argv[2], sys.argv[3]
    rmap = r_map(src_root)
    os.makedirs(os.path.join(outdir, 'layouts'), exist_ok=True)
    z = zipfile.ZipFile(apk)
    names = [n for n in z.namelist()
             if n.startswith('res/layout/') and n.endswith('.xml')
             and not re.search(r'/(abc_|design_|mtrl_|notification_|select_dialog|support_|test_|custom_dialog|plain_text|preference_|simple_list|dropdown_item)', n)]
    index = {}
    for n in sorted(names):
        ev = dump_xml(z.read(n))
        txt = resolve(tree(ev), rmap)
        base = n.split('/')[-1]
        with open(os.path.join(outdir, 'layouts', base + '.txt'), 'w', encoding='utf-8') as fh:
            fh.write(f'# source: {n}\n' + txt + '\n')
        index[base] = {
            'source': n,
            'elements': len([l for l in txt.splitlines() if l.strip().startswith('<')]),
            'view_classes': sorted({re.match(r'\s*<([\w.]+)', l).group(1)
                                    for l in txt.splitlines()
                                    if re.match(r'\s*<([\w.]+)', l)}),
        }
    tokens = arsc_values(apk)
    with open(os.path.join(outdir, 'design-tokens.json'), 'w', encoding='utf-8') as fh:
        json.dump({'tokens': tokens, 'resource_names': {f'0x{k:08x}': v for k, v in rmap.items()}},
                  fh, ensure_ascii=False, indent=1)
    with open(os.path.join(outdir, 'layout-index.json'), 'w', encoding='utf-8') as fh:
        json.dump(index, fh, ensure_ascii=False, indent=1)
    print('layouts:', len(index))
    print('colors:', len(tokens.get('color', {})), 'dimens:', len(tokens.get('dimen', {})),
          'strings:', len(tokens.get('string', {})))


if __name__ == '__main__':
    main()
