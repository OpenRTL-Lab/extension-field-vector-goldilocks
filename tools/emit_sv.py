#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Extension-Field Vector Hardware Accelerator
# Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
# Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
# File: tools/emit_sv.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""生成参考向量、Chisel验证环境和产品依赖闭包，不需要内部仓库或Git历史。"""
from source_headers import stamp_generated
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess

from measurement import reference_check

ROOT = Path(__file__).resolve().parents[1]
OP = Path('operators/extension-field-vector')
BUNDLED = Path('rtl/v1.1.0/gl64-e2-lane1-ctx16-l65536')


def run(command, output, label, expected=0):
    with (output / (label + '.log')).open('x') as log:
        result = subprocess.run([str(x) for x in command], cwd=ROOT,
                                stdout=log, stderr=subprocess.STDOUT)
    if result.returncode != expected:
        raise RuntimeError(f'{label}: status={result.returncode}; inspect the output log')


def relative_build(value):
    p = Path(value)
    if p.is_absolute() or '..' in p.parts or not p.parts or p.parts[0] != 'build' or p == Path('build'):
        raise ValueError('Choose a new relative subdirectory inside build')
    return p


def code(text):
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)


def product_closure(rtl):
    stamp_generated(rtl)
    definitions, texts = {}, {}
    for path in sorted(rtl.glob('*.sv')):
        text = re.sub(r'[ \t]*//[^\n]*\.scala:\d+:\d+[^\n]*', '', path.read_text())
        path.write_text(text)
        texts[path] = code(text)
        for name in re.findall(r'\bmodule\s+(\w+)', texts[path]):
            if name in definitions:
                raise ValueError('Duplicate module definition')
            definitions[name] = path
    selected, pending = set(), ['ExtensionFieldVector']
    while pending:
        path = definitions[pending.pop()]
        if path in selected:
            continue
        selected.add(path)
        instances = set(re.findall(r'^[ \t]*(\w+)\b[ \t]+(?:#\s*\([^;]*?\)\s*)?\w+\s*\(', texts[path], re.M | re.S))
        pending.extend(instances & definitions.keys())
    for path in selected:
        if re.search(r'\$readmem|\$display|%|\b(?:DSP48\w*|RAMB\w*|URAM\w*|BUFG\w*|IOBUF|ReferenceRom|ExtensionSimTop)\b', texts[path]):
            raise ValueError('Verification or FPGA-only content in product closure')
    return sorted(selected)


def simulation_list(rtl, output, bundled=False):
    sources = sorted(rtl.rglob('*.sv'))
    if bundled:
        products = set(product_closure(rtl))
        sources = [p for p in sources if p not in products] + sorted(BUNDLED.glob('*.sv'))
    includes = sorted({p.parent for p in sources})
    output.write_text(''.join('+incdir+' + str(p) + '\n' for p in includes)
                      + ''.join(str(p) + '\n' for p in sources))


def task_plans(max_length, large):
    plans = []
    def add(mode, length, base=0, fault=0, kind='functional'):
        plans.append(dict(mode=mode, length=length, base=base, fault=fault,
                          kind=kind, tag=110000 + len(plans)))
    for mode in range(7):
        add(mode, 0); add(mode, 1); add(mode, 16); add(mode, 16, 1)
    add(7, 3); add(2, max_length + 1)
    for fault in (1, 2, 3):
        add(2, 4, fault=fault)
    add(2, 4, 1, 6); add(4, 16, fault=4); add(4, 16, fault=5)
    for mode in range(7):
        if large and mode != 4:
            add(mode, max_length, kind='large')
        count = (16 if mode == 4 else 64) if large else 8
        length = min(max_length, 256 if mode == 4 and large else 32)
        for _ in range(count):
            add(mode, length, fault=5 if mode == 4 else 0, kind='steady')
    if large:
        add(4, max_length, fault=4, kind='large_zero_inverse')
        add(4, min(max_length, 4096), fault=5, kind='large_inverse')
    return plans


def generate(output, max_length=65536, contexts=16, large=False):
    os.chdir(ROOT)
    output = relative_build(output)
    if output.exists():
        raise ValueError('Output already exists; choose a new directory')
    if not 16 <= max_length <= 65536 or contexts not in (16, 32):
        raise ValueError('max-length: 16..65536; contexts: 16 or 32')
    output.mkdir(parents=True)
    vectors = output / 'vectors'
    vectors.mkdir()
    plans = task_plans(max_length, large)
    (vectors / 'request.tsv').write_text(''.join(
        f"{p['mode']} {p['length']} {p['base']} {p['fault']} {p['tag']} {max_length}\n" for p in plans))
    run(shlex.split(os.environ.get('CC', 'cc')) + ['-O2', '-std=c11', '-Wall', '-Wextra',
        OP / 'cmodel/reference.c', '-o', output / 'reference'], output, 'reference-compile')
    run([output / 'reference', vectors / 'request.tsv', vectors], output, 'reference')
    check = reference_check(vectors)
    (output / 'reference-check.json').write_text(json.dumps(check, indent=2) + '\n')
    meta = [list(map(int, line.split())) for line in (vectors / 'plans.tsv').read_text().splitlines()]
    for p, values in zip(plans, meta):
        p.update(status=values[7], zeros=values[8])
    (output / 'plans.json').write_text(json.dumps(plans, indent=2) + '\n')
    rtl = output / 'rtl'
    run(shlex.split(os.environ.get('MILL', 'mill')) + ['-i', 'extensionField.tool.runMain',
        'zk.extension.Emit', rtl, max_length, contexts, vectors], output, 'emit')
    products = product_closure(rtl)
    (rtl / 'product.f').write_text(''.join(str(p) + '\n' for p in products))
    simulation_list(rtl, rtl / 'simulation.f')
    (output / 'configuration.json').write_text(json.dumps(dict(version='1.1.0',
        max_length=max_length, contexts=contexts, large=large, tasks=len(plans)), indent=2) + '\n')
    return output, plans


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', default='build/emit')
    parser.add_argument('--max-length', type=int, default=65536)
    parser.add_argument('--contexts', type=int, default=16)
    parser.add_argument('--large', action='store_true')
    args = parser.parse_args()
    generate(args.out, args.max_length, args.contexts, args.large)
    print('Generation passed; product list: output/rtl/product.f')
