"""Decode Android binary layouts (AXML) into a readable view tree.

Usage:
  python3 ui_dump.py <apk> [--layouts a.xml,b.xml] [--out docs/ui]
Prints a compact tree: element, id, class, key attributes, children.
"""
import re
import struct
import sys
import zipfile

RES_STRING_POOL = 0x0001
RES_XML = 0x0003
RES_XML_START_TAG = 0x0102
RES_XML_END_TAG = 0x0103
RES_XML_RESOURCE_MAP = 0x0180


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
    return res


def dump_xml(d, resmap=None):
    """returns list of (tag, attrs) events"""
    off = 8
    strings = []
    events = []
    while off < len(d):
        t, h, sz = struct.unpack_from('<HHI', d, off)
        if sz == 0:
            break
        if t == RES_STRING_POOL:
            strings = read_pool(d, off)
        elif t == RES_XML_RESOURCE_MAP:
            n = (sz - 8) // 4
            resmap = list(struct.unpack_from('<%dI' % n, d, off + 8))
        elif t in (RES_XML_START_TAG, RES_XML_END_TAG):
            ns, name = struct.unpack_from('<iI', d, off + h)
            if t == RES_XML_END_TAG:
                events.append((t, strings[name] if name < len(strings) else str(name), []))
                off += sz
                continue
            attr_start, attr_size, attr_count = struct.unpack_from('<HHH', d, off + h + 8)
            attrs = []
            base = off + h + attr_start
            for i in range(attr_count):
                p = base + i * attr_size
                ans, an, rawv, tsz, res0, dt, data = struct.unpack_from('<iIIHBBI', d, p)
                a_name = strings[an] if an < len(strings) else str(an)
                a_res = resmap[an - 1] if resmap and 0 < an <= len(resmap) else 0
                if dt == 0x03:
                    val = strings[data] if data < len(strings) else str(data)
                elif dt == 0x10:
                    val = data
                elif dt == 0x12:
                    val = bool(data)
                elif dt == 0x01:
                    val = '@0x%08x' % data
                elif dt == 0x11:
                    val = '0x%08x' % data
                else:
                    val = data
                attrs.append((a_name, a_res, val))
            events.append((t, strings[name] if name < len(strings) else str(name), attrs))
        off += sz
    return events


def attr_str(attrs):
    keep = ('id', 'text', 'hint', 'contentDescription', 'visibility', 'layout_width',
            'layout_height', 'orientation', 'gravity', 'background', 'src', 'srcCompat',
            'textColor', 'textSize', 'layout_margin', 'padding', 'maxLines', 'inputType',
            'imeOptions', 'singleLine', 'minHeight', 'minWidth', 'elevation', 'alpha')
    out = []
    for n, r, v in attrs:
        if n.startswith('layout_') and n not in keep:
            continue
        if n in ('layout_width', 'layout_height'):
            if isinstance(v, int) and v < 0:
                v = {0: 'wrap', 1: 'fill', 2: 'match'}.get(-1 - v, v)
        if n == 'id' and isinstance(v, int):
            v = '0x%08x' % v
        out.append(f'{n}={v}')
    return ' '.join(out)


def tree(events):
    lines = []
    depth = 0
    for t, name, attrs in events:
        if t == RES_XML_START_TAG:
            lines.append('  ' * depth + f'<{name} {attr_str(attrs)}>')
            depth += 1
        elif t == RES_XML_END_TAG:
            depth = max(0, depth - 1)
    return '\n'.join(lines)


if __name__ == '__main__':
    apk = sys.argv[1]
    want = None
    out = None
    for i, a in enumerate(sys.argv):
        if a == '--layouts' and i + 1 < len(sys.argv):
            want = set(x.strip() for x in sys.argv[i + 1].split(',') if x.strip())
        if a == '--out' and i + 1 < len(sys.argv):
            out = sys.argv[i + 1]
    z = zipfile.ZipFile(apk)
    names = [n for n in z.namelist() if n.startswith('res/layout/') and n.endswith('.xml')]
    if want:
        names = [n for n in names if n.split('/')[-1] in want]
    import os
    if out:
        os.makedirs(out, exist_ok=True)
    for n in sorted(names):
        ev = dump_xml(z.read(n))
        txt = tree(ev)
        if out:
            with open(os.path.join(out, n.split('/')[-1] + '.txt'), 'w', encoding='utf-8') as fh:
                fh.write(txt)
        else:
            print('=' * 70)
            print(n)
            print(txt)
