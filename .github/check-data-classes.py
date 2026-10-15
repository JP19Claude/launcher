#!/usr/bin/env python3
"""
Fails the build when a Kotlin data class has so many constructor fields that calling its copy() or its constructor
with default values needs more registers than Android's code format (dex) allows: 255. Such a class compiles, but the
phone refuses to load every class that calls it (java.lang.VerifyError: "expected N argument registers") – ZENITH 21.5
(191) was lost that way with 249 fields. Nest a group of fields in a data class of its own before the limit is reached.
"""
import math
import os
import re
import sys

LIMIT = 250  # the real limit is 255; stay a few registers below it

def constructor_body(src, start):
    depth = 0
    i = start
    while i < len(src):
        c = src[i]
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return src[start + 1:i]
        i += 1
    return ''

def fields(body):
    body = re.sub(r'//[^\n]*', '', body)
    body = re.sub(r'/\*.*?\*/', '', body, flags=re.S)
    parts, depth, cur = [], 0, ''
    for c in body:
        if c in '([<{':
            depth += 1
        elif c in ')]>}':
            depth -= 1
        if c == ',' and depth == 0:
            parts.append(cur)
            cur = ''
        else:
            cur += c
    if cur.strip():
        parts.append(cur)
    out = []
    for p in parts:
        m = re.search(r'va[lr]\s+\w+\s*:\s*([\w.]+)', p)
        if m:
            out.append(m.group(1))
    return out

bad = False
worst = []
for root in ('app/src', 'smart/src'):
    for dirpath, _, files in os.walk(root):
        for name in files:
            if not name.endswith('.kt'):
                continue
            path = os.path.join(dirpath, name)
            src = open(path, encoding='utf-8').read()
            for m in re.finditer(r'\bdata\s+class\s+(\w+)\s*(?:<[^>]*>)?\s*\(', src):
                types = fields(constructor_body(src, m.end() - 1))
                slots = sum(2 if t in ('Long', 'Double') else 1 for t in types)
                registers = 1 + slots + math.ceil(len(types) / 32) + 1
                worst.append((registers, m.group(1), len(types), path))
                if registers > LIMIT:
                    bad = True
                    print(f'::error file={path}::data class {m.group(1)} has {len(types)} fields = {registers} registers for copy() (limit 255, '
                          f'checked at {LIMIT}). Nest a group of fields in a data class of its own.')

worst.sort(reverse=True)
for registers, cls, n, path in worst[:3]:
    print(f'{cls}: {n} fields, {registers} registers ({path})')
sys.exit(1 if bad else 0)
