"""Replace the "Lessons coming soon" aside on each module overview page with a numbered list of its lessons.

Run after adding lessons: python3 site/scripts/lesson_index.py
"""
import re, sys, pathlib
docs = pathlib.Path(__file__).resolve().parent.parent / 'src' / 'content' / 'docs'
for module in sorted(docs.glob('part-*/module-*')):
    index = module / 'index.mdx'
    lessons = []
    for f in module.glob('*.mdx'):
        if f.name in ('index.mdx',) or f.name.startswith('_'):
            continue
        text = f.read_text()
        title = re.search(r'^title:\s*"(.*)"', text, re.M).group(1)
        order = int(re.search(r'^\s+order:\s*(\d+)', text, re.M).group(1))
        lessons.append((order, title, f.stem))
    if not lessons:
        continue
    lessons.sort()
    listing = '## Lessons\n\n' + '\n'.join(f'{i}. [{t}]({stem}/)' for i, (_, t, stem) in enumerate(lessons, 1)) + '\n'
    s = index.read_text()
    s = re.sub(r'<Aside type="note" title="Lessons coming soon">.*?</Aside>\n', listing, s, flags=re.S)
    s = re.sub(r'## Lessons\n\n(?:\d+\. .*\n)+', listing, s)
    if 'Aside' not in s.split('---', 2)[2].replace("import { Aside } from '@astrojs/starlight/components';", ''):
        s = s.replace("import { Aside } from '@astrojs/starlight/components';\n\n", '')
    index.write_text(s)
    print('updated', module.name, len(lessons))
