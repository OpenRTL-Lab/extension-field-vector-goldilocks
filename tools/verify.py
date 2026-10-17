#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Extension-Field Vector Hardware Accelerator
# Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
# Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
# File: tools/verify.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""参考核对、产品lint、六类RTL验证和独立任务延迟/吞吐/忙率统计。"""
import argparse
import json
import os
from pathlib import Path
import re
import shlex

from emit_sv import BUNDLED, OP, code, generate, product_closure, run, simulation_list
from measurement import metrics


def verify(output, max_length=65536, contexts=16, large=False, jobs=8):
    if not 1 <= jobs <= 64:
        raise ValueError('jobs must be 1..64')
    output, plans = generate(output, max_length, contexts, large)
    report = dict(status=1, configuration=dict(max_length=max_length, contexts=contexts,
        large=large), ideal_interfaces=True, task_overlap=False, tests={})
    try:
        rtl = output / 'rtl'
        products = product_closure(rtl)
        bundled = max_length == 65536 and contexts == 16
        if bundled:
            assert {p.name for p in products} == {p.name for p in BUNDLED.glob('*.sv')}
            for p in products:
                assert re.sub(r'\s+', '', code(p.read_text())) == re.sub(r'\s+', '', code((BUNDLED / p.name).read_text())), p.name
            report['bundled_product_equal'] = True
        verilator = shlex.split(os.environ.get('VERILATOR', 'verilator'))
        run(verilator + ['--lint-only', '--assert', '--top-module', 'ExtensionFieldVector',
            '-f', rtl / 'product.f'], output, 'product-lint')
        meta = [list(map(int, line.split())) for line in (output / 'vectors/plans.tsv').read_text().splitlines()]
        checked = sum(v[6] for v in meta)
        os.environ['CCACHE_DISABLE'] = '1'
        for name in (['generated', 'bundled'] if bundled else ['generated']):
            case = output / name
            case.mkdir()
            listing = case / 'simulation.f'
            simulation_list(rtl, listing, name == 'bundled')
            run(verilator + ['--cc', '--exe', '--build', '--assert', '-O3', '-j', jobs,
                '--top-module', 'ExtensionSimTop', '-Mdir', case / 'obj', '-CFLAGS', '-O2 -std=c++17',
                '-f', listing, (OP / 'sim/driver.cpp').resolve()], case, 'compile')
            # Verilator make files use an absolute C++ source; it stays in ignored build output.
            binary = (case / 'obj/VExtensionSimTop').resolve()
            tests = {}
            for mode, test in enumerate(('ideal', 'stalls', 'reset', 'negative', 'reset_inverse', 'reset_dot')):
                run([binary, mode], case, test, 1 if mode == 3 else 0)
                log = (case / (test + '.log')).read_text()
                summaries = re.findall(r'^SUMMARY (.*)$', log, re.M)
                assert len(summaries) == 1
                result = {k: int(v) for k, v in re.findall(r'(\w+)=(\d+)', summaries[0])}
                assert result['checked'] == checked and result['errors'] == (1 if mode == 3 else 0)
                assert result['pass'] == (0 if mode == 3 else 1)
                if mode in (2, 4, 5):
                    assert 'RESET_RECOVERY' in log
                tests[test] = result
            report['tests'][name] = tests
            measured = metrics((case / 'ideal.log').read_text(), plans)
            (case / 'performance.json').write_text(json.dumps(measured, indent=2) + '\n')
            if name == 'generated':
                report['performance'] = measured
            else:
                assert measured == report['performance']
                assert tests == report['tests']['generated']
        report['tasks'] = len(plans)
        report['checked_elements'] = checked
        report['status'] = 0
    finally:
        (output / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(dict(status=report['status'], tasks=report['tasks'],
        checked_elements=checked, bundled_tested=bundled)))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', default='build/verify')
    parser.add_argument('--max-length', type=int, default=65536)
    parser.add_argument('--contexts', type=int, default=16)
    parser.add_argument('--large', action='store_true')
    parser.add_argument('--jobs', type=int, default=8)
    args = parser.parse_args()
    verify(args.out, args.max_length, args.contexts, args.large, args.jobs)
