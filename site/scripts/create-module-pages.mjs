// Creates an overview page for every module in src/data/curriculum.mjs that
// does not have one yet. Existing pages are never overwritten.
//
//   node scripts/create-module-pages.mjs
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { parts } from '../src/data/curriculum.mjs';

const docs = new URL('../src/content/docs/', import.meta.url).pathname;
const yamlString = (text) => JSON.stringify(text);

let created = 0;
for (const part of parts) {
	for (const module of part.modules) {
		const dir = join(docs, part.dir, module.dir);
		const file = join(dir, 'index.mdx');
		if (existsSync(file)) continue;
		mkdirSync(dir, { recursive: true });
		writeFileSync(
			file,
			`---
title: ${yamlString(`Module ${module.n}: ${module.title}`)}
description: ${yamlString(`After this module you can ${module.outcome}.`)}
sidebar:
  label: Overview
  order: 0
---

import { Aside } from '@astrojs/starlight/components';

**Part ${part.n}: ${part.title}**

## What you will be able to do

After this module you can ${module.outcome}.

## What this module covers

${module.topics.map((topic) => `- ${topic}`).join('\n')}

## Mini-project

${module.project}

<Aside type="note" title="Lessons coming soon">
The lessons for this module are being written. Check back soon.
</Aside>
`
		);
		created++;
	}
}
console.log(`Created ${created} module overview page(s).`);
