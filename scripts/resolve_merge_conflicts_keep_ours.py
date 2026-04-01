from pathlib import Path
import re

repo_root = Path('.')
conflict_files = []
for p in repo_root.rglob('*.xml'):
    text = p.read_text(encoding='utf-8', errors='ignore')
    if '<<<<<<<' in text and '>>>>>>>' in text:
        conflict_files.append(p)

if not conflict_files:
    print('No conflict markers found.')
    exit(0)

for p in conflict_files:
    lines = p.read_text(encoding='utf-8', errors='ignore').splitlines()
    out_lines = []
    in_conflict = False
    keep_ours = False
    for line in lines:
        if line.startswith('<<<<<<< '):
            in_conflict = True
            keep_ours = True
            continue
        if in_conflict and line.startswith('======='):
            keep_ours = False
            continue
        if in_conflict and line.startswith('>>>>>>>'):
            in_conflict = False
            keep_ours = False
            continue
        if not in_conflict:
            out_lines.append(line)
        else:
            if keep_ours:
                out_lines.append(line)

    p.write_text('\n'.join(out_lines) + '\n', encoding='utf-8')
    print(f'Cleaned {p}')
